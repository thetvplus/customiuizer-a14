package tv.withaibuild.customiuizer.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.text.InputType
import android.util.Log
import android.util.Pair
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.github.libxposed.service.RemotePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.GlobalActionToggles
import tv.withaibuild.customiuizer.mods.GlobalActions


object AppHelper {
    const val prefsName = "customiuizer_prefs"
    private const val TAG = "LSPosed-Bridge"

    @JvmField
    var appPrefs: SharedPreferences? = null

    /**
     * True only while the LSPosed service is bound right now.
     *
     * `false` is **not** evidence that the module is inactive: it is also false before the
     * first bind and while a bind is still in flight. To tell the user the module is not
     * active, use [XposedServiceManager.shouldReportInactive] after waiting for a decided
     * state - see [XposedServiceManager.State.isProvisional].
     */
    @JvmField
    var moduleActive = false

    @JvmField
    var remotePrefs: RemotePreferences? = null

    @JvmField
    var silentSync = false

    @JvmField
    var installedAppsList: ArrayList<AppData>? = null

    enum class SettingsType {
        Preference, Edit
    }

    enum class AppAdapterType {
        Default, Standalone, Mutli, CustomTitles, Activities
    }

    enum class ActionBarType {
        HomeUp, Edit
    }

    @JvmStatic
    fun log(line: String) {
        Log.i(TAG, "[Pengeek] $line")
    }

    @JvmStatic
    fun log(t: Throwable) {
        Log.e(TAG, "[Pengeek] " + Log.getStackTraceString(t))
    }

    @JvmStatic
    fun log(mod: String, line: String) {
        Log.i(TAG, "[Pengeek][$mod] $line")
    }

    @JvmStatic
    fun log(mod: String, t: Throwable) {
        Log.e(TAG, "[Pengeek][$mod] " + Log.getStackTraceString(t))
    }

