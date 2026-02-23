# Testing Guide for RecorderAI

This document provides comprehensive information about testing the RecorderAI Android application, including E2E tests with Espresso, UI tests with Jetpack Compose, and unit tests.

---

## Table of Contents

1. [Overview](#overview)
2. [What Are E2E Tests?](#what-are-e2e-tests)
3. [Dependencies](#dependencies)
4. [Project Structure](#project-structure)
5. [The Robot Pattern](#the-robot-pattern)
6. [Test Types](#test-types)
   - [Unit Tests](#unit-tests)
   - [Compose UI Tests](#compose-ui-tests)
   - [Integration Tests](#integration-tests)
7. [Running Tests](#running-tests)
8. [Best Practices](#best-practices)

---

## Overview

UI tests are essential for ensuring that Android apps function correctly from the end user's perspective. One of the best frameworks available for conducting these tests is **Espresso**, Android's UI testing tool.

This guide covers:
- Creating End-to-End (E2E) tests using Espresso
- Efficient project organization
- Leveraging the Robot Pattern
- Applying best testing practices

---

## What Are E2E Tests?

**E2E (End-to-End) tests** simulate real user interactions with your app, covering scenarios from the beginning to the end of a usage flow. This ensures that all components work properly when integrated and that the user experience meets expectations.

In the context of an Android app, E2E tests with Espresso allow you to simulate:
- Clicks
- Scrolls
- Text inputs
- Other interactions

This ensures that the app behaves as expected across different scenarios.

---

## Dependencies

The project uses the following testing dependencies, defined in `gradle/libs.versions.toml`:

### Core Testing Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| JUnit | 4.13.2 | Basic dependency for running unit tests |
| JUnit 5 (Jupiter) | 5.9.3 | Modern JUnit for unit tests |
| AndroidX Test | 1.3.0 | Extension of JUnit that supports instrumented tests |
| Espresso Core | 3.7.0 | Classic UI tests on Android |
| Compose UI Test | (via BOM) | For running instrumented tests on Jetpack Compose |

### Testing Support Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| Robolectric | 4.11.1 | Running Android unit tests on JVM |
| MockK | 1.14.4 | Kotlin mocking library |
| Kotest | 5.7.2 | Kotlin testing assertions |
| Kotlinx Coroutines Test | 1.7.3 | Testing coroutines |

### Gradle Dependencies (from app/build.gradle.kts)

```kotlin
// Unit Tests
testImplementation("junit:junit:4.13.2")
testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
testImplementation("io.kotest:kotest-assertions-core:5.7.2")
testImplementation("io.mockk:mockk:1.14.4")
testImplementation("org.robolectric:robolectric:4.11.1")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

// Android Instrumented Tests / Compose UI Tests
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
```

---

## Project Structure

The project follows a modular structure to ensure scalability and maintainability:

```
src
├── main/                          # Production code
│   └── kotlin/com/example/recorderai/
│       ├── MainActivity.kt
│       ├── DataCollectionService.kt
│       ├── data/                  # Data layer
│       ├── model/                 # Data models
│       └── ui/                    # UI layer (screens, viewmodels, theme)
│
├── test/                          # Unit tests (run on JVM)
│   └── kotlin/com/example/recorderai/
│       ├── DataCollectionServiceTest.kt   # Service tests with Robolectric
│       ├── MainDispatcherRule.kt          # Coroutine test helper
│       ├── ZipUtilsTest.kt                # Utility tests
│       ├── DependencyInjectionTest.kt     # DI tests
│       └── MainHelpersTest.kt             # Helper function tests
│
└── androidTest/                   # Instrumented tests (run on device/emulator)
    └── kotlin/com/example/recorderai/
        ├── MainActivityUiTest.kt           # Compose UI tests
        └── AppIntegrationTest.kt           # Integration tests with Espresso Intents
```

### Folder Organization

| Folder | Purpose |
|--------|---------|
| `helpers/` | Utility classes, like the Robot Pattern for encapsulating UI interactions |
| `cases/` | E2E test cases that cover different flows of the app |
| `screens/` | Focuses on UI tests for each specific screen or activity |

---

## The Robot Pattern

The **Robot Pattern** is an approach that helps separate UI interactions from test scenarios, making the code more organized and maintainable. Instead of directly interacting with UI components within tests, we create high-level methods that encapsulate those interactions.

### Benefits

- **Readability**: Tests focus on what we're verifying, not how to interact with the UI
- **Maintainability**: UI changes only require updating one place
- **Reusability**: Common interactions can be reused across tests
- **Separation of Concerns**: Test logic is separated from UI implementation details

### Example: Creating a Robot Class

```kotlin
// File: app/src/androidTest/kotlin/com/example/recorderai/helpers/MainActivityRobot.kt
class MainActivityRobot(private val composeTestRule: ComposeTestRule<*>) {
    
    fun clickOnStartButton() {
        composeTestRule
            .onNodeWithText("INICIAR")
            .performClick()
    }
    
    fun clickOnStopButton() {
        composeTestRule
            .onNodeWithText("DETENER")
            .performClick()
    }
    
    fun verifyServiceStatusIsRunning() {
        composeTestRule
            .onNodeWithText("ON")
            .assertIsDisplayed()
    }
    
    fun verifyServiceStatusIsStopped() {
        composeTestRule
            .onNodeWithText("OFF")
            .assertIsDisplayed()
    }
    
    fun verifyExportButtonExists() {
        composeTestRule
            .onNodeWithText("EXPORTAR")
            .assertExists()
    }
}
```

### Using the Robot in Tests

```kotlin
// File: app/src/androidTest/kotlin/com/example/recorderai/cases/MainActivityE2ETests.kt
@Test
fun testServiceStartAndStop() {
    // Start the service
    robot.clickOnStartButton()
    robot.verifyServiceStatusIsRunning()
    
    // Stop the service
    robot.clickOnStopButton()
    robot.verifyServiceStatusIsStopped()
}

@Test
fun testExportFunctionality() {
    robot.verifyExportButtonExists()
}
```

---

## Test Types

### Unit Tests

Unit tests run on the JVM without needing an Android device or emulator. They use **Robolectric** to simulate the Android framework.

#### Key Characteristics

- Run fast (no device needed)
- Use MockK for mocking dependencies
- Use Kotest for assertions
- Can test business logic, utilities, and service methods

#### Example: DataCollectionServiceTest

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataCollectionServiceTest {

    private lateinit var svc: DataCollectionService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        svc = DataCollectionService()
        val context = RuntimeEnvironment.getApplication()
        val attachBaseContext = android.content.ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
        attachBaseContext.isAccessible = true
        attachBaseContext.invoke(svc, context)
    }

    @Nested
    inner class `parseCells` {
        @Test
        fun `should parse CellInfoLte`() {
            val cellIdentity = mockk<CellIdentityLte>()
            every { cellIdentity.ci } returns 42
            every { cellIdentity.tac } returns 7

            val signal = mockk<CellSignalStrengthLte>()
            every { signal.dbm } returns -70

            val cell = mockk<CellInfoLte>()
            every { cell.cellIdentity } returns cellIdentity
            every { cell.cellSignalStrength } returns signal

            val parsed = svc.parseCells(listOf(cell))

            parsed shouldHaveSize 1
            parsed[0].type shouldBe "LTE"
            parsed[0].cid shouldBe 42
        }
    }
}
```

### Compose UI Tests

Compose UI tests verify that the user interface works correctly. They run on an Android device or emulator using `ComposeTestRule`.

#### Configuration

Add these dependencies to your `build.gradle.kts`:

```kotlin
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

#### Example: MainActivityUiTest

```kotlin
@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun pantalla_muestra_boton_iniciar() {
        // Verify that the start button text exists
        composeTestRule.onNodeWithText("INICIAR ESCANEO").assertExists()
    }
}
```

#### Key Compose Test APIs

| Function | Purpose |
|----------|---------|
| `onNodeWithText("text")` | Find node by text |
| `onNodeWithTag("tag")` | Find node by test tag |
| `performClick()` | Perform click action |
| `performTextInput("text")` | Enter text |
| `assertIsDisplayed()` | Assert node is visible |
| `assertExists()` | Assert node exists |

### Integration Tests

Integration tests verify that different components work together. They use **Espresso Intents** to test interactions between app components and system features.

#### Example: AppIntegrationTest

```kotlin
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

        // Start service on main thread
        instrumentation.runOnMainSync { startWardrivingService(ctx) }
        
        // Verify running
        assertTrue(isServiceRunning(ctx))

        // Stop service
        instrumentation.runOnMainSync { stopWardrivingService(ctx) }
    }

    @Test
    fun exportLastSession_triggers_share_intent() {
        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        
        // Setup test data
        val root = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)!!
        val sessions = File(root, "RecorderAI")
        sessions.mkdirs()
        val last = File(sessions, "Session_test_export")
        last.mkdirs()
        File(last, "f.txt").writeText("hello")

        // Intercept intents
        init()
        try {
            androidx.test.core.app.ActivityScenario
                .launch(MainActivity::class.java)
                .use { scenario ->
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
```

---

## Running Tests

### Run All Unit Tests

```bash
./gradlew test
```

### Run Unit Tests for a Specific Variant

```bash
./gradlew testDebugUnitTest
./gradlew testReleaseUnitTest
```

### Run All Instrumented Tests (requires device/emulator)

```bash
./gradlew connectedDebugAndroidTest
```

### Run Specific Instrumented Test Class

```bash
./gradlew connectedDebugAndroidTest --tests "com.example.recorderai.MainActivityUiTest"
```

### Run Tests with Coverage

```bash
./gradlew testDebugUnitTest jacocoTestReport
```

### Using Android Studio

1. Open the project in Android Studio
2. Right-click on the test class or method you want to run
3. Select "Run" or use the keyboard shortcut (Ctrl+Shift+R on Mac, Shift+Alt+R on Windows)

### Gradle Tasks for Testing

| Task | Description |
|------|-------------|
| `test` | Run all unit tests |
| `testDebugUnitTest` | Run debug unit tests |
| `testReleaseUnitTest` | Run release unit tests |
| `connectedDebugAndroidTest` | Run instrumented tests on connected device |
| `connectedReleaseAndroidTest` | Run release instrumented tests |
| `startEmulatorIfNeeded` | Start emulator if no device connected |

---

## Best Practices

### 1. Isolated Tests

Each test should cover a specific scenario. Avoid having one test depend on another.

```kotlin
// ✅ Good: Each test is independent
@Test
fun testStartService() { ... }

@Test
fun testStopService() { ... }

// ❌ Bad: Tests depend on each other
@Test
fun testStartThenStop() { ... }
```

### 2. Separation of Concerns

Use the Robot Pattern to avoid code duplication and centralize UI interaction logic.

```kotlin
// ✅ Good: UI interactions centralized in Robot
class MainActivityRobot { ... }

// ❌ Bad: UI interactions scattered in tests
@Test
fun test() {
    composeTestRule.onNodeWithText("Start").performClick()
}
```

### 3. Modular Structure

Organize tests into specific folders to keep the project clean and scalable:

- `helpers/` - Robot classes and utilities
- `cases/` - E2E test cases
- `screens/` - Screen-specific UI tests

### 4. Test Performance

E2E tests can be slower than unit tests. Be selective about which flows should be covered by E2E tests:

- Use unit tests for business logic
- Use E2E tests for critical user flows
- Consider using Espresso's synchronization features to avoid flaky tests

### 5. Espresso Synchronization

Espresso automatically waits for:
- The message queue to be empty
- No AsyncTask instances running
- All idling resources to be idle

This provides more reliable test results.

### 6. Use Proper Test Tags

Add test tags to Compose elements for easier test selection:

```kotlin
@Composable
fun MyButton() {
    Button(
        onClick = { ... },
        modifier = Modifier.testTag("submit_button")
    ) {
        Text("Submit")
    }
}
```

### 7. Handle Permissions in Tests

For instrumented tests that require permissions, use `GrantPermissionRule`:

```kotlin
@get:Rule
val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.RECORD_AUDIO
)
```

### 8. Clean Up Test Data

Always clean up test files and resources after tests:

```kotlin
@AfterEach
fun cleanup() {
    testDirectory.deleteRecursively()
}
```

---

## Additional Resources

- [AndroidX Test Documentation](https://developer.android.com/training/testing)
- [Espresso Documentation](https://developer.android.com/training/testing/espresso)
- [Jetpack Compose Testing](https://developer.android.com/compose/testing)
- [Robolectric Documentation](http://robolectric.org/)
- [MockK Documentation](https://mockk.io/)

---

## Conclusion

E2E testing with Espresso is an excellent way to ensure that your app works correctly from the user's perspective. Proper project organization, the use of the Robot Pattern, and best practices are crucial for making tests readable, maintainable, and scalable.

This guide provides a foundation for building a robust testing suite for your Android application. Feel free to explore the existing tests in the project and modify them to fit your specific needs.

---

*Generated for RecorderAI Android Application*
