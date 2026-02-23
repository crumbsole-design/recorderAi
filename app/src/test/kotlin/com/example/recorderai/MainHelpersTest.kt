package com.example.recorderai

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MainHelpersTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        clearAllMocks()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class `permissions & service helpers` {
        @Test
        fun `hasAllPermissions returns true when granted`() {
            mockkStatic(ContextCompat::class)
            every { ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_GRANTED

            val ctx = mockk<Context>()
            hasAllPermissions(ctx, arrayOf("p1", "p2")) shouldBe true
        }

        @Test
        fun `hasAllPermissions returns false when a permission denied`() {
            mockkStatic(ContextCompat::class)
            every { ContextCompat.checkSelfPermission(any(), any()) } returnsMany listOf(android.content.pm.PackageManager.PERMISSION_GRANTED, android.content.pm.PackageManager.PERMISSION_DENIED)

            val ctx = mockk<Context>()
            hasAllPermissions(ctx, arrayOf("p1", "p2")) shouldBe false
        }

        @Test
        fun `isServiceRunning returns true when running service present`() {
            val am = mockk<ActivityManager>(relaxed=true)
            val running = ActivityManager.RunningServiceInfo()
            val comp = mockk<ComponentName>()
            every { comp.className } returns DataCollectionService::class.java.name
            running.service = comp
            every { am.getRunningServices(Int.MAX_VALUE) } returns listOf(running)

            val ctx = mockk<Context>()
            every { ctx.getSystemService(Context.ACTIVITY_SERVICE) } returns am
            
            com.example.recorderai.isServiceRunning(ctx) shouldBe true
        }

        @Test
        fun `start and stop wardriving call context startService`() {
            val ctx = mockk<Context>(relaxed = true)
            mockkStatic("android.os.Build") // ensure SDK checks don't crash
            mockkStatic(android.widget.Toast::class)
            every { android.widget.Toast.makeText(any(), any<String>(), any()) } returns mockk(relaxed = true)

            startWardrivingService(ctx)
            try {
                verify { ctx.startForegroundService(any()) }
            } catch (err: AssertionError) {
                verify { ctx.startService(any()) }
            }

            stopWardrivingService(ctx)
            verify { ctx.stopService(any()) }
        }
    }

    // Export tests moved to DataExporterTest.kt
    // The old exportLastSession and shareFile functions have been removed
    // and replaced with DataExporter.exportAndShare()
}
