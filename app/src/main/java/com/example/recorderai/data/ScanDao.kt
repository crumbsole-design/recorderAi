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
}
