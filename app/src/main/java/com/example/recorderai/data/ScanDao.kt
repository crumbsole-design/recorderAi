package com.example.recorderai.data

import kotlinx.coroutines.flow.Flow

/**
 * Data access interface for scan operations.
 * Implemented by ScanDaoImpl using direct SQLite.
 */
interface ScanDao {
    suspend fun insertRoom(room: RoomEntity): Long

    fun getAllRooms(): Flow<List<RoomEntity>>

    suspend fun insertSession(session: ScanSessionEntity): Long

    suspend fun insertData(data: ScanDataEntity): Long

    suspend fun setCellAttribute(attribute: CellAttributeEntity)

    fun getCellAttribute(roomId: Long, cellId: Int): Flow<CellAttributeEntity?>

    fun getSessionByRoomAndCell(roomId: Long, cellId: Int): Flow<ScanSessionEntity?>

    fun getSessionsByRoom(roomId: Long): Flow<List<ScanSessionEntity>>

    suspend fun updateCellLinkableStatus(roomId: Long, cellId: Int, isLinkable: Boolean?)

    suspend fun deleteCellData(roomId: Long, cellId: Int)

    suspend fun deleteSessionsByCell(roomId: Long, cellId: Int)

    suspend fun deleteCellAttribute(roomId: Long, cellId: Int)

    // Aggregation for grid visualization - returns map of cellId to count
    fun getScanCounts(roomId: Long): Flow<Map<Int, Int>>

    // Get scan data counts by type for a specific cell
    fun getScanDataCountsByType(roomId: Long, cellId: Int): Flow<Map<String, Int>>

    // Delete a room and all associated data
    suspend fun deleteRoom(roomId: Long)

    suspend fun updateRoomName(roomId: Long, newName: String)

    // Export operations
    suspend fun getAllRoomsForExport(): List<RoomEntity>

    suspend fun getCellAttributesForRoom(roomId: Long): List<CellAttributeEntity>

    suspend fun getAllSessionsForExport(roomId: Long? = null): List<ScanSessionEntity>

    suspend fun getAllScanDataForExport(roomId: Long? = null): List<ScanDataWithSessionInfo>

    suspend fun getScanDataCountsBySession(sessionId: Long): Int

    suspend fun getScanDataCountsByTypeForExport(roomId: Long? = null): Map<String, Int>

    suspend fun getScanDataDateRange(roomId: Long? = null): Pair<Long, Long>?
}

// Helper class for export queries with session info
data class ScanDataWithSessionInfo(
    val id: Long,
    val sessionId: Long,
    val roomId: Long,
    val cellId: Int,
    val type: String,
    val content: String,
    val timestamp: Long
)
