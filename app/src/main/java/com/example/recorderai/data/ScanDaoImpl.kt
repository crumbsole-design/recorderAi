package com.example.recorderai.data

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ScanDaoImpl(private val dbHelper: DatabaseHelper) : ScanDao {

    // Helper to convert Boolean? to Integer for SQLite (null stays null, true=1, false=0)
    private fun Boolean?.toInt(): Int? = when (this) {
        null -> null
        true -> 1
        false -> 0
    }

    // Helper to convert Integer to Boolean? from SQLite (null stays null, 1=true, 0=false)
    private fun Int?.toBoolean(): Boolean? = when (this) {
        null -> null
        1 -> true
        else -> false
    }

    override suspend fun insertRoom(room: RoomEntity): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_NAME, room.name)
            put(DatabaseHelper.COLUMN_TIMESTAMP, room.timestamp)
        }
        db.insert(DatabaseHelper.TABLE_ROOMS, null, values)
    }

    override fun getAllRooms(): Flow<List<RoomEntity>> = flow {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_ROOMS,
            null, null, null, null, null,
            "${DatabaseHelper.COLUMN_TIMESTAMP} DESC"
        )
        val rooms = mutableListOf<RoomEntity>()
        while (cursor.moveToNext()) {
            rooms.add(cursor.toRoomEntity())
        }
        cursor.close()
        emit(rooms)
    }.flowOn(Dispatchers.IO)

    override suspend fun insertSession(session: ScanSessionEntity): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_ROOM_ID, session.roomId)
            put(DatabaseHelper.COLUMN_CELL_ID, session.cellId)
            put(DatabaseHelper.COLUMN_TIMESTAMP, session.timestamp)
        }
        db.insert(DatabaseHelper.TABLE_SESSIONS, null, values)
    }

    override suspend fun insertData(data: ScanDataEntity): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_SESSION_ID, data.sessionId)
            put(DatabaseHelper.COLUMN_TYPE, data.type)
            put(DatabaseHelper.COLUMN_CONTENT, data.content)
            put(DatabaseHelper.COLUMN_TIMESTAMP, data.timestamp)
        }
        db.insert(DatabaseHelper.TABLE_DATA, null, values)
    }

    override suspend fun setCellAttribute(attr: CellAttributeEntity): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_ROOM_ID, attr.roomId)
            put(DatabaseHelper.COLUMN_CELL_ID, attr.cellId)
            put(DatabaseHelper.COLUMN_IS_ENTRANCE, if (attr.isEntrance) 1 else 0)
            put(DatabaseHelper.COLUMN_IS_EXIT, if (attr.isExit) 1 else 0)
            attr.isLinkable.toInt()?.let { put(DatabaseHelper.COLUMN_IS_LINKABLE, it) }
        }
        db.insertWithOnConflict(
            DatabaseHelper.TABLE_ATTRIBUTES,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    override fun getCellAttribute(roomId: Long, cellId: Int): Flow<CellAttributeEntity?> = flow {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_ATTRIBUTES,
            null,
            "${DatabaseHelper.COLUMN_ROOM_ID} = ? AND ${DatabaseHelper.COLUMN_CELL_ID} = ?",
            arrayOf(roomId.toString(), cellId.toString()),
            null, null, null
        )
        val attr = if (cursor.moveToFirst()) cursor.toCellAttributeEntity() else null
        cursor.close()
        emit(attr)
    }.flowOn(Dispatchers.IO)

    override fun getSessionByRoomAndCell(roomId: Long, cellId: Int): Flow<ScanSessionEntity?> = flow {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_SESSIONS,
            null,
            "${DatabaseHelper.COLUMN_ROOM_ID} = ? AND ${DatabaseHelper.COLUMN_CELL_ID} = ?",
            arrayOf(roomId.toString(), cellId.toString()),
            null, null,
            "${DatabaseHelper.COLUMN_TIMESTAMP} DESC",
            "1"
        )
        val session = if (cursor.moveToFirst()) cursor.toSessionEntity() else null
        cursor.close()
        emit(session)
    }.flowOn(Dispatchers.IO)

    override fun getSessionsByRoom(roomId: Long): Flow<List<ScanSessionEntity>> = flow {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_SESSIONS,
            null,
            "${DatabaseHelper.COLUMN_ROOM_ID} = ?",
            arrayOf(roomId.toString()),
            null, null,
            "${DatabaseHelper.COLUMN_TIMESTAMP} DESC"
        )
        val sessions = mutableListOf<ScanSessionEntity>()
        while (cursor.moveToNext()) {
            sessions.add(cursor.toSessionEntity())
        }
        cursor.close()
        emit(sessions)
    }.flowOn(Dispatchers.IO)

    override suspend fun updateCellLinkableStatus(roomId: Long, cellId: Int, isLinkable: Boolean?): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            isLinkable.toInt()?.let { put(DatabaseHelper.COLUMN_IS_LINKABLE, it) }
                ?: putNull(DatabaseHelper.COLUMN_IS_LINKABLE)
        }
        db.update(
            DatabaseHelper.TABLE_ATTRIBUTES,
            values,
            "${DatabaseHelper.COLUMN_ROOM_ID} = ? AND ${DatabaseHelper.COLUMN_CELL_ID} = ?",
            arrayOf(roomId.toString(), cellId.toString())
        )
    }

    override suspend fun deleteCellData(roomId: Long, cellId: Int): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        // Get all session IDs for this room/cell
        val cursor = db.query(
            DatabaseHelper.TABLE_SESSIONS,
            arrayOf(DatabaseHelper.COLUMN_ID),
            "${DatabaseHelper.COLUMN_ROOM_ID} = ? AND ${DatabaseHelper.COLUMN_CELL_ID} = ?",
            arrayOf(roomId.toString(), cellId.toString()),
            null, null, null
        )
        val sessionIds = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            sessionIds.add(cursor.getLong(0))
        }
        cursor.close()

        // Delete scan_data for these sessions (CASCADE will handle this too, but we're explicit)
        if (sessionIds.isNotEmpty()) {
            val placeholders = sessionIds.joinToString(",") { "?" }
            db.delete(
                DatabaseHelper.TABLE_DATA,
                "${DatabaseHelper.COLUMN_SESSION_ID} IN ($placeholders)",
                sessionIds.map { it.toString() }.toTypedArray()
            )
        }
    }

    override suspend fun deleteSessionsByCell(roomId: Long, cellId: Int): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        // Foreign keys will cascade delete related scan_data
        db.delete(
            DatabaseHelper.TABLE_SESSIONS,
            "${DatabaseHelper.COLUMN_ROOM_ID} = ? AND ${DatabaseHelper.COLUMN_CELL_ID} = ?",
            arrayOf(roomId.toString(), cellId.toString())
        )
    }

    override suspend fun deleteCellAttribute(roomId: Long, cellId: Int): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            DatabaseHelper.TABLE_ATTRIBUTES,
            "${DatabaseHelper.COLUMN_ROOM_ID} = ? AND ${DatabaseHelper.COLUMN_CELL_ID} = ?",
            arrayOf(roomId.toString(), cellId.toString())
        )
    }

    override fun getScanCounts(roomId: Long): Flow<Map<Int, Int>> = flow {
        val db = dbHelper.readableDatabase
        // Query to count scan_data grouped by cellId for a given room
        val cursor = db.rawQuery(
            """
            SELECT s.${DatabaseHelper.COLUMN_CELL_ID}, COUNT(d.${DatabaseHelper.COLUMN_ID}) as count
            FROM ${DatabaseHelper.TABLE_SESSIONS} s
            LEFT JOIN ${DatabaseHelper.TABLE_DATA} d ON s.${DatabaseHelper.COLUMN_ID} = d.${DatabaseHelper.COLUMN_SESSION_ID}
            WHERE s.${DatabaseHelper.COLUMN_ROOM_ID} = ?
            GROUP BY s.${DatabaseHelper.COLUMN_CELL_ID}
            """.trimIndent(),
            arrayOf(roomId.toString())
        )
        val counts = mutableMapOf<Int, Int>()
        while (cursor.moveToNext()) {
            val cellId = cursor.getInt(0)
            val count = cursor.getInt(1)
            counts[cellId] = count
        }
        cursor.close()
        emit(counts)
    }.flowOn(Dispatchers.IO)

    override fun getScanDataCountsByType(roomId: Long, cellId: Int): Flow<Map<String, Int>> = flow {
        val db = dbHelper.readableDatabase
        // Query to count scan_data by type for a specific cell
        val cursor = db.rawQuery(
            """
            SELECT d.${DatabaseHelper.COLUMN_TYPE}, COUNT(d.${DatabaseHelper.COLUMN_ID}) as count
            FROM ${DatabaseHelper.TABLE_SESSIONS} s
            INNER JOIN ${DatabaseHelper.TABLE_DATA} d ON s.${DatabaseHelper.COLUMN_ID} = d.${DatabaseHelper.COLUMN_SESSION_ID}
            WHERE s.${DatabaseHelper.COLUMN_ROOM_ID} = ? AND s.${DatabaseHelper.COLUMN_CELL_ID} = ?
            GROUP BY d.${DatabaseHelper.COLUMN_TYPE}
            """.trimIndent(),
            arrayOf(roomId.toString(), cellId.toString())
        )
        val counts = mutableMapOf<String, Int>()
        while (cursor.moveToNext()) {
            val type = cursor.getString(0) ?: "UNKNOWN"
            val count = cursor.getInt(1)
            counts[type] = count
        }
        cursor.close()
        emit(counts)
    }.flowOn(Dispatchers.IO)

    override suspend fun deleteRoom(roomId: Long): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        // Delete the room - CASCADE will handle related sessions, data, and attributes
        db.delete(
            DatabaseHelper.TABLE_ROOMS,
            "${DatabaseHelper.COLUMN_ID} = ?",
            arrayOf(roomId.toString())
        )
    }

    // Helper extension functions to convert Cursor to entities

    private fun Cursor.toRoomEntity() = RoomEntity(
        id = getLong(getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)),
        name = getString(getColumnIndexOrThrow(DatabaseHelper.COLUMN_NAME)),
        timestamp = getLong(getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP))
    )

    private fun Cursor.toSessionEntity() = ScanSessionEntity(
        id = getLong(getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)),
        roomId = getLong(getColumnIndexOrThrow(DatabaseHelper.COLUMN_ROOM_ID)),
        cellId = getInt(getColumnIndexOrThrow(DatabaseHelper.COLUMN_CELL_ID)),
        timestamp = getLong(getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP))
    )

    private fun Cursor.toCellAttributeEntity() = CellAttributeEntity(
        roomId = getLong(getColumnIndexOrThrow(DatabaseHelper.COLUMN_ROOM_ID)),
        cellId = getInt(getColumnIndexOrThrow(DatabaseHelper.COLUMN_CELL_ID)),
        isEntrance = getInt(getColumnIndexOrThrow(DatabaseHelper.COLUMN_IS_ENTRANCE)) == 1,
        isExit = getInt(getColumnIndexOrThrow(DatabaseHelper.COLUMN_IS_EXIT)) == 1,
        isLinkable = getColumnIndex(DatabaseHelper.COLUMN_IS_LINKABLE).let { idx ->
            if (idx >= 0 && !isNull(idx)) getInt(idx).toBoolean() else null
        }
    )
}
