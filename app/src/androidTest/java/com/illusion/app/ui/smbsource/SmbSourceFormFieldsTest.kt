package com.illusion.app.ui.smbsource

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmbSourceFormFieldsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: SmbSourceFormState,
        onHostChange: (String) -> Unit = {},
        onSave: () -> Unit = {},
        onTestConnection: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                SmbSourceFormFields(
                    state = state,
                    onDisplayNameChange = {},
                    onHostChange = onHostChange,
                    onShareChange = {},
                    onRootPathChange = {},
                    onDomainChange = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onTestConnection = onTestConnection,
                    onSave = onSave,
                    saveLabel = "Сохранить"
                )
            }
        }
    }

    @Test
    fun typingIntoHostFieldInvokesCallback() {
        var lastValue: String? = null
        setContent(state = SmbSourceFormState(), onHostChange = { lastValue = it })

        composeRule.onNodeWithText("Адрес сервера", substring = true).performTextInput("192.168.1.10")

        assertEquals("192.168.1.10", lastValue)
    }

    @Test
    fun saveButtonIsDisabledWhenHostAndShareAreBlank() {
        setContent(state = SmbSourceFormState(host = "", share = ""))

        composeRule.onNodeWithText("Сохранить").assertIsNotEnabled()
    }

    @Test
    fun saveButtonIsEnabledOnceHostAndShareAreFilled() {
        setContent(state = SmbSourceFormState(host = "192.168.1.10", share = "Movies"))

        composeRule.onNodeWithText("Сохранить").assertIsEnabled()
    }

    @Test
    fun clickingSaveInvokesCallback() {
        var saved = false
        setContent(
            state = SmbSourceFormState(host = "192.168.1.10", share = "Movies"),
            onSave = { saved = true }
        )

        composeRule.onNodeWithText("Сохранить").performScrollTo().performClick()

        assertTrue(saved)
    }

    @Test
    fun testConnectionSuccessStateShowsSuccessMessage() {
        setContent(state = SmbSourceFormState(testState = TestConnectionState.Success))

        composeRule.onNodeWithText("Соединение установлено").assertExists()
    }

    @Test
    fun testConnectionFailureStateShowsItsMessage() {
        setContent(
            state = SmbSourceFormState(testState = TestConnectionState.Failure("Неверный логин или пароль"))
        )

        composeRule.onNodeWithText("Неверный логин или пароль").assertExists()
    }

    @Test
    fun clickingTestConnectionInvokesCallback() {
        var tested = false
        setContent(state = SmbSourceFormState(), onTestConnection = { tested = true })

        composeRule.onNodeWithText("Проверить соединение").performScrollTo().performClick()

        assertTrue(tested)
    }
}
