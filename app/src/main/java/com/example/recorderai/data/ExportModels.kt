package com.example.recorderai.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Models for database export functionality.
 * These classes represent the structured export format for RecorderAI data.
 */

data class ExportData(
    val exportDate: Long = System.currentTimeMillis(),
    val exportDateReadable: String = formatDate(System.currentTimeMillis()),
    val app: String = "RecorderAI",
    val version: String = "1.0",
    val exportType: String = "FULL", // FULL or BY_ROOM
    val rooms: List<RoomExport> = emptyList(),
    val sessions: List<SessionExport> = emptyList(),
    val scanData: List<ScanDataExport> = emptyList(),
    val statistics: ExportStatistics? = null
)

data class RoomExport(
    val id: Long,
    val name: String,
    val timestamp: Long,
    val timestampReadable: String = formatDate(timestamp),
    val cells: List<CellExport> = emptyList()
)

data class CellExport(
    val cellId: Int,
    val displayName: String?,
    val isEntrance: Boolean = false,
    val isExit: Boolean = false,
    val isLinkable: Boolean? = null
)

data class SessionExport(
    val id: Long,
    val roomId: Long,
    val roomName: String?,
    val cellId: Int,
    val cellName: String?,
    val timestamp: Long,
    val timestampReadable: String = formatDate(timestamp),
    val dataCount: Int = 0
)

data class ScanDataExport(
    val id: Long,
    val sessionId: Long,
    val roomId: Long,
    val cellId: Int,
    val type: String,
    val timestamp: Long,
    val timestampReadable: String = formatDate(timestamp),
    val content: String // JSON string as stored in database
)

data class ExportStatistics(
    val totalRooms: Int,
    val totalSessions: Int,
    val totalScanRecords: Int,
    val recordsByType: Map<String, Int> = emptyMap(),
    val dateRange: DateRange? = null
)

data class DateRange(
    val start: Long,
    val end: Long,
    val startReadable: String = formatDate(start),
    val endReadable: String = formatDate(end)
)

private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "Invalid date"
    }
}