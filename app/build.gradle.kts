import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // ksp plugin temporarily disabled to bypass KSP runtime incompatibility during tests
} 

apply(plugin = "org.jetbrains.kotlinx.kover")

android {
    namespace = "com.example.recorderai"
    compileSdk = 36 // Correcto para Android 16 (Baklava)

    defaultConfig {
        applicationId = "com.example.recorderai"
        minSdk = 29  // BAJAR ESTO es la clave para que instale en tu S21 FE
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // TEST deps
    // JUnit 5 for unit tests
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.9.3")
    // Kotlin testing helpers
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("org.robolectric:robolectric:4.11.1") // Use JUnit 4 @RunWith for best compatibility
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.14.4")
    testImplementation("io.mockk:mockk-agent-jvm:1.14.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Android instrumented tests / Compose UI tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // --- DEPENDENCIAS CRÍTICAS ---
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Para el loop de 31s
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // ksp(libs.androidx.room.compiler)  // disabled for test run
}

// --- Helper task: start emulator if no device is connected (used for debug/instrumented tests) ---
// AVD name is configurable via project property `emulatorAvd` (e.g. -PemulatorAvd=My_AVD)


tasks.register("startEmulatorIfNeeded") {
    group = "verification"
    description = "Start AVD (configurable via project property 'emulatorAvd') if no device is connected (used by debug instrumentation tests)."

    doLast {
        // resolve AVD lazily inside the task action to avoid configuration-cache serialization issues
        val avd = (project.findProperty("emulatorAvd") as? String) ?: "Medium_Phone_API_36.1"

        // if adb isn't available (headless/container), skip gracefully
        val adbAvailable = runCatching {
            val p = ProcessBuilder("adb", "devices").redirectErrorStream(true).start()
            p.waitFor(5, TimeUnit.SECONDS)
            true
        }.getOrDefault(false)
        if (!adbAvailable) {
            println("`adb` not found in PATH — running in headless environment. Skipping emulator start.")
            return@doLast
        }

        // check for already connected device
        val devicesOut = ProcessBuilder("adb", "devices").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        if (devicesOut.lines().any { it.endsWith("\tdevice") }) {
            println("Device already connected — skipping emulator start")
            return@doLast
        }

        println("No device detected — launching emulator '$'" + avd + "' in background...")
        // start emulator in background so Gradle keeps running
        ProcessBuilder("bash", "-c", "emulator -avd " + avd + " -no-window -no-audio >/dev/null 2>&1 &").start()

        // wait for emulator to finish booting
        val maxWaitSec = 120
        var waited = 0
        while (waited < maxWaitSec) {
            val bootOut = ProcessBuilder("adb", "shell", "getprop", "sys.boot_completed").redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim()
            if (bootOut == "1") {
                println("Emulator booted after ${'$'}waited s")
                return@doLast
            }
            Thread.sleep(2000)
            waited += 2
            print('.')
        }
        throw GradleException("Emulator did not boot within ${'$'}maxWaitSec seconds")
    }
}

// Only run connected instrumentation tests if a device is present (prevents failures in headless CI)
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    onlyIf {
        val adbOutput = runCatching {
            ProcessBuilder("adb", "devices").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        }.getOrNull()
        adbOutput != null && adbOutput.lines().any { it.endsWith("\tdevice") }
    }
}