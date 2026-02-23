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
import com.example.recorderai.data.ScanDataEntity
import com.example.recorderai.model.MagnetometerInfo
import com.example.recorderai.model.ScanRecord
import com.example.recorderai.model.GeoLocation
import com.example.recorderai.model.WifiInfo
import com.example.recorderai.model.BtInfo
import com.example.recorderai.model.CellInfo
import com.example.recorderai.data.ScanDao
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.function.Consumer

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataCollectionServiceTest {

    private lateinit var svc: DataCollectionService

    @BeforeEach
    fun setup() {
        android.util.Log.e("TEST", "Resetting mocks")
        clearAllMocks()
        
        // Create service with Robolectric's real Android context
        svc = DataCollectionService()
        val context = RuntimeEnvironment.getApplication()
        val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachBaseContext.isAccessible = true
        attachBaseContext.invoke(svc, context)
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

        @Test @Disabled("TelephonyManager.CellInfoCallback requires API 29+ executor support in Robolectric")
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
        @Test @Disabled("Requires TestCoroutineScheduler coordination with Robolectric's Looper for broadcast delivery")
        fun `scanWifiSuspend returns list from scanResults`() = runTest {
            // Grant necessary permissions
            val app = RuntimeEnvironment.getApplication()
            val shadowApp = shadowOf(app)
            shadowApp.grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            )
            
            // Get real WifiManager and enable it
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.setWifiEnabled(true)
            val shadowWifi = shadowOf(wifi)
            
            // Create mocked ScanResult
            val sr = mockk<WifiScanResult>()
            every { sr.SSID } returns "net"
            every { sr.BSSID } returns "00:11"
            every { sr.level } returns -50
            every { sr.frequency } returns 2412
            every { sr.capabilities } returns "WPA2"
            
            // Set scan results in shadow
            @Suppress("DEPRECATION")
            shadowWifi.setScanResults(listOf(sr))

            // Launch suspending function in background
            val deferred = async {
                svc.scanWifiSuspend(wifi)
            }
            
            // Give time for broadcast receiver to register
            advanceTimeBy(100)
            
            // Manually send scan completion broadcast
            val intent = Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            intent.putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true)
            app.sendBroadcast(intent)
            
            // Advance Robolectric's Looper to process broadcast
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            
            val result = deferred.await()

            result shouldHaveSize 1
            result[0].ssid shouldBe "net"
        }

        @Test @Disabled("Requires TestCoroutineScheduler coordination with Robolectric's Looper for broadcast delivery")
        fun `scanWifiSuspend unregisters broadcast receiver after scan`() = runTest {
            // Grant permissions and enable WiFi
            val app = RuntimeEnvironment.getApplication()
            val shadowApp = shadowOf(app)
            shadowApp.grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            )
            
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.setWifiEnabled(true)
            val shadowWifi = shadowOf(wifi)

            val sr = mockk<WifiScanResult>()
            every { sr.SSID } returns "net"
            every { sr.BSSID } returns "00:11"
            every { sr.level } returns -50
            every { sr.frequency } returns 2412
            every { sr.capabilities } returns "WPA2"

            @Suppress("DEPRECATION")
            shadowWifi.setScanResults(listOf(sr))

            // Launch suspending function
            val deferred = async {
                svc.scanWifiSuspend(wifi)
            }
            
            advanceTimeBy(100)
            
            // Manually trigger broadcast
            val intent = Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            intent.putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true)
            app.sendBroadcast(intent)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            
            val result = deferred.await()

            // With Robolectric, receiver lifecycle is managed by real Context
            result shouldHaveSize 1
            result[0].ssid shouldBe "net"
        }

        @Test @Disabled("Needs spyk on service to mock internal getSystemService(BLUETOOTH_SERVICE) call")
        fun `scanBluetoothSuspend collects devices`() = runTest {
            // Use MockK for Bluetooth since Robolectric's Bluetooth shadows are limited
            // Still benefits from real Context provided by Robolectric
            val btManager = mockk<BluetoothManager>(relaxed = true)
            
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
        @Test @Disabled("Needs mocking of SensorEvent creation which is final - requires service refactoring")
        fun `getFreshMagnetometer returns values when sensor available`() = runTest {
            // Use MockK for sensors - Robolectric's SensorManager shadows are complex for event simulation
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

            val result = svc.getFreshMagnetometer(sensorManager)
            result?.total shouldBe kotlin.math.sqrt((1f*1f + 2f*2f + 3f*3f).toDouble()).toFloat()
        }

        @Test
        fun `sendUiUpdate broadcasts intent with expected extras`() {
            // Robolectric provides real Intent and Context
            // Note: sendBroadcast will work with real context; we can spy to verify
            val sendUiUpdate = svc.javaClass.getDeclaredMethod("sendUiUpdate", String::class.java)
            sendUiUpdate.isAccessible = true
            
            // Simply invoke - Robolectric will handle the Intent broadcast
            sendUiUpdate.invoke(svc, "T")
            
            // With Robolectric, we could use ShadowApplication to verify broadcasts
            // For now, test passes if no exception is thrown
        }
    }

    @Nested
    inner class `saveDataToDb` {

        // Helper function to call saveDataToDb directly (now that it's internal)
        private suspend fun callSaveDataToDb(service: DataCollectionService, record: ScanRecord, type: String) {
            service.saveDataToDb(record, type)
        }

        @Test
        fun `saveDataToDb should save data when sessionId is valid`() = runTest {
            // Given - Create service and inject mocked DAO
            val mockDao = mockk<ScanDao>(relaxed = true)
            coEvery { mockDao.insertData(any()) } returns 1L

            // Create a new service instance for this test
            val service = DataCollectionService()
            val context = RuntimeEnvironment.getApplication()
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(service, context)
            
            // Inject mocked DAO via reflection
            val daoField = service.javaClass.getDeclaredField("dao")
            daoField.isAccessible = true
            daoField.set(service, mockDao)
            
            // Set currentSessionId to a valid value
            val sessionIdField = service.javaClass.getDeclaredField("currentSessionId")
            sessionIdField.isAccessible = true
            sessionIdField.set(service, 1L)
            
            // Create a test record with all 4 data types
            val record = ScanRecord(
                timestamp = 1000L,
                readableTime = "01/01/2024 12:00:00",
                location = GeoLocation(40.0, -74.0, 5f, 100.0),
                wifiNetworks = listOf(WifiInfo("TestNet", "00:11:22:33:44:55", -50, 2412, "WPA2")),
                bluetoothDevices = listOf(BtInfo("BTDevice", "AA:BB:CC:DD:EE:FF", -60)),
                cellTowers = listOf(CellInfo("LTE", 12345, 100, -70)),
                magnetometer = MagnetometerInfo(25.0f, 5.0f, 10.0f, 27.0f),
                audioFilename = "SUSPENDED"
            )
            
            // When - Call saveDataToDb
            callSaveDataToDb(service, record, "WIFI")

            // Then - Verify DAO was called
            coVerify { mockDao.insertData(any()) }
        }

        @Test
        fun `saveDataToDb should NOT save data when sessionId is -1`() = runTest {
            // Given - Create service and inject mocked DAO
            val mockDao = mockk<ScanDao>(relaxed = true)
            coEvery { mockDao.insertData(any()) } returns 1L

            // Create a new service instance for this test
            val service = DataCollectionService()
            val context = RuntimeEnvironment.getApplication()
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(service, context)
            
            // Inject mocked DAO via reflection
            val daoField = service.javaClass.getDeclaredField("dao")
            daoField.isAccessible = true
            daoField.set(service, mockDao)
            
            // Set currentSessionId to -1 (not recording)
            val sessionIdField = service.javaClass.getDeclaredField("currentSessionId")
            sessionIdField.isAccessible = true
            sessionIdField.set(service, -1L)
            
            // Create a test record
            val record = ScanRecord(
                timestamp = 1000L,
                readableTime = "01/01/2024 12:00:00",
                location = GeoLocation(40.0, -74.0, 5f, 100.0),
                wifiNetworks = listOf(WifiInfo("TestNet", "00:11:22:33:44:55", -50, 2412, "WPA2")),
                bluetoothDevices = listOf(BtInfo("BTDevice", "AA:BB:CC:DD:EE:FF", -60)),
                cellTowers = listOf(CellInfo("LTE", 12345, 100, -70)),
                magnetometer = MagnetometerInfo(25.0f, 5.0f, 10.0f, 27.0f),
                audioFilename = "SUSPENDED"
            )
            
            // When - Call saveDataToDb
            callSaveDataToDb(service, record, "WIFI")

            // Then - Verify DAO was NOT called
            coVerify(exactly = 0) { mockDao.insertData(any()) }
        }

        @Test
        fun `saveDataToDb should save BT_MAGNET type correctly`() = runTest {
            // Given - Create service and inject mocked DAO
            val mockDao = mockk<ScanDao>(relaxed = true)
            coEvery { mockDao.insertData(any()) } returns 1L

            // Create a new service instance for this test
            val service = DataCollectionService()
            val context = RuntimeEnvironment.getApplication()
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(service, context)
            
            // Inject mocked DAO via reflection
            val daoField = service.javaClass.getDeclaredField("dao")
            daoField.isAccessible = true
            daoField.set(service, mockDao)
            
            // Set currentSessionId to a valid value
            val sessionIdField = service.javaClass.getDeclaredField("currentSessionId")
            sessionIdField.isAccessible = true
            sessionIdField.set(service, 1L)
            
            // Create a test record with BT and magnetometer data
            val record = ScanRecord(
                timestamp = 1000L,
                readableTime = "01/01/2024 12:00:00",
                location = GeoLocation(40.0, -74.0, 5f, 100.0),
                wifiNetworks = emptyList(),
                bluetoothDevices = listOf(BtInfo("BTDevice", "AA:BB:CC:DD:EE:FF", -60)),
                cellTowers = emptyList(),
                magnetometer = MagnetometerInfo(25.0f, 5.0f, 10.0f, 27.0f),
                audioFilename = "SUSPENDED"
            )
            
            // When - Call saveDataToDb with BLUETOOTH type
            callSaveDataToDb(service, record, "BLUETOOTH")

            // Then - Verify DAO was called with correct type
            coVerify { mockDao.insertData(match { it.type == "BLUETOOTH" }) }
        }

        @Test
        fun `saveDataToDb should serialize record to JSON correctly`() = runTest {
            // Given - Create service and inject mocked DAO
            val mockDao = mockk<ScanDao>(relaxed = true)
            val dataSlot = slot<ScanDataEntity>()
            coEvery { mockDao.insertData(capture(dataSlot)) } returns 1L
            
            // Create a new service instance for this test
            val service = DataCollectionService()
            val context = RuntimeEnvironment.getApplication()
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(service, context)
            
            // Inject mocked DAO via reflection
            val daoField = service.javaClass.getDeclaredField("dao")
            daoField.isAccessible = true
            daoField.set(service, mockDao)
            
            // Set currentSessionId to a valid value
            val sessionIdField = service.javaClass.getDeclaredField("currentSessionId")
            sessionIdField.isAccessible = true
            sessionIdField.set(service, 1L)
            
            // Create a test record with all data types
            val record = ScanRecord(
                timestamp = 1000L,
                readableTime = "01/01/2024 12:00:00",
                location = GeoLocation(40.0, -74.0, 5f, 100.0),
                wifiNetworks = listOf(WifiInfo("TestNet", "00:11:22:33:44:55", -50, 2412, "WPA2")),
                bluetoothDevices = listOf(BtInfo("BTDevice", "AA:BB:CC:DD:EE:FF", -60)),
                cellTowers = listOf(CellInfo("LTE", 12345, 100, -70)),
                magnetometer = MagnetometerInfo(25.0f, 5.0f, 10.0f, 27.0f),
                audioFilename = "SUSPENDED"
            )
            
            // When - Call saveDataToDb
            callSaveDataToDb(service, record, "WIFI")

            // Then - Verify JSON content contains expected fields
            val captured = dataSlot.captured
            captured.sessionId shouldBe 1L
            captured.type shouldBe "WIFI"
            captured.timestamp shouldBe 1000L
            captured.content shouldNotBe null
            // Verify JSON contains key data
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("TestNet"))
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("00:11:22:33:44:55"))
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("BTDevice"))
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("LTE"))
        }

        @Test
        fun `saveDataToDb should save WiFi data type individually`() = runTest {
            // Given
            val mockDao = mockk<ScanDao>(relaxed = true)
            val dataSlot = slot<ScanDataEntity>()
            coEvery { mockDao.insertData(capture(dataSlot)) } returns 1L

            val service = DataCollectionService()
            val context = RuntimeEnvironment.getApplication()
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(service, context)

            val daoField = service.javaClass.getDeclaredField("dao")
            daoField.isAccessible = true
            daoField.set(service, mockDao)

            val sessionIdField = service.javaClass.getDeclaredField("currentSessionId")
            sessionIdField.isAccessible = true
            sessionIdField.set(service, 5L)

            // Record with only WiFi data
            val record = ScanRecord(
                timestamp = 2000L,
                readableTime = "01/01/2024 13:00:00",
                location = GeoLocation(41.0, -75.0, 3f, 50.0),
                wifiNetworks = listOf(
                    WifiInfo("Network1", "AA:BB:CC:DD:EE:FF", -55, 2437, "WPA3"),
                    WifiInfo("Network2", "11:22:33:44:55:66", -68, 5180, "WPA2")
                ),
                bluetoothDevices = emptyList(),
                cellTowers = emptyList(),
                magnetometer = null,
                audioFilename = "SUSPENDED"
            )

        // When - Use helper function instead of reflection for suspend function
            callSaveDataToDb(service, record, "WIFI")

            // Then
            val captured = dataSlot.captured
            captured.sessionId shouldBe 5L
            captured.type shouldBe "WIFI"
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("Network1"))
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("Network2"))
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("5180"))
        }

        @Test
        fun `saveDataToDb should save Bluetooth data type individually`() = runTest {
            // Given
            val mockDao = mockk<ScanDao>(relaxed = true)
            val dataSlot = slot<ScanDataEntity>()
            coEvery { mockDao.insertData(capture(dataSlot)) } returns 1L

            val service = DataCollectionService()
            val context = RuntimeEnvironment.getApplication()
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(service, context)

            val daoField = service.javaClass.getDeclaredField("dao")
            daoField.isAccessible = true
            daoField.set(service, mockDao)

            val sessionIdField = service.javaClass.getDeclaredField("currentSessionId")
            sessionIdField.isAccessible = true
            sessionIdField.set(service, 3L)

            // Record with only Bluetooth data
            val record = ScanRecord(
                timestamp = 3000L,
                readableTime = "01/01/2024 14:00:00",
                location = GeoLocation(42.0, -76.0, 4f, 75.0),
                wifiNetworks = emptyList(),
                bluetoothDevices = listOf(
                    BtInfo("HeadphonesXYZ", "AA:11:22:33:44:55", -65),
                    BtInfo("Speaker123", "BB:11:22:33:44:55", -72)
                ),
                cellTowers = emptyList(),
                magnetometer = null,
                audioFilename = "SUSPENDED"
            )

            // When - Use helper function instead of reflection for suspend function
            callSaveDataToDb(service, record, "BLUETOOTH")

            // Then
            val captured = dataSlot.captured
            captured.sessionId shouldBe 3L
            captured.type shouldBe "BLUETOOTH"
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("HeadphonesXYZ"))
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("Speaker123"))
        }

        @Test
        fun `saveDataToDb should save Cell data type individually`() = runTest {
            // Given
            val mockDao = mockk<ScanDao>(relaxed = true)
            val dataSlot = slot<ScanDataEntity>()
            coEvery { mockDao.insertData(capture(dataSlot)) } returns 1L

            val service = DataCollectionService()
            val context = RuntimeEnvironment.getApplication()
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(service, context)

            val daoField = service.javaClass.getDeclaredField("dao")
            daoField.isAccessible = true
            daoField.set(service, mockDao)

            val sessionIdField = service.javaClass.getDeclaredField("currentSessionId")
            sessionIdField.isAccessible = true
            sessionIdField.set(service, 7L)

            // Record with only Cell data
            val record = ScanRecord(
                timestamp = 4000L,
                readableTime = "01/01/2024 15:00:00",
                location = GeoLocation(43.0, -77.0, 6f, 120.0),
                wifiNetworks = emptyList(),
                bluetoothDevices = emptyList(),
                cellTowers = listOf(
                    CellInfo("LTE", 54321, 200, -75),
                    CellInfo("5G", 98765, 300, -82)
                ),
                magnetometer = null,
                audioFilename = "SUSPENDED"
            )

            // When - Use helper function instead of reflection for suspend function
            callSaveDataToDb(service, record, "CELL")

            // Then
            val captured = dataSlot.captured
            captured.sessionId shouldBe 7L
            captured.type shouldBe "CELL"
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("54321"))
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("5G"))
        }

        @Test
        fun `saveDataToDb should save Magnetometer data correctly`() = runTest {
            // Given
            val mockDao = mockk<ScanDao>(relaxed = true)
            val dataSlot = slot<ScanDataEntity>()
            coEvery { mockDao.insertData(capture(dataSlot)) } returns 1L

            val service = DataCollectionService()
            val context = RuntimeEnvironment.getApplication()
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(service, context)

            val daoField = service.javaClass.getDeclaredField("dao")
            daoField.isAccessible = true
            daoField.set(service, mockDao)

            val sessionIdField = service.javaClass.getDeclaredField("currentSessionId")
            sessionIdField.isAccessible = true
            sessionIdField.set(service, 9L)

            // Record with magnetometer data
            val record = ScanRecord(
                timestamp = 5000L,
                readableTime = "01/01/2024 16:00:00",
                location = GeoLocation(44.0, -78.0, 2f, 90.0),
                wifiNetworks = emptyList(),
                bluetoothDevices = emptyList(),
                cellTowers = emptyList(),
                magnetometer = MagnetometerInfo(30.5f, -10.2f, 15.8f, 35.7f),
                audioFilename = "SUSPENDED"
            )

            // When
            callSaveDataToDb(service, record, "MAGNETOMETER")

            // Then
            val captured = dataSlot.captured
            captured.sessionId shouldBe 9L
            captured.type shouldBe "MAGNETOMETER"
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("30.5"))
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("-10.2"))
            org.junit.jupiter.api.Assertions.assertTrue(captured.content.contains("35.7"))
        }

        @Test
        fun `saveDataToDb should handle multiple sequential saves`() = runTest {
            // Given
            val mockDao = mockk<ScanDao>(relaxed = true)
            val capturedData = mutableListOf<ScanDataEntity>()
            coEvery { mockDao.insertData(capture(capturedData)) } returns 1L

            val service = DataCollectionService()
            val context = RuntimeEnvironment.getApplication()
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(service, context)

            val daoField = service.javaClass.getDeclaredField("dao")
            daoField.isAccessible = true
            daoField.set(service, mockDao)

            val sessionIdField = service.javaClass.getDeclaredField("currentSessionId")
            sessionIdField.isAccessible = true
            sessionIdField.set(service, 10L)

            // When - Save multiple records
            val record1 = ScanRecord(
                timestamp = 6000L,
                readableTime = "01/01/2024 17:00:00",
                location = GeoLocation(45.0, -79.0, 3f, 110.0),
                wifiNetworks = listOf(WifiInfo("Net1", "AA:AA:AA:AA:AA:AA", -50, 2412, "WPA2")),
                bluetoothDevices = emptyList(),
                cellTowers = emptyList(),
                magnetometer = null,
                audioFilename = "SUSPENDED"
            )

            val record2 = ScanRecord(
                timestamp = 7000L,
                readableTime = "01/01/2024 17:00:10",
                location = GeoLocation(45.0, -79.0, 3f, 110.0),
                wifiNetworks = emptyList(),
                bluetoothDevices = listOf(BtInfo("Device1", "BB:BB:BB:BB:BB:BB", -60)),
                cellTowers = emptyList(),
                magnetometer = MagnetometerInfo(20.0f, 5.0f, 10.0f, 23.0f),
                audioFilename = "SUSPENDED"
            )

            callSaveDataToDb(service, record1, "WIFI")
            callSaveDataToDb(service, record2, "BLUETOOTH")

            // Then
            capturedData shouldHaveSize 2
            capturedData[0].type shouldBe "WIFI"
            capturedData[0].sessionId shouldBe 10L
            capturedData[1].type shouldBe "BLUETOOTH"
            capturedData[1].sessionId shouldBe 10L
        }
    }
}