    @JvmStatic
    fun getSharedPrefs(context: Context, protectedStorage: Boolean): SharedPreferences {
        val ctx = if (protectedStorage) getProtectedContext(context) else context
        return ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun getIntOfAppPrefs(key: String?, defValue: Int): Int {
        if (key == null) return defValue
        return appPrefs?.getInt(prefixKey(key), defValue) ?: defValue
    }

    @JvmStatic
    fun getStringOfAppPrefs(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        return appPrefs?.getString(prefixKey(key), defValue) ?: defValue
    }

    @JvmStatic
    fun getStringAsIntOfAppPrefs(key: String?, defValue: Int): Int {
        val prefValue = getStringOfAppPrefs(key, null)
        return if (prefValue == null) defValue else Integer.parseInt(prefValue)
    }

    @JvmStatic
    fun getStringSetOfAppPrefs(key: String?, defValue: Set<String>): MutableSet<String> {
        if (key == null) return defValue.toMutableSet()
        val set = appPrefs?.getStringSet(prefixKey(key), defValue) ?: defValue
        return set.toMutableSet()
    }

    @JvmStatic
    @JvmOverloads
    fun getBooleanOfAppPrefs(key: String?, defValue: Boolean = false): Boolean {
        if (key == null) return defValue
        return appPrefs?.getBoolean(prefixKey(key), defValue) ?: defValue
    }

    @JvmStatic
    @JvmOverloads
    fun getProtectedContext(context: Context, config: Configuration? = null): Context {
        return try {
            val mContext = if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
            if (config == null) mContext else mContext.createConfigurationContext(config)
        } catch (t: Throwable) {
            context
        }
    }

    @JvmStatic
    fun showInputDialog(
        context: Context?,
        key: String?,
        titleRes: Int,
        summRes: Int,
        maxLines: Int,
        callback: Helpers.InputCallback
    ) {
        showInputDialog(context, key, titleRes, summRes, maxLines, callback, true)
    }

    @JvmStatic
    fun showInputDialog(
        context: Context?,
        key: String?,
        titleRes: Int,
        summRes: Int,
        maxLines: Int,
        callback: Helpers.InputCallback,
        prefDefault: Boolean
    ) {
        if (context == null || key == null) return
        val builder = AlertDialog.Builder(context)
        builder.setTitle(titleRes)
        val input = EditText(context)
        input.setText(if (prefDefault) getStringOfAppPrefs(key, "") else key)

        if (maxLines > 1) {
            input.isSingleLine = false
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        }

        val container = LinearLayout(context)
        val horizPadding = context.resources.getDimensionPixelSize(R.dimen.preference_item_child_padding)
        container.setPadding(horizPadding, 0, horizPadding, 0)
        container.orientation = LinearLayout.VERTICAL
        if (summRes > 0) {
            val msg = TextView(context)
            msg.setText(summRes)
            msg.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            container.addView(msg)
        }
        container.addView(input)
        builder.setView(container)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            callback.onInputFinished(key, input.text.toString())
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    @JvmStatic
    fun addStringPair(hayStack: MutableSet<String>?, needle1: String, needle2: String) {
        hayStack?.add("$needle1|$needle2")
    }

    @JvmStatic
    fun removeStringPair(hayStack: MutableSet<String>?, needle: String) {
        hayStack ?: return
        val it = hayStack.iterator()
        while (it.hasNext()) {
            val pair = it.next()
            if (pair.substringBefore("|") == needle) {
                it.remove()
                return
            }
        }
    }

    @JvmStatic
    fun getActionNameLocal(context: Context, key: String): Pair<String, String>? {
        return try {
            val action = getIntOfAppPrefs(key + "_action", 1)
            val modRes = context.resources
            var pair: Pair<String, String>? = null
            val resId = GlobalActions.getActionResId(action)
            if (resId != 0) {
                pair = Pair(modRes.getString(resId), "")
            } else if (action == 8) {
                pair = Pair(
                    modRes.getString(R.string.array_global_actions_launch),
                    Helpers.getAppName(context, getStringOfAppPrefs(key + "_app", "") ?: "", true)?.toString() ?: ""
                )
            } else if (action == 9) {
                pair = Pair(
                    modRes.getString(R.string.array_global_actions_shortcut),
                    getStringOfAppPrefs(key + "_shortcut_name", "") ?: ""
                )
            } else if (action == 10) {
                val what = getIntOfAppPrefs(key + "_toggle", 0)
                val toggle = modRes.getString(R.string.array_global_actions_toggle)
                val labelRes = GlobalActionToggles.labelResId(what)
                pair = if (labelRes != null) Pair(toggle, modRes.getString(labelRes)) else null
            } else if (action == 20) {
                val pref = getStringOfAppPrefs(key + "_activity", "") ?: ""
                var name = Helpers.getAppName(context, pref)?.toString()
                if (name.isNullOrEmpty()) name = Helpers.getAppName(context, pref, true)?.toString()
                pair = Pair(modRes.getString(R.string.array_global_actions_activity), name ?: "")
            }
            pair
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun syncPrefsToAnother(
        entries: Map<String, Any?>?,
        prefs: SharedPreferences,
        clearType: Int,
        ignoreKeys: Set<String>?,
        commitAction: Boolean
    ) {
        if (entries.isNullOrEmpty()) return
        val prefEdit = prefs.edit()
        when (clearType) {
            1 -> prefEdit.clear()
            2 -> {
                for (k in prefs.all.keys) {
                    if (!entries.containsKey(k)) {
                        prefEdit.remove(k)
                    }
                }
            }
        }
        for ((key, value) in entries) {
            if (ignoreKeys != null && ignoreKeys.contains(key)) continue
            if (value == null) continue
            when (value) {
                is Boolean -> prefEdit.putBoolean(key, value)
                is Float -> prefEdit.putFloat(key, value)
                is Int -> prefEdit.putInt(key, value)
                is Long -> prefEdit.putLong(key, value)
                is String -> prefEdit.putString(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") prefEdit.putStringSet(key, value as Set<String>)
            }
        }
        if (commitAction) {
            prefEdit.commit()
        } else {
            prefEdit.apply()
        }
    }

    private fun prefixKey(key: String): String {
        return if (key.startsWith("pref_key_")) key else "pref_key_$key"
    }

    /**
     * Executes a shell command as root. Returns the exit code and combined stdout/stderr.
     * Caller is responsible for running this off the main thread.
     */
    @JvmStatic
    fun executeRootCommand(command: String): Pair<Int, String> {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = StringBuilder()
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.forEachLine { output.append(it).append("\n") }
            }
            process.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.forEachLine { output.append(it).append("\n") }
            }
            val exitCode = process.waitFor()
            Pair(exitCode, output.toString().trim())
        } catch (t: Throwable) {
            Log.e(TAG, "executeRootCommand failed: ${t.message}")
            Pair(-1, t.message ?: "unknown error")
        } finally {
            try { process?.destroy() } catch (_: Throwable) {}
        }
    }

    /**
     * Suspend wrapper for executeRootCommand that switches to IO dispatcher.
     */
    @JvmStatic
    suspend fun executeRootCommandAsync(command: String): Pair<Int, String> =
        withContext(Dispatchers.IO) { executeRootCommand(command) }
}
