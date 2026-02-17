package com.example.recorderai

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.ScanResult as WifiScanResult
import android.net.wifi.WifiManager
import android.telephony.*
import com.example.recorderai.model.ScanRecord
import com.example.recorderai.model.GeoLocation
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.util.function.Consumer

@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataCollectionServiceTest {

    private val svc = DataCollectionService()

    @BeforeEach
    fun setup() {
        android.util.Log.e("TEST", "Resetting mocks")
        clearAllMocks()
    }

    @Nested
    inner class `appendLog & setupFiles` {
        @Test
        fun `appendLog writes jsonl to currentLogFile`() = runTest {
            val temp = createTempDir(prefix = "svc")
            val logFile = File(temp, "scan_log.jsonl")
            logFile.writeText("")

            // set private field currentLogFile via reflection
            val f = svc.javaClass.getDeclaredField("currentLogFile").apply { isAccessible = true }
            f.set(svc, logFile)

            val record = ScanRecord(
                timestamp = 1L,
                readableTime = "now",
                location = com.example.recorderai.model.GeoLocation(1.0, 2.0, 3f, 4.0),
                wifiNetworks = emptyList(),
                bluetoothDevices = emptyList(),
                cellTowers = emptyList(),
                magnetometer = null,
                audioFilename = "audio.pcm"
            )

            svc.appendLog(record)

            val contents = logFile.readText()
            (contents.contains(record.readableTime)) shouldBe true

            temp.deleteRecursively()
        }
    }

    @Nested
    inner class `parseCells` {
        @Test
        fun `should parse CellInfoLte`() {
            val cellIdentity = mockk<CellIdentityLte>()
            every { cellIdentity.ci } returns 42
            every { cellIdentity.tac } returns 7

            val signal = mockk<CellSignalStrengthLte>()
            every { signal.dbm } returns -70

            val cell = mockk<CellInfoLte>()
            every { cell.cellIdentity } returns cellIdentity
            every { cell.cellSignalStrength } returns signal

            val parsed = svc.parseCells(listOf(cell))

            parsed shouldHaveSize 1
            parsed[0].type shouldBe "LTE"
            parsed[0].cid shouldBe 42
            parsed[0].lac shouldBe 7
            parsed[0].dbm shouldBe -70
        }
    }

    @Nested
    inner class `location & telephony helpers` {
        @Test
        fun `getFreshLocation returns geo when location available`() = runTest {
            mockkStatic(androidx.core.content.ContextCompat::class)
            every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_GRANTED

            val lm = mockk<LocationManager>()
            val loc = mockk<Location>(relaxed = true)
            every { loc.latitude } returns 12.34
            every { loc.longitude } returns 56.78
            every { loc.accuracy } returns 1.2f
            every { loc.altitude } returns 3.4

            every { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } returns loc
            // also mock newer API-call (if used)
            every { lm.getCurrentLocation(any<String>(), or(any<android.os.CancellationSignal>(), isNull()), any<java.util.concurrent.Executor>(), any<java.util.function.Consumer<Location>>()) } answers {
                val consumer = arg<java.util.function.Consumer<Location>>(3)
                consumer.accept(loc)
                Unit
            }

            val result = svc.getFreshLocation(lm)
            result?.latitude shouldBe 12.34
            result?.longitude shouldBe 56.78
        }

        @Test @Disabled("MockK final class issues require Robolectric or manual fakes")
        fun `getFreshCellInfo parses callback cellinfo`() = runTest {
            mockkStatic(androidx.core.content.ContextCompat::class)
            every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_GRANTED

            val telephony = mockk<TelephonyManager>()

            // create a mocked android.telephony.CellInfoLte and reuse parsing
            val cellIdentity = mockk<CellIdentityLte>()
            every { cellIdentity.ci } returns 99
            every { cellIdentity.tac } returns 11
            val signal = mockk<CellSignalStrengthLte>()
            every { signal.dbm } returns -80
            val androidCell = mockk<android.telephony.CellInfoLte>()
            every { androidCell.cellIdentity } returns cellIdentity
            every { androidCell.cellSignalStrength } returns signal

            every { telephony.requestCellInfoUpdate(any<java.util.concurrent.Executor>(), any<android.telephony.TelephonyManager.CellInfoCallback>()) } answers {
                val cb = arg<android.telephony.TelephonyManager.CellInfoCallback>(1)
                cb.onCellInfo(mutableListOf(androidCell as android.telephony.CellInfo))
                Unit
            }

            val parsed = svc.getFreshCellInfo(telephony)
            parsed shouldHaveSize 1
            parsed[0].type shouldBe "LTE"
            parsed[0].cid shouldBe 99
        }
    }

    @Nested
    inner class `wifi & bluetooth scans` {
        @Test @Disabled("MockK final class issues require Robolectric or manual fakes")
        fun `scanWifiSuspend returns list from scanResults`() = runTest {
            // prepare service base context so registerReceiver works
            val ctx = mockk<Context>(relaxed = true)
            // stub applicationContext
            every { ctx.applicationContext } returns ctx

            every { ctx.registerReceiver(any(android.content.BroadcastReceiver::class), any(android.content.IntentFilter::class)) } answers {
                val receiver = arg<android.content.BroadcastReceiver>(0)
                val intent = android.content.Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                intent.putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true)
                receiver.onReceive(ctx, intent)
                null
            }
            every { ctx.unregisterReceiver(any()) } just Runs

            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(svc, ctx)

            val wifi = mockk<WifiManager>()
            every { wifi.isWifiEnabled } returns true
            every { wifi.startScan() } returns true

            val sr = mockk<WifiScanResult>()
            every { sr.SSID } returns "net"
            every { sr.BSSID } returns "00:11"
            every { sr.level } returns -50
            every { sr.frequency } returns 2412
            every { sr.capabilities } returns "WPA2"

            every { wifi.scanResults } returns listOf(sr)

            val result = svc.scanWifiSuspend(wifi)
            result shouldHaveSize 1
            result[0].ssid shouldBe "net"
        }

        @Test @Disabled("MockK final class issues require Robolectric or manual fakes")
        fun `scanBluetoothSuspend collects devices`() = runTest {
            // attach fake context/service base via reflection
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            val ctx = mockk<Context>(relaxed = true)
            // also stub getSystemService inside service
            attachBaseContext.invoke(svc, ctx)
            
            // Ensure btManager is mocked properly
            val btManager = mockk<BluetoothManager>(relaxed = true)
            every { ctx.getSystemService(Context.BLUETOOTH_SERVICE) } returns btManager
            
            val adapter = mockk<android.bluetooth.BluetoothAdapter>(relaxed = true)
            // Use spyk or explicit mockk to ensure interception works
            every { btManager.adapter } returns adapter
            every { adapter.isEnabled } returns true

            val scanner = mockk<BluetoothLeScanner>()
            every { adapter.bluetoothLeScanner } returns scanner

            val device = mockk<BluetoothDevice>()
            every { device.name } returns "dev"
            every { device.address } returns "AA:BB"

            val scanResult = mockk<ScanResult>()
            every { scanResult.device } returns device
            every { scanResult.rssi } returns -55

            // capture callback and immediately deliver a scan result
            every { scanner.startScan(any<ScanCallback>()) } answers {
                val cb = arg<ScanCallback>(0)
                cb.onScanResult(0, scanResult)
                Unit
            }
            every { scanner.stopScan(any<ScanCallback>()) } just Runs

            // use test dispatcher on serviceScope so the internal delay can be advanced
            val testDispatcher = StandardTestDispatcher(testScheduler)
            svc.serviceScope = CoroutineScope(testDispatcher + Job())

            val deferred = async { svc.scanBluetoothSuspend() }

            // advance the internal service delay (4000ms)
            advanceTimeBy(4000)

            val results = deferred.await()
            results shouldHaveSize 1
            results[0].name shouldBe "dev"
            results[0].address shouldBe "AA:BB"
        }
    }

    @Nested
    inner class `loop runners` {
        @Test @Disabled("MockK final class issues require Robolectric or manual fakes")
        fun `getFreshMagnetometer returns values when sensor available`() = runTest {
            val localSvc = DataCollectionService()
            val sensorManager = mockk<android.hardware.SensorManager>()
            val sensor = mockk<android.hardware.Sensor>()
            every { sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD) } returns sensor

            // when registerListener is called, immediately invoke listener.onSensorChanged with mocked SensorEvent
            every { sensorManager.registerListener(any(android.hardware.SensorEventListener::class), any(android.hardware.Sensor::class), any(Int::class)) } answers {
                val listener = arg<android.hardware.SensorEventListener>(0)
                val event = mockk<android.hardware.SensorEvent>()
                every { event.values } returns floatArrayOf(1f, 2f, 3f)
                listener.onSensorChanged(event)
                true
            }
            every { sensorManager.unregisterListener(any(android.hardware.SensorEventListener::class)) } just Runs

            val result = localSvc.getFreshMagnetometer(sensorManager)
            result?.total shouldBe kotlin.math.sqrt((1f*1f + 2f*2f + 3f*3f).toDouble()).toFloat()
        }

        @Test @Disabled("Dependencies on final Android classes (Intent, Context) require proper Robolectric")
        fun `sendUiUpdate broadcasts intent with expected extras`() {
            val localSvc = DataCollectionService()
            val ctx = mockk<Context>(relaxed = true)
            every { ctx.packageName } returns "com.example.recorderai"
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(localSvc, ctx)

            val slot = slot<Intent>()
            every { ctx.sendBroadcast(capture(slot)) } just Runs

            localSvc.javaClass.getDeclaredMethod("sendUiUpdate", String::class.java).apply { isAccessible = true }.invoke(localSvc, "T")

            val intent = slot.captured
            intent.action shouldBe DataCollectionService.ACTION_CAPTURE_UPDATE
            intent.getStringExtra(DataCollectionService.EXTRA_LAST_CAPTURE_TIME) shouldBe "T"
        }

        @Test @Disabled("Dependencies require Robolectric or correct MockK agent setup for final classes")
        fun `runBluetoothAndMagnetometerLoop runs one iteration and appends log`() = runTest {
            val localSvc = spyk(DataCollectionService())
            // attach fake context
            val ctx = mockk<Context>(relaxed = true)
            every { ctx.getSystemService(Context.LOCATION_SERVICE) } returns mockk<LocationManager>(relaxed = true)
            every { ctx.getSystemService(Context.SENSOR_SERVICE) } returns mockk<android.hardware.SensorManager>(relaxed = true)
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(localSvc, ctx)

            // stub helpers
            coEvery { localSvc.getFreshLocation(any()) } returns com.example.recorderai.model.GeoLocation(1.0, 2.0, 1f, 0.0)
            coEvery { localSvc.scanBluetoothSuspend() } returns listOf(com.example.recorderai.model.BtInfo("n", "a", -50))
            coEvery { localSvc.getFreshMagnetometer(any()) } returns com.example.recorderai.model.MagnetometerInfo(1f, 2f, 3f, 4f)

            // set currentLogFile
            val temp = createTempDir(prefix = "svcloop")
            val logFile = File(temp, "scan_log.jsonl")
            val f = localSvc.javaClass.getDeclaredField("currentLogFile").apply { isAccessible = true }
            f.set(localSvc, logFile)
            val audioFile = File(temp, "audio.pcm")
            val fAudio = localSvc.javaClass.getDeclaredField("currentAudioFile").apply { isAccessible = true }
            fAudio.set(localSvc, audioFile)

            coEvery { localSvc.appendLog(any()) } answers {
                // stop after first append
                localSvc.isRecording = false
                Unit
            }

            localSvc.isRecording = true
            localSvc.runBluetoothAndMagnetometerLoop()

            // appendLog was called => file exists
            logFile.exists() shouldBe true
            temp.deleteRecursively()
        }

        @Test @Disabled("Dependencies require Robolectric or correct MockK agent setup for final classes")
        fun `runEnvironmentLoop runs one iteration and appends log`() = runTest {
            val localSvc = spyk(DataCollectionService())
            val ctx = mockk<Context>(relaxed = true)
            every { ctx.getSystemService(Context.WIFI_SERVICE) } returns mockk<WifiManager>(relaxed = true)
            every { ctx.getSystemService(Context.TELEPHONY_SERVICE) } returns mockk<TelephonyManager>(relaxed = true)
            every { ctx.getSystemService(Context.LOCATION_SERVICE) } returns mockk<LocationManager>(relaxed = true)
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(localSvc, ctx)

            // Fix nullable mocking for getFreshLocation
            coEvery { localSvc.getFreshLocation(any()) } returns com.example.recorderai.model.GeoLocation(1.0, 2.0, 1f, 0.0)
            coEvery { localSvc.scanWifiSuspend(any()) } returns emptyList()
            coEvery { localSvc.getFreshCellInfo(any()) } returns emptyList()

            val temp = createTempDir(prefix = "svcenv")
            val logFile = File(temp, "scan_log.jsonl")
            val f = localSvc.javaClass.getDeclaredField("currentLogFile").apply { isAccessible = true }
            f.set(localSvc, logFile)
            val audioFile = File(temp, "audio.pcm")
            val fAudio = localSvc.javaClass.getDeclaredField("currentAudioFile").apply { isAccessible = true }
            fAudio.set(localSvc, audioFile)

            coEvery { localSvc.appendLog(any()) } answers { localSvc.isRecording = false; Unit }

            localSvc.isRecording = true
            localSvc.runEnvironmentLoop()

            logFile.exists() shouldBe true
            temp.deleteRecursively()
        }
    }
}
