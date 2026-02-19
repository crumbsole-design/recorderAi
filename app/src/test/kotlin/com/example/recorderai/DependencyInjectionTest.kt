package com.example.recorderai

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class DependencyInjectionTest {

    // --- Test-only interfaces / classes to demonstrate manual DI ---
    private interface TimeProvider { fun now(): Long }
    private class Recorder(private val timeProvider: TimeProvider) {
        fun recordTimestamp(): Long = timeProvider.now()
    }

    private object ManualInjector {
        // mutable on purpose so tests can swap implementations
        var timeProvider: TimeProvider = object : TimeProvider { override fun now() = System.currentTimeMillis() }
    }

    @Nested
    inner class `Manual DI` {

        @Test
        fun `should allow constructor injection with a mock implementation`() {
            val tp = mockk<TimeProvider>()
            every { tp.now() } returns 123456L

            val recorder = Recorder(tp)

            recorder.recordTimestamp() shouldBe 123456L
        }

        @Test
        fun `should allow swapping provider in a manual injector`() {
            val tp = mockk<TimeProvider>()
            every { tp.now() } returns 42L

            // swap provider (manual DI)
            ManualInjector.timeProvider = tp

            val recorder = Recorder(ManualInjector.timeProvider)
            recorder.recordTimestamp() shouldBe 42L
        }
    }

    @Nested
    inner class `Hilt DI (placeholder)` {

        @Test @Disabled("Hilt not present in this project; manual DI tests used instead")
        fun `hilt injection test placeholder`() {
            // left intentionally empty — would verify Hilt component bindings if Hilt were available
        }
    }
}
