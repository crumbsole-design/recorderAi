package com.example.recorderai.data

// Plain data classes for SQLite (no Room annotations)
data class RoomEntity(
    val id: Long = 0,
    val name: String,
    val timestamp: Long
)

data class ScanSessionEntity(
    val id: Long = 0,
    val roomId: Long,
    val cellId: Int, // 0-14
    val timestamp: Long
)

data class ScanDataEntity(
    val id: Long = 0,
    val sessionId: Long,
    val type: String, // "WIFI", "BLUETOOTH", "CELL", "MAGNETOMETER"
    val content: String, // JSON content
    val timestamp: Long
)

data class CellAttributeEntity(
    val roomId: Long,
    val cellId: Int,
    val isEntrance: Boolean = false,
    val isExit: Boolean = false,
    val isLinkable: Boolean? = null,  // null = not configured, true = open for linking, false = closed
    val displayName: String? = null  // Descriptive name for display purposes (not unique)
)
