package com.example.recorderai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Insert
    suspend fun insertRoom(room: RoomEntity): Long

    @Query("SELECT * FROM rooms")
    fun getAllRooms(): Flow<List<RoomEntity>>

    @Insert
    suspend fun insertSession(session: ScanSessionEntity): Long

    @Insert
    suspend fun insertData(data: ScanDataEntity)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun setCellAttribute(attribute: CellAttributeEntity)

    @Query("SELECT * FROM cell_attributes WHERE roomId = :roomId")
    fun getCellAttributes(roomId: Long): Flow<List<CellAttributeEntity>>

    @Query("SELECT * FROM scan_data WHERE sessionId = :sessionId")
    fun getSessionData(sessionId: Long): List<ScanDataEntity>
    
    // Aggregation for grid visualization
    @Query("""
        SELECT s.cellId, d.type, COUNT(*) as count 
        FROM scan_sessions s
        JOIN scan_data d ON s.id = d.sessionId
        WHERE s.roomId = :roomId
        GROUP BY s.cellId, d.type
    """)
    fun getScanCounts(roomId: Long): Flow<List<ScanCount>>
}

data class ScanCount(
    val cellId: Int,
    val type: String,
    val count: Int
)
