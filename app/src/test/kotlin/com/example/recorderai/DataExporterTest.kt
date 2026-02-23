package com.example.recorderai

import com.example.recorderai.data.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for DataExporter functionality.
 * Tests the export data structure and JSON serialization.
 */
@RunWith(JUnit4::class)
class DataExporterTest {

    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    }

    @Test
    fun `ExportData serializes correctly with all fields`() {
        val exportData = ExportData(
            exportDate = 1708704000000,
            exportDateReadable = "23/02/2026 16:40:00",
            app = "RecorderAI",
            version = "1.0",
            exportType = "FULL",
            rooms = listOf(
                RoomExport(
                    id = 1,
                    name = "Sala de estar",
                    timestamp = 1708600000000,
                    cells = listOf(
                        CellExport(
                            cellId = 0,
                            displayName = "Puerta principal",
                            isEntrance = true,
                            isExit = false,
                            isLinkable = true
                        )
                    )
                )
            ),
            sessions = listOf(
                SessionExport(
                    id = 101,
                    roomId = 1,
                    roomName = "Sala de estar",
                    cellId = 0,
                    cellName = "Puerta principal",
                    timestamp = 1708601000000,
                    dataCount = 45
                )
            ),
            scanData = listOf(
                ScanDataExport(
                    id = 1001,
                    sessionId = 101,
                    roomId = 1,
                    cellId = 0,
                    type = "WIFI",
                    timestamp = 1708601001000,
                    content = "{\"test\": \"data\"}"
                )
            ),
            statistics = ExportStatistics(
                totalRooms = 1,
                totalSessions = 1,
                totalScanRecords = 1,
                recordsByType = mapOf("WIFI" to 1),
                dateRange = DateRange(start = 1708601001000, end = 1708601001000)
            )
        )

        val json = gson.toJson(exportData)

        // Verify JSON contains expected fields
        assertTrue(json.contains("\"app\": \"RecorderAI\""))
        assertTrue(json.contains("\"version\": \"1.0\""))
        assertTrue(json.contains("\"exportType\": \"FULL\""))
        assertTrue(json.contains("\"name\": \"Sala de estar\""))
        assertTrue(json.contains("\"type\": \"WIFI\""))
        assertTrue(json.contains("\"totalRooms\": 1"))
    }

    @Test
    fun `ExportData handles empty data correctly`() {
        val exportData = ExportData(
            exportType = "FULL",
            rooms = emptyList(),
            sessions = emptyList(),
            scanData = emptyList(),
            statistics = null
        )

        val json = gson.toJson(exportData)

        // Should still serialize without errors
        assertTrue(json.contains("\"rooms\": []"))
        assertTrue(json.contains("\"sessions\": []"))
        assertTrue(json.contains("\"scanData\": []"))
    }

    @Test
    fun `CellExport handles null displayName correctly`() {
        val cellExport = CellExport(
            cellId = 5,
            displayName = null,
            isEntrance = false,
            isExit = false,
            isLinkable = null
        )

        val json = gson.toJson(cellExport)

        // Should include null values due to serializeNulls
        assertTrue(json.contains("\"displayName\": null"))
        assertTrue(json.contains("\"isLinkable\": null"))
    }

    @Test
    fun `ExportStatistics calculates correctly`() {
        val stats = ExportStatistics(
            totalRooms = 3,
            totalSessions = 10,
            totalScanRecords = 150,
            recordsByType = mapOf(
                "WIFI" to 50,
                "BLUETOOTH" to 30,
                "CELL" to 40,
                "MAGNETOMETER" to 30
            ),
            dateRange = DateRange(
                start = 1708600000000,
                end = 1708700000000
            )
        )

        val json = gson.toJson(stats)

        assertTrue(json.contains("\"totalRooms\": 3"))
        assertTrue(json.contains("\"totalSessions\": 10"))
        assertTrue(json.contains("\"totalScanRecords\": 150"))
        assertTrue(json.contains("\"WIFI\": 50"))
        assertTrue(json.contains("\"BLUETOOTH\": 30"))
    }

    @Test
    fun `DateRange formats dates correctly`() {
        val dateRange = DateRange(
            start = 1708600000000,
            end = 1708700000000
        )

        // Check that readable dates are generated
        assertNotNull(dateRange.startReadable)
        assertNotNull(dateRange.endReadable)
        assertTrue(dateRange.startReadable.isNotEmpty())
        assertTrue(dateRange.endReadable.isNotEmpty())
    }

    @Test
    fun `ExportResult sealed class works correctly`() {
        val success = ExportResult.Success(java.io.File("/tmp/test.json"))
        val error = ExportResult.Error("Test error message")

        // Test pattern matching
        when (success) {
            is ExportResult.Success -> assertEquals("test.json", success.file.name)
            is ExportResult.Error -> fail("Should be Success")
        }

        when (error) {
            is ExportResult.Success -> fail("Should be Error")
            is ExportResult.Error -> assertEquals("Test error message", error.message)
        }
    }

    @Test
    fun `ScanDataWithSessionInfo data class works correctly`() {
        val data = ScanDataWithSessionInfo(
            id = 1L,
            sessionId = 100L,
            roomId = 1L,
            cellId = 5,
            type = "WIFI",
            content = "{\"ssid\": \"TestNetwork\"}",
            timestamp = 1708600000000
        )

        assertEquals(1L, data.id)
        assertEquals(100L, data.sessionId)
        assertEquals(1L, data.roomId)
        assertEquals(5, data.cellId)
        assertEquals("WIFI", data.type)
        assertTrue(data.content.contains("TestNetwork"))
    }
}