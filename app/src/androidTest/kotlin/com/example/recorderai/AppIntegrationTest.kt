package com.example.recorderai

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.release
import androidx.test.espresso.intent.Intents.init
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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

    @Test
    fun exportLastSession_triggers_share_intent() {
        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val root = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)!!
        val sessions = File(root, "RecorderAI")
        sessions.mkdirs()
        val last = File(sessions, "Session_test_export")
        last.mkdirs()
        File(last, "f.txt").writeText("hello")

        // intercept intents
        init()
        try {
            // use an Activity context so Espresso Intents can intercept started activities
            androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    runBlocking {
                        exportLastSession(activity)
                    }
                }
            }

            intended(IntentMatchers.hasAction(Intent.ACTION_CHOOSER))
        } finally {
            release()
            root.deleteRecursively()
        }
    }
}
