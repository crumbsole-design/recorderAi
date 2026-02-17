package com.example.recorderai

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.recorderai.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.math.sqrt

class DataCollectionService : Service() {

    // permitimos reemplazar el scope en tests
    internal var serviceScope = CoroutineScope(Dispatchers.IO + Job())
    internal var isRecording = false

    // Serializa nulos para mantener estructura JSON fija (ej: "magnetometer": null)
    private val gson = GsonBuilder().serializeNulls().create()

    private var wakeLock: PowerManager.WakeLock? = null
    private val fileMutex = Mutex()
    private var lastKnownWifiCount = 0

    companion object {
        const val ACTION_CAPTURE_UPDATE = "com.example.recorderai.CAPTURE_UPDATE"
        const val EXTRA_LAST_CAPTURE_TIME = "extra_time"
        const val EXTRA_WIFI_COUNT = "extra_wifi_count"
        private const val TAG = "WardrivingService"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // evitar llamada a AudioRecord.getMinBufferSize en tests JVM (Robolectric o JVM)
        private val BUFFER_SIZE: Int by lazy {
            try {
                AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
            } catch (t: Throwable) {
                1024
            }
        }
        private const val NOTIFICATION_CHANNEL_ID = "wardriving_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var sessionDir: File
    private lateinit var currentAudioFile: File
    private lateinit var currentLogFile: File

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acquireWakeLock()
        try {
            startForegroundServiceNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error fatal Foreground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRecording) {
            isRecording = true
            setupFiles()
           // startAudioRecording()
            // Lanzamos bucles paralelos
            serviceScope.launch { runBluetoothAndMagnetometerLoop() } // NOMBRE ACTUALIZADO
            serviceScope.launch { runEnvironmentLoop() }
        }
        return START_STICKY
    }

    // --- BUCLES ---

    // Este bucle corre cada ~10 segundos (Bluetooth + Magnetómetro)
    internal suspend fun runBluetoothAndMagnetometerLoop() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        while (isRecording) {
            val timestamp = System.currentTimeMillis()
            val readableTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(timestamp))

            // 1. Ubicación (Compartida)
            val geoLoc = getFreshLocation(locationManager)

            // 2. Escaneo BT
            val btList = scanBluetoothSuspend()

            // 3. Magnetómetro (NUEVO)
            val magInfo = getFreshMagnetometer(sensorManager)

            // Guardar
            val distinctBt = btList.distinctBy { it.address }
            val record = ScanRecord(
                timestamp = timestamp,
                readableTime = readableTime,
                location = geoLoc,
                wifiNetworks = emptyList(),
                bluetoothDevices = distinctBt,
                cellTowers = emptyList(),
                magnetometer = magInfo, // Guardamos datos magnéticos
                audioFilename = currentAudioFile.name
            )

            appendLog(record)
            sendUiUpdate(readableTime)

