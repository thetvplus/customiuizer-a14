package tv.withaibuild.customiuizer

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.ui.SettingsInputForm
import tv.withaibuild.customiuizer.ui.SettingsTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreferenceSmallWindowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun smallHeightKeepsNumericInputAndCancelReachableWithoutCommitting() {
        val app = RuntimeEnvironment.getApplication()
        var draft by mutableStateOf("400")
        var committed = "400"
        var open by mutableStateOf(true)
        // Test the production form under the same small available height. Native
        // dialog / IME insets are checked separately on the Android 14 device.
        compose.setContent { SettingsTheme {
            Box(Modifier.requiredSize(320.dp, 120.dp)) {
                if (open) SettingsInputForm("Numeric editor", { open = false },
                    confirmButton = { TextButton(onClick = { committed = draft }) { Text("Apply") } },
                    text = { OutlinedTextField(draft, { draft = it }, Modifier.testTag("setting.input")) })
            }
        } }
        compose.onNodeWithTag("setting.input").performScrollTo().assertIsDisplayed().performTextReplacement("25")
        compose.onNodeWithText(app.getString(android.R.string.cancel)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertFalse(open)
            assertEquals("25", draft)
            assertEquals("400", committed)
        }
    }
}
