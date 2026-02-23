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

    // Export operations
    suspend fun exportAllData(roomId: Long? = null): ExportData {
        val rooms = dao.getAllRoomsForExport()
        val sessions = dao.getAllSessionsForExport(roomId)
        val scanData = dao.getAllScanDataForExport(roomId)
        val typeCounts = dao.getScanDataCountsByTypeForExport(roomId)
        val dateRange = dao.getScanDataDateRange(roomId)

        // Build room exports with cell attributes
        val roomExports = rooms.map { room ->
            val cellAttrs = dao.getCellAttributesForRoom(room.id)
            RoomExport(
                id = room.id,
                name = room.name,
                timestamp = room.timestamp,
                cells = cellAttrs.map { attr ->
                    CellExport(
                        cellId = attr.cellId,
                        displayName = attr.displayName,
                        isEntrance = attr.isEntrance,
                        isExit = attr.isExit,
                        isLinkable = attr.isLinkable
                    )
                }
            )
        }

        // Build session exports with counts and names
        val sessionExports = sessions.map { session ->
            val roomName = rooms.find { it.id == session.roomId }?.name
            val cellAttr = dao.getCellAttributesForRoom(session.roomId).find { it.cellId == session.cellId }
            val dataCount = dao.getScanDataCountsBySession(session.id)
            SessionExport(
                id = session.id,
                roomId = session.roomId,
                roomName = roomName,
                cellId = session.cellId,
                cellName = cellAttr?.displayName,
                timestamp = session.timestamp,
                dataCount = dataCount
            )
        }

        // Build scan data exports
        val scanDataExports = scanData.map { data ->
            val roomName = rooms.find { it.id == data.roomId }?.name
            val cellAttr = dao.getCellAttributesForRoom(data.roomId).find { it.cellId == data.cellId }
            ScanDataExport(
                id = data.id,
                sessionId = data.sessionId,
                roomId = data.roomId,
                cellId = data.cellId,
                type = data.type,
                timestamp = data.timestamp,
                content = data.content
            )
        }

        // Build statistics
        val statistics = ExportStatistics(
            totalRooms = if (roomId != null) 1 else rooms.size,
            totalSessions = sessions.size,
            totalScanRecords = scanData.size,
            recordsByType = typeCounts,
            dateRange = dateRange?.let { pair -> DateRange(start = pair.first, end = pair.second) }
        )

        return ExportData(
            exportType = if (roomId != null) "BY_ROOM" else "FULL",
            rooms = if (roomId != null) roomExports.filter { it.id == roomId } else roomExports,
            sessions = sessionExports,
            scanData = scanDataExports,
            statistics = statistics
        )
    }
}