            delay(6000L) // Ciclo total ~10s
        }
    }

    internal suspend fun runEnvironmentLoop() {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        while (isRecording) {
            val timestamp = System.currentTimeMillis()
            val readableTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(timestamp))

            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.w(TAG, "GPS Apagado: No se pueden obtener WiFi/Celdas")
            }

            val geoLoc = getFreshLocation(locationManager)
            val wifiList = scanWifiSuspend(wifiManager)
            lastKnownWifiCount = wifiList.size
            val cellList = getFreshCellInfo(telephonyManager)

            val record = ScanRecord(
                timestamp = timestamp,
                readableTime = readableTime,
                location = geoLoc,
                wifiNetworks = wifiList,
                bluetoothDevices = emptyList(),
                cellTowers = cellList,
                magnetometer = null, // En este bucle lento no leemos magnetómetro (o podrias duplicarlo si quieres)
                audioFilename = currentAudioFile.name
            )

            appendLog(record)
            sendUiUpdate(readableTime)
            delay(25000L) // Ciclo ~30s
        }
    }

    // --- NUEVA FUNCIÓN: CAPTURA DE MAGNETÓMETRO ---

    internal suspend fun getFreshMagnetometer(sensorManager: SensorManager): MagnetometerInfo? = suspendCancellableCoroutine { cont ->
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sensor == null) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && cont.isActive) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    // Calculamos fuerza total vectorial
                    val total = sqrt((x*x + y*y + z*z).toDouble()).toFloat()

                    cont.resume(MagnetometerInfo(x, y, z, total))
                    // Desregistramos inmediatamente tras la primera lectura válida
                    sensorManager.unregisterListener(this)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Registramos el listener
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

        // Limpieza si se cancela la corrutina
        cont.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
        }

        // Timeout de seguridad (1 segundo es suficiente para sensores)
        serviceScope.launch {
            delay(1000)
            if (cont.isActive) {
                sensorManager.unregisterListener(listener)
                cont.resume(null)
            }
        }
    }

    // --- RESTO DE ESCÁNERES (Idénticos a tu versión anterior) ---

    @SuppressLint("MissingPermission")
    internal suspend fun scanWifiSuspend(wifiManager: WifiManager): List<WifiInfo> = suspendCancellableCoroutine { cont ->
        if (!hasPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE)) {
            if (cont.isActive) cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        if (!wifiManager.isWifiEnabled) {
            if (cont.isActive) cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!cont.isActive) return
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    try {
                        @Suppress("DEPRECATION")
                        val results = wifiManager.scanResults.map {
                            WifiInfo(it.SSID ?: "", it.BSSID ?: "", it.level, it.frequency, it.capabilities ?: "")
                        }
                        cont.resume(results)
                    } catch (e: Exception) { cont.resume(emptyList()) }
                } else {
                    cont.resume(emptyList())
                }
            }
        }
        try {
            applicationContext.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            cont.invokeOnCancellation { try { applicationContext.unregisterReceiver(receiver) } catch (e: Exception) {} }
            if (!wifiManager.startScan()) { if (cont.isActive) cont.resume(emptyList()) }
        } catch (e: Exception) { if (cont.isActive) cont.resume(emptyList()) }
        serviceScope.launch {
            delay(5000)
            if (cont.isActive) cont.resume(emptyList())
        }
    }

    @SuppressLint("MissingPermission")
    internal suspend fun scanBluetoothSuspend(): List<BtInfo> = suspendCancellableCoroutine { cont ->
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        if (adapter == null || !adapter.isEnabled || !hasPermissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) {
            if (cont.isActive) cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val results = mutableListOf<BtInfo>()
        val scanner = adapter.bluetoothLeScanner
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val name = device.name ?: "Unknown"
                    results.add(BtInfo(name, device.address, result.rssi))
                }
            }
            override fun onScanFailed(errorCode: Int) {}
        }
        try {
            scanner.startScan(callback)
            cont.invokeOnCancellation { try { scanner.stopScan(callback) } catch(e: Exception){} }
            serviceScope.launch {
                delay(4000)
                try { scanner.stopScan(callback) } catch(e: Exception){}
                if (cont.isActive) cont.resume(results)
            }
        } catch (e: Exception) { if (cont.isActive) cont.resume(emptyList()) }
    }

    @SuppressLint("MissingPermission")
    internal suspend fun getFreshCellInfo(telephonyManager: TelephonyManager): List<com.example.recorderai.model.CellInfo> = suspendCancellableCoroutine { cont ->
        if (!hasPermissions(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (cont.isActive) cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                telephonyManager.requestCellInfoUpdate(applicationContext.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<android.telephony.CellInfo>) {
                        if (cont.isActive) cont.resume(parseCells(cellInfo))
                    }
                    override fun onError(errorCode: Int, detail: Throwable?) {
                        if (cont.isActive) cont.resume(parseCells(telephonyManager.allCellInfo))
                    }
                })
            } catch (e: Exception) { if (cont.isActive) cont.resume(parseCells(telephonyManager.allCellInfo)) }
        } else { if (cont.isActive) cont.resume(parseCells(telephonyManager.allCellInfo)) }
    }

    @SuppressLint("MissingPermission")
    internal suspend fun getFreshLocation(locManager: LocationManager): GeoLocation? = suspendCancellableCoroutine { cont ->
        if (!hasPermissions(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val timeoutJob = serviceScope.launch {
            delay(2000)
            if (cont.isActive) cont.resume(null)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locManager.getCurrentLocation(LocationManager.GPS_PROVIDER, null, applicationContext.mainExecutor) { loc ->
                    timeoutJob.cancel()
                    if (cont.isActive) {
                        if (loc != null) cont.resume(GeoLocation(loc.latitude, loc.longitude, loc.accuracy, loc.altitude))
                        else cont.resume(null)
                    }
                }
            } else {
                val loc = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                timeoutJob.cancel()
                if (cont.isActive) {
                    if (loc != null) cont.resume(GeoLocation(loc.latitude, loc.longitude, loc.accuracy, loc.altitude))
                    else cont.resume(null)
                }
            }
        } catch (e: Exception) { if (cont.isActive) cont.resume(null) }
    }

    // --- UTILIDADES ---

    internal fun parseCells(rawList: List<android.telephony.CellInfo>?): List<com.example.recorderai.model.CellInfo> {
        if (rawList == null) return emptyList()
        return rawList.mapNotNull { cell ->
            var type = "UNKNOWN"; var cid = 0; var lac = 0; var dbm = 0
            when (cell) {
                is CellInfoLte -> { type = "LTE"; cid = cell.cellIdentity.ci; lac = cell.cellIdentity.tac; dbm = cell.cellSignalStrength.dbm }
                is CellInfoGsm -> { type = "GSM"; cid = cell.cellIdentity.cid; lac = cell.cellIdentity.lac; dbm = cell.cellSignalStrength.dbm }
                is CellInfoWcdma -> { type = "WCDMA"; cid = cell.cellIdentity.cid; lac = cell.cellIdentity.lac; dbm = cell.cellSignalStrength.dbm }
                is CellInfoNr -> {
                    type = "5G"
                    val id = cell.cellIdentity as android.telephony.CellIdentityNr
                    cid = try { id.nci.toInt() } catch(e:Exception){0}
                    lac = id.tac
                    dbm = cell.cellSignalStrength.dbm
                }
            }
            if (dbm != 0 && dbm != Int.MAX_VALUE) com.example.recorderai.model.CellInfo(type, cid, lac, dbm) else null
        }
    }

    private fun hasPermissions(vararg perms: String): Boolean {
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RecorderAI::Lock")
        wakeLock?.acquire(4*60*60*1000L)
    }

    internal fun setupFiles() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val rootDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        sessionDir = File(rootDir, "RecorderAI/Session_$timeStamp")
        if (!sessionDir.exists()) sessionDir.mkdirs()
        currentAudioFile = File(sessionDir, "audio_raw.pcm")
        currentLogFile = File(sessionDir, "scan_log.jsonl")
    }

    private fun startForegroundServiceNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Wardriving Service", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Escaneando Entorno")
            .setContentText("Grabando datos...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        serviceScope.launch(Dispatchers.IO) {
            if (!hasPermissions(Manifest.permission.RECORD_AUDIO)) return@launch
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)
            val data = ByteArray(BUFFER_SIZE)
            try {
                recorder.startRecording()
                val outputStream = FileOutputStream(currentAudioFile)
                while (isRecording) {
                    val read = recorder.read(data, 0, BUFFER_SIZE)
                    if (read > 0) outputStream.write(data, 0, read)
                }
                outputStream.close(); recorder.stop(); recorder.release()
            } catch (e: Exception) { Log.e(TAG, "Audio Error: ${e.message}") }
        }
    }

    internal suspend fun appendLog(record: ScanRecord) {
        fileMutex.withLock {
            try {
                val jsonLine = gson.toJson(record) + "\n"
                FileOutputStream(currentLogFile, true).use { it.write(jsonLine.toByteArray()) }
                Log.d(TAG, "Log guardado.")
            } catch (e: Exception) { Log.e(TAG, "Log Error: $e") }
        }
    }

    private fun sendUiUpdate(time: String) {
        val intent = Intent(ACTION_CAPTURE_UPDATE).apply {
            putExtra(EXTRA_LAST_CAPTURE_TIME, time)
            putExtra(EXTRA_WIFI_COUNT, lastKnownWifiCount)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        isRecording = false
        serviceScope.cancel()
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (e:Exception){}
        super.onDestroy()
    }
}