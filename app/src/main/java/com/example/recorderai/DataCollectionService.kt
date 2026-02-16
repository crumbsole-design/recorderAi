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
import android.location.Location
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
import com.example.recorderai.model.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.coroutines.resume

class DataCollectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRecording = false
    private val gson = Gson()
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_CAPTURE_UPDATE = "com.example.recorderai.CAPTURE_UPDATE"
        const val EXTRA_LAST_CAPTURE_TIME = "extra_time"
        const val EXTRA_WIFI_COUNT = "extra_wifi_count"
        private const val TAG = "WardrivingService"

        // Audio Config
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2

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
            Log.e(TAG, "Error iniciando foreground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRecording) {
            isRecording = true
            setupFiles()
         //   startAudioRecording()
            startSensorLoop()
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RecorderAI::Lock")
        wakeLock?.acquire(4*60*60*1000L)
    }

    private fun setupFiles() {
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

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Escaneando Entorno")
            .setContentText("Grabando...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            // Android 14+ requiere declarar tipos conectados si se usan
            if (Build.VERSION.SDK_INT >= 34) {
                // types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC // Opcional según uso
            }
            try {
                startForeground(NOTIFICATION_ID, notification, types)
            } catch (e: Exception) {
                // Fallback por si faltan permisos en runtime
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSensorLoop() {
        serviceScope.launch {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val telephonyManager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            while (isRecording) {
                val timestamp = System.currentTimeMillis()
                val readableTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(timestamp))
                Log.d(TAG, "--- Iniciando Barrido en $readableTime ---")

                // --- 1. UBICACIÓN (GPS) ---
                val geoLoc = getFreshLocation(locationManager)

                // --- 2. WIFI (CORREGIDO: Esperar Broadcast) ---
                val wifiList = scanWifiSuspend(wifiManager)

                // --- 3. BLUETOOTH (Tu código que ya funciona) ---
                val activeBtDevices = mutableListOf<BtInfo>()
                if (hasPermission(Manifest.permission.BLUETOOTH_SCAN) && bluetoothManager.adapter?.isEnabled == true) {
                    val scanner = bluetoothManager.adapter.bluetoothLeScanner
                    val scanCallback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult?) {
                            result?.device?.let { device ->
                                val name = if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) device.name else null
                                activeBtDevices.add(BtInfo(name ?: "Unknown", device.address, result.rssi))
                            }
                        }
                    }
                    try {
                        scanner.startScan(scanCallback)
                        delay(4000)
                        scanner.stopScan(scanCallback)
                    } catch (e: Exception) { Log.e(TAG, "BT Scan Error: $e") }
                }
                val distinctBt = activeBtDevices.distinctBy { it.address }

                // --- 4. CELDAS MÓVILES (CORREGIDO: requestCellInfoUpdate) ---
                val cellList = getFreshCellInfo(telephonyManager)

                // --- 5. GUARDAR ---
                val record = ScanRecord(
                    timestamp = timestamp,
                    readableTime = readableTime,
                    location = geoLoc,
                    wifiNetworks = wifiList,
                    bluetoothDevices = distinctBt,
                    cellTowers = cellList,
                    audioFilename = currentAudioFile.name
                )

                appendLog(record)

                val intent = Intent(ACTION_CAPTURE_UPDATE).apply {
                    putExtra(EXTRA_LAST_CAPTURE_TIME, readableTime)
                    putExtra(EXTRA_WIFI_COUNT, wifiList.size)
                    setPackage(packageName)
                }
                sendBroadcast(intent)

                // Ajustamos el delay para mantener el ciclo cerca de 30s
                // (Wifi toma ~2s, BT 4s, Celdas ~1s)
                delay(24000L)
            }
        }
    }

    /**
     * Función mágica para WiFi: Inicia escaneo -> Espera Broadcast -> Devuelve resultados
     */
    private suspend fun scanWifiSuspend(wifiManager: WifiManager): List<WifiInfo> = suspendCancellableCoroutine { cont ->
        // 1. VERIFICACIÓN ROBUSTA DE PERMISOS
        // Para escanear y leer resultados necesitamos: Ubicación Fina + Estado WiFi + Cambio WiFi
        val missingLocation = !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val missingWifiState = !hasPermission(Manifest.permission.ACCESS_WIFI_STATE)
        val missingChangeWifi = !hasPermission(Manifest.permission.CHANGE_WIFI_STATE)

        if (missingLocation || missingWifiState || missingChangeWifi) {
            Log.w("WifiScan", "Faltan permisos para escanear WiFi. Loc:$missingLocation, State:$missingWifiState, Change:$missingChangeWifi")
            if (cont.isActive) cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        // 2. VERIFICAR QUE EL WIFI ESTÉ ENCENDIDO
        // Si el WiFi está apagado, startScan() fallará o no devolverá nada útil.
        if (!wifiManager.isWifiEnabled) {
            // Opcional: Podríamos intentar encenderlo en versiones antiguas, pero en Android 10+ requiere intervención del usuario.
            Log.w("WifiScan", "El WiFi está apagado. No se puede escanear.")
            if (cont.isActive) cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Verificar si el escaneo fue exitoso o falló (ej. por throttling)
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    try {
                        @Suppress("DEPRECATION")
                        val results = wifiManager.scanResults.map {
                            WifiInfo(it.SSID ?: "", it.BSSID ?: "", it.level, it.frequency, it.capabilities ?: "")
                        }
                        if (cont.isActive) cont.resume(results)
                    } catch (e: SecurityException) {
                        Log.e("WifiScan", "Permiso denegado al leer resultados: ${e.message}")
                        if (cont.isActive) cont.resume(emptyList())
                    } catch (e: Exception) {
                        Log.e("WifiScan", "Error procesando resultados: ${e.message}")
                        if (cont.isActive) cont.resume(emptyList())
                    }
                } else {
                    // El escaneo falló (probablemente throttling de Android)
                    // En este caso, devolver lista vacía o (opcionalmente) devolver los resultados antiguos en caché
                    // Para wardriving preciso, mejor vacío o loguear que es antiguo.
                    Log.d("WifiScan", "Scan fallido (Throttling).")
                    if (cont.isActive) cont.resume(emptyList())
                }

                // Limpieza inmediata
                try { context.unregisterReceiver(this) } catch (e: Exception) {}
            }
        }

        // Registrar Receiver
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        applicationContext.registerReceiver(receiver, intentFilter)

        // Iniciar escaneo
        val started = try {
            wifiManager.startScan()
        } catch (e: Exception) {
            Log.e("WifiScan", "Excepción al iniciar scan: ${e.message}")
            false
        }

        if (!started) {
            try { applicationContext.unregisterReceiver(receiver) } catch (e: Exception) {}
            if (cont.isActive) cont.resume(emptyList())
        }

        // Timeout de seguridad (5s)
        serviceScope.launch {
            delay(5000)
            if (cont.isActive) {
                try { applicationContext.unregisterReceiver(receiver) } catch (e: Exception) {}
                // Si salta el timeout, devolvemos vacío para no bloquear el bucle principal
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }
    /**
     * Función mágica para Celdas: Solicita actualización asíncrona (Android 12+)
     */
    @SuppressLint("MissingPermission")
    private suspend fun getFreshCellInfo(telephonyManager: TelephonyManager): List<com.example.recorderai.model.CellInfo> = suspendCancellableCoroutine { cont ->
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Método moderno: Pide a la antena que refresque datos
            telephonyManager.requestCellInfoUpdate(applicationContext.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfo: MutableList<android.telephony.CellInfo>) {
                    if (cont.isActive) cont.resume(parseCells(cellInfo))
                }
                override fun onError(errorCode: Int, detail: Throwable?) {
                    // Si falla, intentamos usar la caché antigua
                    if (cont.isActive) cont.resume(parseCells(telephonyManager.allCellInfo))
                }
            })
        } else {
            // Método antiguo
            cont.resume(parseCells(telephonyManager.allCellInfo))
        }
    }

    private fun parseCells(rawList: List<android.telephony.CellInfo>?): List<com.example.recorderai.model.CellInfo> {
        if (rawList == null) return emptyList()
        return rawList.mapNotNull { cell ->
            var type = "UNKNOWN"
            var cid = 0
            var lac = 0
            var dbm = 0
            when (cell) {
                is CellInfoLte -> { type = "LTE"; cid = cell.cellIdentity.ci; lac = cell.cellIdentity.tac; dbm = cell.cellSignalStrength.dbm }
                is CellInfoGsm -> { type = "GSM"; cid = cell.cellIdentity.cid; lac = cell.cellIdentity.lac; dbm = cell.cellSignalStrength.dbm }
                is CellInfoWcdma -> { type = "WCDMA"; cid = cell.cellIdentity.cid; lac = cell.cellIdentity.lac; dbm = cell.cellSignalStrength.dbm }
                is CellInfoNr -> {
                    type = "5G"
                    val identity = cell.cellIdentity as android.telephony.CellIdentityNr
                    cid = try { identity.nci.toInt() } catch(e:Exception){0}
                    lac = identity.tac
                    dbm = cell.cellSignalStrength.dbm
                }
            }
            if (dbm != 0 && dbm != Int.MAX_VALUE) com.example.recorderai.model.CellInfo(type, cid, lac, dbm) else null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation(locManager: LocationManager): GeoLocation? = suspendCancellableCoroutine { cont ->
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        // Timeout de 2s para GPS
        serviceScope.launch {
            delay(2000)
            if (cont.isActive) cont.resume(null)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                locManager.getCurrentLocation(LocationManager.GPS_PROVIDER, null, applicationContext.mainExecutor) { loc ->
                    if (cont.isActive) {
                        if (loc != null) cont.resume(GeoLocation(loc.latitude, loc.longitude, loc.accuracy, loc.altitude))
                        else cont.resume(null)
                    }
                }
            } catch (e: Exception) { if(cont.isActive) cont.resume(null) }
        } else {
            val loc = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (loc != null) cont.resume(GeoLocation(loc.latitude, loc.longitude, loc.accuracy, loc.altitude))
            else cont.resume(null)
        }
    }

    // ... (appendLog, startAudioRecording, hasPermission, onDestroy IGUALES) ...
    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        serviceScope.launch(Dispatchers.IO) {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return@launch

            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)
            val data = ByteArray(BUFFER_SIZE)

            try {
                recorder.startRecording()
                val outputStream = FileOutputStream(currentAudioFile)

                while (isRecording) {
                    val read = recorder.read(data, 0, BUFFER_SIZE)
                    if (read > 0) {
                        outputStream.write(data, 0, read)
                    }
                }

                outputStream.close()
                recorder.stop()
                recorder.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error Audio: ${e.message}")
            }
        }
    }
    private fun appendLog(record: ScanRecord) {
        try {
            val jsonLine = gson.toJson(record) + "\n"
            FileOutputStream(currentLogFile, true).use { it.write(jsonLine.toByteArray()) }
        } catch (e: Exception) { Log.e(TAG, "Error escribiendo log: $e") }
    }

    private fun hasPermission(p: String) = ActivityCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        Log.d(TAG, "Destruyendo servicio...")
        isRecording = false
        serviceScope.cancel()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) { Log.e(TAG, "Error liberando WakeLock: $e") }

        super.onDestroy()
    }
}