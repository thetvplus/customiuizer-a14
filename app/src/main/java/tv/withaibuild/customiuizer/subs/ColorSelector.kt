package tv.withaibuild.customiuizer.subs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.ui.SettingsSection
import tv.withaibuild.customiuizer.ui.settingsView
import tv.withaibuild.customiuizer.utils.AppHelper
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
class ColorSelector : SubFragment() {
    private var key: String? = null
    private var hue by mutableFloatStateOf(0f)
    private var saturation by mutableFloatStateOf(0f)
    private var brightness by mutableFloatStateOf(1f)
    private var opacity by mutableIntStateOf(255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        key = arguments?.getString("key")
        val saved = savedInstanceState?.getFloatArray("color.hsv")
        if (saved != null && saved.size == 3) {
            hue = saved[0]; saturation = saved[1]; brightness = saved[2]
            opacity = savedInstanceState.getInt("color.alpha", 255)
        } else setColor(AppHelper.getIntOfAppPrefs(key, Color.WHITE))
    }

    private fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; opacity = Color.alpha(color)
    }
    private fun color() = Color.HSVToColor(opacity, floatArrayOf(hue, saturation, brightness))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = settingsView {
        var hexOpen by rememberSaveable { mutableStateOf(false) }
        val selected = color()
        val formatted = String.format(Locale.US, "#%08X", selected)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(88.dp).background(Brush.linearGradient(
                listOf(ComposeColor.White, ComposeColor.Black)))) {
                Box(Modifier.fillMaxSize().background(ComposeColor(selected)))
            }
            TextButton(onClick = { hexOpen = true }) { Text(formatted, style = MaterialTheme.typography.titleMedium) }
            SettingsSection(stringResource(R.string.settings_color_hue) + " · " + hue.roundToInt() + "°")
            Slider(hue, { hue = it }, valueRange = 0f..360f)
            SettingsSection(stringResource(R.string.settings_color_saturation) + " · " + (saturation * 100).roundToInt() + "%")
            Slider(saturation, { saturation = it })
            SettingsSection(stringResource(R.string.settings_color_brightness) + " · " + (brightness * 100).roundToInt() + "%")
            Slider(brightness, { brightness = it })
            SettingsSection(stringResource(R.string.settings_color_opacity) + " · " + (opacity * 100 / 255) + "%")
            Slider(opacity.toFloat(), { opacity = it.roundToInt() }, valueRange = 0f..255f)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { setColor(Color.WHITE) }) { Text(stringResource(R.string.white_color)) }
                OutlinedButton(onClick = { setColor(Color.BLACK) }) { Text(stringResource(R.string.black_color)) }
                if (key?.contains("pref_key_system_batteryindicator") == true)
                    OutlinedButton(onClick = { setColor(Color.TRANSPARENT) }) { Text(stringResource(R.string.auto_color)) }
            }
        }
        if (hexOpen) {
            var input by rememberSaveable { mutableStateOf(formatted) }
            var invalid by remember { mutableStateOf(false) }
            AlertDialog(onDismissRequest = { hexOpen = false }, modifier = Modifier.imePadding().safeDrawingPadding().padding(vertical = 12.dp),
                properties = DialogProperties(decorFitsSystemWindows = false),
                title = { Text(settingTitle) }, text = {
                OutlinedTextField(input, { input = it; invalid = false }, singleLine = true,
                    isError = invalid, supportingText = { if (invalid) Text(stringResource(R.string.settings_color_invalid)) })
            }, confirmButton = { TextButton(onClick = {
                try { setColor(Color.parseColor(input.trim())); hexOpen = false }
                catch (_: IllegalArgumentException) { invalid = true }
            }) { Text(stringResource(android.R.string.ok)) } }, dismissButton = {
                TextButton(onClick = { hexOpen = false }) { Text(stringResource(android.R.string.cancel)) }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putFloatArray("color.hsv", floatArrayOf(hue, saturation, brightness))
        outState.putInt("color.alpha", opacity)
        super.onSaveInstanceState(outState)
    }
    override fun saveSharedPrefs() { key?.let { AppHelper.appPrefs?.edit()?.putInt(it, color())?.apply() } }
    override fun loadSharedPrefs() {}
}
