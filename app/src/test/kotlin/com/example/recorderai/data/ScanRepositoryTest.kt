package com.example.recorderai.data

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for ScanRepository.
 * Uses MockK to mock the underlying DAO.
 */
class ScanRepositoryTest {

    private lateinit var dao: ScanDao
    private lateinit var repository: ScanRepository

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = ScanRepository(dao)
    }

    @Nested
    inner class RoomOperations {
        @Test
        fun `createRoom inserts room and returns id`() = runBlocking {
            // Given
            coEvery { dao.insertRoom(any()) } returns 1L

            // When
            val roomId = repository.createRoom("Test Room")

            // Then
            org.junit.jupiter.api.Assertions.assertEquals(1L, roomId)
            coVerify { dao.insertRoom(any()) }
        }

        @Test
        fun `getAllRooms returns rooms from DAO`() = runBlocking {
            // Given
            val rooms = listOf(
                RoomEntity(id = 1, name = "Room 1", timestamp = 1000L),
                RoomEntity(id = 2, name = "Room 2", timestamp = 2000L)
            )
            every { dao.getAllRooms() } returns flowOf(rooms)

            // When
            val result = repository.getAllRooms().first()

            // Then
            org.junit.jupiter.api.Assertions.assertEquals(2, result.size)
            org.junit.jupiter.api.Assertions.assertEquals("Room 1", result[0].name)
        }

        @Test
        fun `deleteRoom calls DAO deleteRoom`() = runBlocking {
            // Given
            coEvery { dao.deleteRoom(any()) } returns Unit

            // When
            repository.deleteRoom(1L)

            // Then
            coVerify { dao.deleteRoom(1L) }
        }

        @Test
        fun `updateRoomName calls DAO updateRoomName`() = runBlocking {
            // Given
            coEvery { dao.updateRoomName(any(), any()) } returns Unit

            // When
            repository.updateRoomName(1L, "New Name")

            // Then
            coVerify { dao.updateRoomName(1L, "New Name") }
        }
    }

    @Nested
    inner class SessionOperations {
        @Test
        fun `createSession inserts session and returns id`() = runBlocking {
            // Given
            coEvery { dao.insertSession(any()) } returns 1L

            // When
            val sessionId = repository.createSession(roomId = 1L, cellId = 5)

            // Then
            org.junit.jupiter.api.Assertions.assertEquals(1L, sessionId)
        }

        @Test
        fun `getSessionByRoomAndCell returns session from DAO`() = runBlocking {
            // Given
            val session = ScanSessionEntity(
                id = 1L,
                roomId = 1L,
                cellId = 5,
                timestamp = 1000L
            )
            every { dao.getSessionByRoomAndCell(1L, 5) } returns flowOf(session)

            // When
            val result = repository.getSessionByRoomAndCell(1L, 5)

            // Then
            org.junit.jupiter.api.Assertions.assertNotNull(result)
            org.junit.jupiter.api.Assertions.assertEquals(1L, result!!.id)
        }

        @Test
        fun `getSessionsByRoom returns sessions from DAO`() = runBlocking {
            // Given
            val sessions = listOf(
                ScanSessionEntity(id = 1L, roomId = 1L, cellId = 1, timestamp = 1000L),
                ScanSessionEntity(id = 2L, roomId = 1L, cellId = 2, timestamp = 2000L)
            )
            every { dao.getSessionsByRoom(1L) } returns flowOf(sessions)

            // When
            val result = repository.getSessionsByRoom(1L).first()

            // Then
            org.junit.jupiter.api.Assertions.assertEquals(2, result.size)
        }
    }

    @Nested
    inner class DataOperations {
        @Test
        fun `insertScanData calls DAO insertData`() = runBlocking {
            // Given
            coEvery { dao.insertData(any()) } returns 1L

            // When
            repository.insertScanData(
                ScanDataEntity(
                    sessionId = 1L,
                    type = "WIFI",
                    content = "{}",
                    timestamp = 1000L
                )
            )

            // Then
            coVerify { dao.insertData(any()) }
        }
    }

    @Nested
    inner class CellAttributeOperations {
        @Test
        fun `setCellAttribute calls DAO setCellAttribute`() = runBlocking {
            // Given
            coEvery { dao.setCellAttribute(any()) } returns Unit

            // When
            repository.setCellAttribute(
                CellAttributeEntity(
                    roomId = 1L,
                    cellId = 5,
                    isLinkable = true,
                    displayName = "Test Cell"
                )
            )

            // Then
            coVerify { dao.setCellAttribute(any()) }
        }

        @Test
        fun `getCellAttribute returns attribute from DAO`() = runBlocking {
            // Given
            val attribute = CellAttributeEntity(
                roomId = 1L,
                cellId = 5,
                isLinkable = true,
                displayName = "Test Cell"
            )
            every { dao.getCellAttribute(1L, 5) } returns flowOf(attribute)

            // When
            val result = repository.getCellAttribute(1L, 5)

            // Then
            org.junit.jupiter.api.Assertions.assertNotNull(result)
            org.junit.jupiter.api.Assertions.assertEquals(true, result!!.isLinkable)
        }

        @Test
        fun `updateCellLinkableStatus calls DAO`() = runBlocking {
            // Given
            coEvery { dao.updateCellLinkableStatus(any(), any(), any()) } returns Unit

            // When
            repository.updateCellLinkableStatus(1L, 5, true)

            // Then
            coVerify { dao.updateCellLinkableStatus(1L, 5, true) }
        }
    }

    @Nested
    inner class AggregationOperations {
        @Test
        fun `getScanCounts returns counts from DAO`() = runBlocking {
            // Given
            val counts = mapOf(1 to 5, 2 to 3, 3 to 10)
            every { dao.getScanCounts(1L) } returns flowOf(counts)

            // When
            val result = repository.getScanCounts(1L).first()

            // Then
            org.junit.jupiter.api.Assertions.assertEquals(5, result[1])
            org.junit.jupiter.api.Assertions.assertEquals(3, result[2])
        }

        @Test
        fun `getScanDataCountsByType returns type counts from DAO`() = runBlocking {
            // Given
            val counts = mapOf(
                "WIFI" to 10,
                "CELL" to 5,
                "BT_MAGNET" to 3
            )
            every { dao.getScanDataCountsByType(1L, 5) } returns flowOf(counts)

            // When
            val result = repository.getScanDataCountsByType(1L, 5).first()

            // Then
            org.junit.jupiter.api.Assertions.assertEquals(10, result["WIFI"])
            org.junit.jupiter.api.Assertions.assertEquals(5, result["CELL"])
        }
    }
}
