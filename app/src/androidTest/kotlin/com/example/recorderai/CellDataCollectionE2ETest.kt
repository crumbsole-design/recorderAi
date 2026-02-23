package com.example.recorderai

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E Tests for Cell Data Collection functionality.
 * 
 * Tests:
 * - Create a room
 * - Navigate to room grid
 * - Configure cell with name and linkable status
 * - Verify cell shows configured status
 * - Start data collection (if permissions available)
 * - Verify data counters are displayed
 */
@RunWith(AndroidJUnit4::class)
class CellDataCollectionE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val testRoomName = "Test Room for Collection ${System.currentTimeMillis()}"

    /**
     * Setup executed before each test to ensure the Activity is ready
     * and the screen stays on during test execution.
     */
    @Before
    fun setUp() {
        // Keep screen on during tests to prevent device sleep
        @Suppress("DEPRECATION")
        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }

        // Wait for Activity to be fully initialized
        composeTestRule.waitForIdle()

        // Wait for compose hierarchy to be available with timeout
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onRoot().fetchSemanticsNode()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    @Test
    fun testCreateRoomAndVerifyGrid() {
        // Wait for UI to be ready
        waitForUiReady()

        // Step 1: Create a room
        composeTestRule.onNodeWithText("NUEVA ESTANCIA")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Nombre de la estancia")
            .performTextInput(testRoomName)

        composeTestRule.onNodeWithText("Crear")
            .performClick()

        // Step 2: Verify room appears with timeout
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(testRoomName)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(testRoomName)
            .assertIsDisplayed()

        // Step 3: Click on the room to navigate to grid
        composeTestRule.onNodeWithText(testRoomName)
            .performClick()

        // Wait for grid to load with explicit timeout
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Step 4: Verify grid cells are displayed (15 cells for 3x5 grid)
        // We should see at least some cells
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("0")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onAllNodes(hasText("0"))
            .onFirst()
            .assertExists()
    }

    @Test
    fun testCellShowsUnconfiguredStatus() {
        // Create room and navigate to grid
        createTestRoomAndNavigateToGrid()

        // Click on a cell (cell 0)
        composeTestRule.onNodeWithText("0")
            .performClick()

        // Wait for detail screen with timeout
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Verify cell shows "Sin configurar" status
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("? Sin configurar")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("? Sin configurar")
            .assertIsDisplayed()
    }

    @Test
    fun testCellConfigurationDialog() {
        // Create room and navigate to grid
        createTestRoomAndNavigateToGrid()

        // Click on a cell to open detail
        composeTestRule.onNodeWithText("0")
            .performClick()

        // Wait for detail screen
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Click on the unconfigured cell card to open config dialog
        composeTestRule.onNodeWithText("? Sin configurar")
            .performClick()

        // Wait for dialog
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Verify dialog has expected elements
        composeTestRule.onNodeWithText("Configurar Celda 0")
            .assertIsDisplayed()

        // Verify dialog has expected elements
        composeTestRule.onNodeWithTag("linkableCheckbox")
            .assertExists()

        composeTestRule.onNodeWithText("Nombre descriptivo (opcional)")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Guardar")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancelar")
            .assertIsDisplayed()
    }

    @Test
    fun testConfigureCellWithLinkableAndName() {
        // Create room and navigate to grid
        createTestRoomAndNavigateToGrid()

        // Click on a cell
        composeTestRule.onNodeWithText("0")
            .performClick()

        // Wait for detail screen
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Open config dialog
        composeTestRule.onNodeWithText("? Sin configurar")
            .performClick()

        // Wait for dialog
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Check the linkable checkbox using testTag
        composeTestRule.onNodeWithTag("linkableCheckbox")
            .performClick()

        // Enter a descriptive name
        composeTestRule.onNodeWithText("Nombre descriptivo (opcional)")
            .performTextInput("Puerta principal")

        // Click save
        composeTestRule.onNodeWithText("Guardar")
            .performClick()

        // Wait for dialog to close
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Additional wait for navigation and UI update
        Thread.sleep(1000)

        // Verify cell now shows as "Abierta" (linkable = true)
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("🔓 Abierta")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("🔓 Abierta")
            .assertIsDisplayed()

        // Verify display name is shown
        composeTestRule.onNodeWithText("Nombre: Puerta principal")
            .assertIsDisplayed()
    }

    @Test
    fun testConfigureCellAsClosed() {
        // Create room and navigate to grid
        createTestRoomAndNavigateToGrid()

        // Click on a cell
        composeTestRule.onNodeWithText("1")
            .performClick()

        // Wait for detail screen
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Open config dialog
        composeTestRule.onNodeWithText("? Sin configurar")
            .performClick()

        // Wait for dialog
        composeTestRule.waitForIdle()
        waitForUiReady()

        // DO NOT check the linkable checkbox (leave it unchecked = closed)

        // Enter a descriptive name
        composeTestRule.onNodeWithText("Nombre descriptivo (opcional)")
            .performTextInput("Ventana")

        // Click save
        composeTestRule.onNodeWithText("Guardar")
            .performClick()

        // Wait for dialog to close
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Additional wait for navigation and UI update
        Thread.sleep(1000)

        // Verify cell now shows as "Cerrada" (linkable = false)
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("🔒 Cerrada")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("🔒 Cerrada")
            .assertIsDisplayed()

        // Verify display name is shown
        composeTestRule.onNodeWithText("Nombre: Ventana")
            .assertIsDisplayed()
    }

    @Test
    fun testDataCountersDisplayWhenNoData() {
        // Create room and navigate to grid
        createTestRoomAndNavigateToGrid()

        // Configure and click on a cell
        configureCellAsLinkable(0, "Test Cell")

        // After configuration, verify data summary section is visible
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Resumen de Datos")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Resumen de Datos")
            .assertIsDisplayed()

        // Verify "Sin datos registrados" message appears
        composeTestRule.onNodeWithText("Sin datos registrados")
            .assertIsDisplayed()

        // Verify the "Iniciar recolección" button is visible
        composeTestRule.onNodeWithText("▶ INICIAR RECOLECCIÓN")
            .assertIsDisplayed()
    }

    @Test
    fun testStartCollectionButtonExists() {
        // Create room and navigate to grid
        createTestRoomAndNavigateToGrid()

        // Configure and click on a cell
        configureCellAsLinkable(0, "Test Cell")

        // Verify the start collection button is present
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("▶ INICIAR RECOLECCIÓN")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("▶ INICIAR RECOLECCIÓN")
            .assertIsDisplayed()
    }

    // Helper function to wait for UI to be ready
    private fun waitForUiReady() {
        composeTestRule.waitForIdle()
        // Small additional wait to ensure UI is fully rendered
        Thread.sleep(200)
    }

    // Helper function to create a test room and navigate to grid
    private fun createTestRoomAndNavigateToGrid() {
        // Wait for initial UI
        waitForUiReady()

        // Create room
        composeTestRule.onNodeWithText("NUEVA ESTANCIA")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Nombre de la estancia")
            .performTextInput(testRoomName)

        composeTestRule.onNodeWithText("Crear")
            .performClick()

        // Verify room appears with timeout
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(testRoomName)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(testRoomName)
            .assertIsDisplayed()

        // Navigate to grid
        composeTestRule.onNodeWithText(testRoomName)
            .performClick()

        // Wait for grid with explicit timeout
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Ensure at least one cell is visible
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("0")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Helper function to configure a cell as linkable with a name
    private fun configureCellAsLinkable(cellId: Int, displayName: String) {
        // Click on the cell
        composeTestRule.onNodeWithText(cellId.toString())
            .performClick()

        // Wait for detail screen
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Open config dialog
        composeTestRule.onNodeWithText("? Sin configurar")
            .performClick()

        // Wait for dialog
        composeTestRule.waitForIdle()
        waitForUiReady()

        // Check the linkable checkbox using testTag
        composeTestRule.onNodeWithTag("linkableCheckbox")
            .performClick()

        // Enter the display name
        composeTestRule.onNodeWithText("Nombre descriptivo (opcional)")
            .performTextInput(displayName)

        // Click save
        composeTestRule.onNodeWithText("Guardar")
            .performClick()

        // Wait for dialog to close
        composeTestRule.waitForIdle()
        waitForUiReady()
    }
}
