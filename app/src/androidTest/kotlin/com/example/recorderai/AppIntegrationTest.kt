package com.example.recorderai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIntegrationTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.RECORD_AUDIO
    )

    @Test
    fun service_lifecycle_start_and_stop() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // start service on the main thread to avoid Toast/Looper issues
        instrumentation.runOnMainSync { startWardrivingService(ctx) }

        // should report running
        assertTrue(isServiceRunning(ctx))

        // stop on the main thread
        instrumentation.runOnMainSync { stopWardrivingService(ctx) }
    }
}
