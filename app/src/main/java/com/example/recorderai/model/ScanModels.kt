package com.example.recorderai.model

// Modelo principal que agrupa todo lo que pasa en un instante T
data class ScanRecord(
    val timestamp: Long,          // Hora exacta del sistema
    val readableTime: String,     // Hora legible para humanos
    val location: GeoLocation?,   // Puede ser null si no hay GPS
    val wifiNetworks: List<WifiInfo>,
    val bluetoothDevices: List<BtInfo>,
    val cellTowers: List<CellInfo>,
    val audioFilename: String     // Nombre del archivo de audio asociado
)

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double
)

data class WifiInfo(
    val ssid: String,       // Nombre de la red (Oculto si es '')
    val bssid: String,      // Dirección MAC (Identificador único)
    val rssi: Int,          // Potencia en dBm (Ej: -65)
    val frequency: Int,     // 2400 o 5000 MHz
    val capabilities: String // WPA2, WEP, etc.
)

data class BtInfo(
    val name: String,
    val address: String,    // MAC
    val rssi: Int           // Potencia
)

data class CellInfo(
    val type: String,       // LTE, GSM, NR (5G)
    val cid: Int,           // Cell ID
    val lac: Int,           // Location Area Code
    val dbm: Int            // Potencia
)