package com.example.recorderai.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class ScanRepository(private val dao: ScanDao) {

    // Room operations
    suspend fun createRoom(name: String): Long {
        return dao.insertRoom(RoomEntity(name = name, timestamp = System.currentTimeMillis()))
    }

    fun getAllRooms(): Flow<List<RoomEntity>> = dao.getAllRooms()

    // Session operations
    suspend fun createSession(roomId: Long, cellId: Int): Long {
        return dao.insertSession(
            ScanSessionEntity(
                roomId = roomId,
                cellId = cellId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSessionByRoomAndCell(roomId: Long, cellId: Int): ScanSessionEntity? {
        return dao.getSessionByRoomAndCell(roomId, cellId).firstOrNull()
    }

    fun getSessionsByRoom(roomId: Long): Flow<List<ScanSessionEntity>> = dao.getSessionsByRoom(roomId)

    // Data operations
    suspend fun insertScanData(data: ScanDataEntity) {
        dao.insertData(data)
    }

    // Cell attributes operations
    suspend fun setCellAttribute(attribute: CellAttributeEntity) {
        dao.setCellAttribute(attribute)
    }

    suspend fun getCellAttribute(roomId: Long, cellId: Int): CellAttributeEntity? {
        return dao.getCellAttribute(roomId, cellId).firstOrNull()
    }

    suspend fun updateCellLinkableStatus(roomId: Long, cellId: Int, isLinkable: Boolean?) {
        dao.updateCellLinkableStatus(roomId, cellId, isLinkable)
    }

    suspend fun regenerateCell(roomId: Long, cellId: Int) {
        // Delete all data associated with this cell
        dao.deleteCellData(roomId, cellId)
        dao.deleteSessionsByCell(roomId, cellId)
        
        // Reset cell attribute to unconfigured state
        val currentAttribute = dao.getCellAttribute(roomId, cellId).firstOrNull()
        if (currentAttribute != null) {
            dao.setCellAttribute(
                currentAttribute.copy(isLinkable = null)
            )
        } else {
            // Create new attribute with isLinkable = null
            dao.setCellAttribute(
                CellAttributeEntity(
                    roomId = roomId,
                    cellId = cellId,
                    isLinkable = null
                )
            )
        }
    }

    // Aggregation
    fun getScanCounts(roomId: Long): Flow<Map<Int, Int>> = dao.getScanCounts(roomId)

    // Get scan data counts by type for a specific cell
    fun getScanDataCountsByType(roomId: Long, cellId: Int): Flow<Map<String, Int>> = 
        dao.getScanDataCountsByType(roomId, cellId)

    // Delete a room and all associated data
    suspend fun deleteRoom(roomId: Long) {
        dao.deleteRoom(roomId)
    }

    // Update room name
    suspend fun updateRoomName(roomId: Long, newName: String) {
        dao.updateRoomName(roomId, newName)
    }
}
