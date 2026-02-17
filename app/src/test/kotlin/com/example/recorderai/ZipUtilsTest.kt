package com.example.recorderai

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZipUtilsTest {

    @BeforeEach
    fun setup() = io.mockk.clearAllMocks()

    @Nested
    inner class `zipFolder` {
        @Test
        fun `should zip files in folder and return true`() {
            val tempDir = createTempDir(prefix = "ziptest")
            val a = File(tempDir, "a.txt").apply { writeText("hello") }
            val b = File(tempDir, "b.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val zipFile = File.createTempFile("out", ".zip")

            val result = ZipUtils.zipFolder(tempDir, zipFile)

            result shouldBe true
            zipFile.exists() shouldBe true

            val entries = java.util.zip.ZipFile(zipFile).use { zf ->
                zf.entries().asSequence().map { it.name }.toList()
            }

            entries shouldContain "a.txt"
            entries shouldContain "b.bin"

            tempDir.deleteRecursively()
            zipFile.delete()
        }

        @Test
        fun `should return false for non-existent source folder`() {
            val nonExistent = File("/this-folder-does-not-exist-${System.currentTimeMillis()}")
            val zipFile = File.createTempFile("out", ".zip")

            val result = ZipUtils.zipFolder(nonExistent, zipFile)

            result shouldBe false
            zipFile.delete()
        }
    }

    @AfterAll
    fun tearDown() {
        // nothing to clean globally
    }
}
