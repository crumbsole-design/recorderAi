package com.example.recorderai

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Ignore("Flaky on some emulator images: InputManager reflection issue")
    @Test
    fun pantalla_muestra_boton_iniciar() {
        // Verificamos que el texto del botón de inicio exista
        composeTestRule.onNodeWithText("INICIAR ESCANEO").assertExists()
    }
}
