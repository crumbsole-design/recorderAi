package com.example.recorderai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.recorderai.data.RoomEntity
import com.example.recorderai.data.ScanDao
import com.example.recorderai.data.ScanDaoImpl
import com.example.recorderai.data.ScanDataEntity
import com.example.recorderai.data.ScanSessionEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for the complete data collection flow.
 * Uses an in-memory database to test the full flow: 
 * create room → create session → start collection → verify data saved → stop → verify persistence
 */
@RunWith(AndroidJUnit4::class)
class DataCollectionIntegrationTest {

    private lateinit var context: Context
    private lateinit var dao: ScanDao
    private lateinit var dbHelper: com.example.recorderai.data.DatabaseHelper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Use the real database helper with actual SQLite database
        // Context is in test mode, so database will be in test directory
        dbHelper = com.example.recorderai.data.DatabaseHelper(context)
        dao = ScanDaoImpl(dbHelper)
    }

    @After
    fun tearDown() {
        // Clear all tables to ensure clean state for next test
        val db = dbHelper.writableDatabase
        db.execSQL("DELETE FROM ${com.example.recorderai.data.DatabaseHelper.TABLE_DATA}")
        db.execSQL("DELETE FROM ${com.example.recorderai.data.DatabaseHelper.TABLE_SESSIONS}")
        db.execSQL("DELETE FROM ${com.example.recorderai.data.DatabaseHelper.TABLE_ATTRIBUTES}")
        db.execSQL("DELETE FROM ${com.example.recorderai.data.DatabaseHelper.TABLE_ROOMS}")
        dbHelper.close()
    }

    @Test
    fun testFullFlowCreateRoomSessionAndRetrieveData() {
        runBlocking {
            // Step 1: Create a room
            val roomId = dao.insertRoom(RoomEntity(name = "Test Room", timestamp = System.currentTimeMillis()))
            roomId shouldNotBe -1L

            // Verify room was created
            val rooms = dao.getAllRooms().first()
            rooms shouldHaveSize 1
            rooms[0].name shouldBe "Test Room"

            // Step 2: Create a session for a specific cell
            val sessionId = dao.insertSession(
                ScanSessionEntity(roomId = roomId, cellId = 5, timestamp = System.currentTimeMillis())
            )
            sessionId shouldNotBe -1L

            // Verify session was created
            val session = dao.getSessionByRoomAndCell(roomId, 5).first()
            session shouldNotBe null
            session!!.id shouldBe sessionId

            // Step 3: Simulate saving scan data (as DataCollectionService would do)
            val wifiJson = """{"timestamp":1000,"wifiNetworks":[{"ssid":"TestNet","bssid":"00:11:22:33:44:55","rssi":-50}]}"""

            dao.insertData(
                ScanDataEntity(
                    sessionId = sessionId,
                    type = "WIFI",
                    content = wifiJson,
                    timestamp = System.currentTimeMillis()
                )
            )

            // Step 4: Verify data was saved
            val dataCounts = dao.getScanDataCountsByType(roomId, 5).first()
            dataCounts["WIFI"] shouldBe 1

            // Step 5: Get total scan count for the room
            val scanCounts = dao.getScanCounts(roomId).first()
            scanCounts[5] shouldBe 1

            // Step 6: Simulate stopping collection and verify data persists
            // (In real app, this would stop the service)

            // Verify data is still accessible
            val finalDataCounts = dao.getScanDataCountsByType(roomId, 5).first()
            finalDataCounts["WIFI"] shouldBe 1
        }
    }

    @Test
    fun testFullFlowMultipleCellsWithDifferentDataTypes() {
        runBlocking {
            // Create room
            val roomId = dao.insertRoom(RoomEntity(name = "Multi-Cell Room", timestamp = System.currentTimeMillis()))

            // Create sessions for multiple cells
            val session1 = dao.insertSession(ScanSessionEntity(roomId = roomId, cellId = 0, timestamp = System.currentTimeMillis()))
            val session2 = dao.insertSession(ScanSessionEntity(roomId = roomId, cellId = 1, timestamp = System.currentTimeMillis()))
            val session3 = dao.insertSession(ScanSessionEntity(roomId = roomId, cellId = 14, timestamp = System.currentTimeMillis()))

            // Add different types of data to different cells

            // Cell 0: WiFi data
            dao.insertData(ScanDataEntity(sessionId = session1, type = "WIFI", content = "{}", timestamp = System.currentTimeMillis()))

            // Cell 1: Bluetooth + Magnetometer data
            dao.insertData(ScanDataEntity(sessionId = session2, type = "BLUETOOTH", content = "{}", timestamp = System.currentTimeMillis()))
            dao.insertData(ScanDataEntity(sessionId = session2, type = "MAGNETOMETER", content = "{}", timestamp = System.currentTimeMillis()))

            // Cell 14: Mixed data
            dao.insertData(ScanDataEntity(sessionId = session3, type = "WIFI", content = "{}", timestamp = System.currentTimeMillis()))
            dao.insertData(ScanDataEntity(sessionId = session3, type = "CELL", content = "{}", timestamp = System.currentTimeMillis()))

            // Verify counts for each cell
            val counts0 = dao.getScanDataCountsByType(roomId, 0).first()
            counts0["WIFI"] shouldBe 1

            val counts1 = dao.getScanDataCountsByType(roomId, 1).first()
            counts1["BLUETOOTH"] shouldBe 1
            counts1["MAGNETOMETER"] shouldBe 1

            val counts14 = dao.getScanDataCountsByType(roomId, 14).first()
            counts14["WIFI"] shouldBe 1
            counts14["CELL"] shouldBe 1

            // Verify total scan counts for the room
            val totalCounts = dao.getScanCounts(roomId).first()
            totalCounts[0] shouldBe 1
            totalCounts[1] shouldBe 2
            totalCounts[14] shouldBe 2
        }
    }

    @Test
    fun testFullFlowCellAttributeConfigWithDisplayName() {
        runBlocking {
            // Create room
            val roomId = dao.insertRoom(RoomEntity(name = "Config Test Room", timestamp = System.currentTimeMillis()))

            // Configure cell with linkable status and display name
            dao.setCellAttribute(
                com.example.recorderai.data.CellAttributeEntity(
                    roomId = roomId,
                    cellId = 7,
                    isLinkable = true,
                    displayName = "Puerta principal"
                )
            )

            // Verify attribute was saved
            val attr = dao.getCellAttribute(roomId, 7).first()
            attr shouldNotBe null
            attr!!.isLinkable shouldBe true
            attr.displayName shouldBe "Puerta principal"

            // Create session for the cell
            val sessionId = dao.insertSession(
                ScanSessionEntity(roomId = roomId, cellId = 7, timestamp = System.currentTimeMillis())
            )

            // Add some data
            dao.insertData(ScanDataEntity(sessionId = sessionId, type = "WIFI", content = "{}", timestamp = System.currentTimeMillis()))

            // Verify data count
            val counts = dao.getScanDataCountsByType(roomId, 7).first()
            counts["WIFI"] shouldBe 1

            // Update the cell configuration
            dao.setCellAttribute(
                com.example.recorderai.data.CellAttributeEntity(
                    roomId = roomId,
                    cellId = 7,
                    isLinkable = false,
                    displayName = "Updated: Ventana lateral"
                )
            )

            // Verify updated attribute
            val updatedAttr = dao.getCellAttribute(roomId, 7).first()
            updatedAttr shouldNotBe null
            updatedAttr!!.isLinkable shouldBe false
            updatedAttr.displayName shouldBe "Updated: Ventana lateral"

            // Verify data is still accessible (persistence check)
            val finalCounts = dao.getScanDataCountsByType(roomId, 7).first()
            finalCounts["WIFI"] shouldBe 1
        }
    }

    @Test
    fun testFullFlowRegenerateCellClearsDataPreservesConfig() {
        runBlocking {
            // Create room and configure cell
            val roomId = dao.insertRoom(RoomEntity(name = "Regen Test Room", timestamp = System.currentTimeMillis()))

            dao.setCellAttribute(
                com.example.recorderai.data.CellAttributeEntity(
                    roomId = roomId,
                    cellId = 3,
                    isLinkable = true,
                    displayName = "Entrada principal"
                )
            )

            // Create session and add data
            val sessionId = dao.insertSession(ScanSessionEntity(roomId = roomId, cellId = 3, timestamp = System.currentTimeMillis()))
            dao.insertData(ScanDataEntity(sessionId = sessionId, type = "WIFI", content = "{}", timestamp = System.currentTimeMillis()))

            // Verify data exists
            dao.getScanDataCountsByType(roomId, 3).first()["WIFI"] shouldBe 1

            // Delete cell data (simulating regeneration)
            dao.deleteCellData(roomId, 3)
            dao.deleteSessionsByCell(roomId, 3)

            // Verify data is cleared
            dao.getScanDataCountsByType(roomId, 3).first().values.sum() shouldBe 0

            // But attribute should be preserved completely (both isLinkable and displayName)
            val attr = dao.getCellAttribute(roomId, 3).first()
            attr shouldNotBe null
            attr!!.isLinkable shouldBe true  // Configuration preserved
            attr.displayName shouldBe "Entrada principal"  // Name preserved
        }
    }

    @Test
    fun testFullFlowDeleteRoomRemovesAllData() {
        runBlocking {
            // Create multiple rooms with data
            val room1Id = dao.insertRoom(RoomEntity(name = "Room 1", timestamp = System.currentTimeMillis()))
            val room2Id = dao.insertRoom(RoomEntity(name = "Room 2", timestamp = System.currentTimeMillis()))

            // Add data to room 1
            val session1 = dao.insertSession(ScanSessionEntity(roomId = room1Id, cellId = 0, timestamp = System.currentTimeMillis()))
            dao.insertData(ScanDataEntity(sessionId = session1, type = "WIFI", content = "{}", timestamp = System.currentTimeMillis()))

            // Add data to room 2
            val session2 = dao.insertSession(ScanSessionEntity(roomId = room2Id, cellId = 0, timestamp = System.currentTimeMillis()))
            dao.insertData(ScanDataEntity(sessionId = session2, type = "BLUETOOTH", content = "{}", timestamp = System.currentTimeMillis()))

            // Verify both rooms have data
            dao.getScanCounts(room1Id).first()[0] shouldBe 1
            dao.getScanCounts(room2Id).first()[0] shouldBe 1

            // Delete room 1
            dao.deleteRoom(room1Id)

            // Verify only room 2 remains
            val rooms = dao.getAllRooms().first()
            rooms shouldHaveSize 1
            rooms[0].id shouldBe room2Id

            // Verify room 2 data is still intact
            dao.getScanCounts(room2Id).first()[0] shouldBe 1
        }
    }

    @Test
    fun testFullFlowConcurrentDataInsertion() {
        runBlocking {
            // Create room
            val roomId = dao.insertRoom(RoomEntity(name = "Concurrent Test Room", timestamp = System.currentTimeMillis()))

            // Create single session
            val sessionId = dao.insertSession(ScanSessionEntity(roomId = roomId, cellId = 5, timestamp = System.currentTimeMillis()))

            // Simulate concurrent insertions from multiple collection loops
            // (WiFi/Cell loop and BT/Magnetometer loop running in parallel)
            val scope = CoroutineScope(Dispatchers.IO)

            // Launch multiple concurrent insertions
            val jobs = (1..10).map { i ->
                scope.launch {
                    dao.insertData(
                        ScanDataEntity(
                            sessionId = sessionId,
                            type = if (i % 2 == 0) "WIFI" else "BLUETOOTH",
                            content = "{\"index\":$i}",
                            timestamp = System.currentTimeMillis() + i
                        )
                    )
                }
            }

            // Wait for all insertions to complete
            jobs.joinAll()

            // Verify all data was saved
            val counts = dao.getScanDataCountsByType(roomId, 5).first()
            counts["WIFI"] shouldBe 5
            counts["BLUETOOTH"] shouldBe 5
            counts.values.sum() shouldBe 10
        }
    }
}
