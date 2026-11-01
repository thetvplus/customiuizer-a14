package tv.withaibuild.customiuizer.subs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.ui.settingsView
import tv.withaibuild.customiuizer.ui.SettingsActionRow
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers
import java.io.File
import java.io.IOException
import android.widget.Toast
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.utils.AppIconLoader

/** An explicit draft: selecting a target never persists until the toolbar Done action. */
class MultiAction : SubFragment() {
    enum class Actions { NAVBAR, LAUNCHER, CONTROLS, LOCKSCREEN, LAUNCH, STATUSBAR, MUSIC }
    private var key = ""
    private var action by mutableIntStateOf(1)
    private var toggle by mutableIntStateOf(1)
    private var appValue by mutableStateOf<String?>(null)
    private var appUser = 0
    private var activityValue by mutableStateOf<String?>(null)
    private var activityUser = 0
    private var shortcutValue by mutableStateOf<String?>(null)
    private var shortcutName by mutableStateOf<String?>(null)
    private var shortcutIcon: String? = null
    private var shortcutIntent: Intent? = null
    private var entries: List<String> = emptyList()
    private var entryValues = intArrayOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        key = arguments?.getString("key").orEmpty()
        val actions = Actions.entries.getOrElse(arguments?.getInt("actions") ?: 0) { Actions.NAVBAR }
        val (entriesResId, entryValuesResId) = when (actions) {
            Actions.NAVBAR -> R.array.global_actions_navbar to R.array.global_actions_navbar_val
            Actions.LAUNCHER -> R.array.global_actions_launcher to R.array.global_actions_launcher_val
            Actions.CONTROLS -> R.array.global_actions_controls to R.array.global_actions_controls_val
            Actions.STATUSBAR -> R.array.global_actions_statusbar to R.array.global_actions_statusbar_val
            Actions.LOCKSCREEN -> R.array.global_lockscreen_actions to R.array.global_lockscreen_actions_val
            Actions.LAUNCH -> R.array.global_launch_actions to R.array.global_launch_actions_val
            Actions.MUSIC -> R.array.global_actions_island_music to R.array.global_actions_island_music_val
        }

