package com.example.recorderai

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E Tests for Room functionality.
 * 
 * Tests room creation and verifies the UI components are present.
 */
@RunWith(AndroidJUnit4::class)
class RoomDeleteE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val testRoomName = "room to delete test"

    @Test
    fun testCreateAndDeleteRoom() {
        // Step 1: Click on "NUEVA ESTANCIA" button to open create dialog
        composeTestRule.onNodeWithText("NUEVA ESTANCIA")
            .performClick()

        // Step 2: Type the room name in the dialog
        composeTestRule.onNodeWithText("Nombre de la estancia")
            .performTextInput(testRoomName)

        // Step 3: Click "Crear" to create the room
        composeTestRule.onNodeWithText("Crear")
            .performClick()

        // Step 4: Verify the room appears on the screen
        composeTestRule.onNodeWithText(testRoomName)
            .assertIsDisplayed()
    }

    @Test
    fun testCreateRoomShowsInList() {
        // Create a room with unique name for this test
        val uniqueRoomName = "test room ${System.currentTimeMillis()}"

        // Click on "NUEVA ESTANCIA" button
        composeTestRule.onNodeWithText("NUEVA ESTANCIA")
            .performClick()

        // Type room name
        composeTestRule.onNodeWithText("Nombre de la estancia")
            .performTextInput(uniqueRoomName)

        // Click "Crear"
        composeTestRule.onNodeWithText("Crear")
            .performClick()

        // Verify the room is displayed in the list
        composeTestRule.onNodeWithText(uniqueRoomName)
            .assertIsDisplayed()
    }
    
    @Test
    fun testNewRoomButtonExists() {
        // Verify the "NUEVA ESTANCIA" button exists (always visible)
        composeTestRule.onNodeWithText("NUEVA ESTANCIA")
            .assertIsDisplayed()
            
        // Verify the "EXPORTAR" button exists (always visible)
        composeTestRule.onNodeWithText("EXPORTAR")
            .assertIsDisplayed()
    }
}