        entries = resources.getStringArray(entriesResId).toList()
        entryValues = resources.getIntArray(entryValuesResId)
        val defaultAction = if (actions == Actions.MUSIC)
            tv.withaibuild.customiuizer.mods.island.IslandIdleActions.defaultForKey(key) else 1
        action = savedInstanceState?.getInt("draft.action") ?: AppHelper.getIntOfAppPrefs(key + "_action", defaultAction).coerceAtLeast(1)
        toggle = savedInstanceState?.getInt("draft.toggle") ?: AppHelper.getIntOfAppPrefs(key + "_toggle", 1)
        if (action !in entryValues) action = entryValues.first()
        if (toggle !in resources.getIntArray(R.array.global_toggles_val)) toggle = resources.getIntArray(R.array.global_toggles_val).first()
        appValue = savedInstanceState?.getString("draft.app") ?: AppHelper.getStringOfAppPrefs(key + "_app", null)
        appUser = savedInstanceState?.getInt("draft.appUser") ?: AppHelper.getIntOfAppPrefs(key + "_app_user", 0)
        activityValue = savedInstanceState?.getString("draft.activity") ?: AppHelper.getStringOfAppPrefs(key + "_activity", null)
        activityUser = savedInstanceState?.getInt("draft.activityUser") ?: AppHelper.getIntOfAppPrefs(key + "_activity_user", 0)
        shortcutValue = savedInstanceState?.getString("draft.shortcut") ?: AppHelper.getStringOfAppPrefs(key + "_shortcut", null)
        shortcutName = savedInstanceState?.getString("draft.shortcutName") ?: AppHelper.getStringOfAppPrefs(key + "_shortcut_name", null)
        shortcutIcon = savedInstanceState?.getString("draft.shortcutIcon")
        shortcutIntent = savedInstanceState?.getParcelable("draft.shortcutIntent", Intent::class.java)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("draft.action", action); outState.putInt("draft.toggle", toggle)
        outState.putString("draft.app", appValue); outState.putInt("draft.appUser", appUser)
        outState.putString("draft.activity", activityValue); outState.putInt("draft.activityUser", activityUser)
        outState.putString("draft.shortcut", shortcutValue); outState.putString("draft.shortcutName", shortcutName)
        outState.putString("draft.shortcutIcon", shortcutIcon); outState.putParcelable("draft.shortcutIntent", shortcutIntent)
        super.onSaveInstanceState(outState)
    }

    override fun loadSharedPrefs() = Unit

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = settingsView {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            ActionChoice(getString(R.string.launcher_gestures_action), entries, entryValues, action) { action = it }
            when (action) {
                8 -> TargetRow(getString(R.string.launcher_gestures_launch), appValue, appUser) {
                    AppSelector().also { it.setTargetFragment(this@MultiAction, 0) }.let {
                        openSubFragment(it, null, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_app, R.id.prefs_app_selector)
                    }
                }
                9 -> SettingsActionRow(getString(R.string.launcher_gestures_shortcut), shortcutName ?: targetLabel(shortcutValue)) {
                    val args = Bundle().apply { putString("key", key + "_shortcut") }
                    ShortcutSelector().also { it.setTargetFragment(this@MultiAction, 1) }.let {
                        openSubFragment(it, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_shortcut, R.id.prefs_app_selector)
                    }
                }
                10 -> ActionChoice(getString(R.string.launcher_gestures_setting), resources.getStringArray(R.array.global_toggles).toList(),
                    resources.getIntArray(R.array.global_toggles_val), toggle) { toggle = it }
                20 -> TargetRow(getString(R.string.launcher_gestures_activity), activityValue, activityUser) {
                    val args = Bundle().apply { putBoolean("activity", true) }
                    AppSelector().also { it.setTargetFragment(this@MultiAction, 2) }.let {
                        openSubFragment(it, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_app, R.id.prefs_app_selector)
                    }
                }
            }
            if (action == 33 || action == 34) Text(getString(R.string.pip_action_hint), Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun targetLabel(target: String?): String {
        val context = requireContext().applicationContext
        val fallback = getString(R.string.notselected)
        val label by produceState(fallback, context, target) {
            value = withContext(AppIconLoader.dispatcher) {
                target?.takeIf { it.isNotEmpty() }?.let { Helpers.getAppName(context, it, true)?.toString() } ?: fallback
            }
        }
        return label
    }

    @Composable
    private fun TargetRow(title: String, value: String?, user: Int, select: () -> Unit) {
        SettingsActionRow(title, targetLabel(value) + if (user != 0) " · " + getString(R.string.a14_secondary_user) else "", onClick = select)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) when (requestCode) {
            0 -> { appValue = data.getStringExtra("app"); appUser = data.getIntExtra("user", 0) }
            1 -> {
                shortcutValue = data.getStringExtra("shortcut_contents"); shortcutName = data.getStringExtra("shortcut_name")
                shortcutIcon = data.getStringExtra("shortcut_icon"); shortcutIntent = data.getParcelableExtra("shortcut_intent", Intent::class.java)
            }
            2 -> { activityValue = data.getStringExtra("activity"); activityUser = data.getIntExtra("user", 0) }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun saveSharedPrefs() {
        val editor = AppHelper.appPrefs?.edit() ?: return
        editor.putInt(key + "_action", action).putInt(key + "_toggle", toggle)
            .putString(key + "_app", appValue).putInt(key + "_app_user", appUser)
            .putString(key + "_activity", activityValue).putInt(key + "_activity_user", activityUser)
            .putString(key + "_shortcut", shortcutValue)
        shortcutIntent?.let { editor.putString(key + "_shortcut_intent", it.toUri(0)) }
        shortcutName?.let { editor.putString(key + "_shortcut_name", it) }
        val temp = shortcutIcon?.let { File(it) }
        if (temp?.isFile == true) {
            val target = File(requireContext().filesDir, "shortcuts/${key}_shortcut.png")
            val directory = File(requireContext().filesDir, "shortcuts").canonicalFile
            if (temp.canonicalFile != File(directory, "tmp.png") || target.canonicalFile.parentFile != directory ||
                !temp.renameTo(target)) throw IOException("Unable to save shortcut icon")
            shortcutIcon = null
        }
        editor.apply()
    }
    override fun confirmEdit() {
        try { saveSharedPrefs(); finish() }
        catch (_: IOException) { Toast.makeText(context, R.string.storage_unavailable, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        if (activity?.isChangingConfigurations != true) context?.let { context ->
            val pending = shortcutIcon?.let(::File)
            val owned = File(context.filesDir, "shortcuts/tmp.png")
            if (pending?.absoluteFile == owned.absoluteFile) pending.delete()
        }
        super.onDestroy()
    }

}

@Composable
private fun ActionChoice(title: String, labels: List<String>, values: IntArray, value: Int, change: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsActionRow(title, value = labels.getOrNull(values.indexOf(value)).orEmpty(), onClick = { open = true })
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            labels.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(values.getOrNull(index) == value, { values.getOrNull(index)?.let(change); open = false })
                    TextButton(onClick = { values.getOrNull(index)?.let(change); open = false }) { Text(label) }
                }
            }
        } }, confirmButton = { TextButton(onClick = { open = false }) { Text(androidx.compose.ui.res.stringResource(android.R.string.cancel)) } })
}
