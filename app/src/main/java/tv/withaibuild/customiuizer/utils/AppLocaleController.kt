package tv.withaibuild.customiuizer.utils

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.LocaleList
import android.os.Process
import android.util.Log
import androidx.core.os.LocaleListCompat
import java.util.Locale
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/**
 * Single owner for the application locale setting.
 *
 * The only persisted user choice is the value of [LOCALE_PREF_KEY]. Everything else
 * (the framework's per-application locale, [Locale.getDefault()], [APPLIED_LOCALE_PREF_KEY])
 * is a derived, re-creatable state computed from that single source and the current
 * system locale.
 *
 * Locale changes are deferred: the user confirms once, the choice is persisted
 * synchronously, the settings application exits, and the new language takes effect
 * the next time the application is opened.
 */
object AppLocaleController {

    const val LOCALE_PREF_KEY = "pref_key_miuizer_locale"

    /**
     * The last tag this application actually pushed into the framework.
     *
     * This exists so that the overwhelmingly common case - the user never touched the
     * language setting - costs nothing on start-up. Without it, [apply] cannot tell
     * "auto, and we have never set anything" from "auto, but we previously set an explicit
     * locale that must now be cleared", so it has to ask `LocaleManager` what the current
     * application locales are on every single start just to find out there is nothing to do.
     *
     * Absent means the framework locale was never set by us, so `auto` needs no work.
     */
    const val APPLIED_LOCALE_PREF_KEY = "pref_key_miuizer_locale_applied"

    private const val AUTO = "auto"

    /**
     * Marker value that names no locale: present, so the fast path is skipped, but not a
     * tag, so nothing reads it as "this is what we applied". See [invalidateFastPath].
     */
    private const val RECONCILE_MARKER = ""

    private const val LEGACY_AUTO = "1"
    private const val TAG = "AppLocaleController"

    // Display order: auto, then the supported explicit locales.
    private val SUPPORTED_LOCALE_TAGS = listOf(
        "auto", "en", "zh-CN", "zh-TW", "ru-RU", "ja-JP", "vi-VN", "cs-CZ", "pt-BR", "tr-TR", "es-ES"
    )

    /**
     * Normalize a raw user selection or a persisted value.
     *
     * - `null`, empty, or unknown values -> `auto`
     * - legacy `"1"` -> `auto`
     * - any supported tag as-is
     */
    @JvmStatic
    fun normalizeLocaleTag(tag: String?): String = when {
        tag == null || tag.isBlank() -> AUTO
        tag == LEGACY_AUTO -> AUTO
        SUPPORTED_LOCALE_TAGS.contains(tag) -> tag
        tag.equals(AUTO, ignoreCase = true) -> AUTO
        else -> AUTO
    }

    /** Read the normalized user selection from the shared preferences. */
    @JvmStatic
    fun getUserLocale(prefs: SharedPreferences?): String =
        normalizeLocaleTag(prefs?.getString(LOCALE_PREF_KEY, "auto"))

    /**
     * Persist a new user selection synchronously.
     *
     * The only persisted source of truth is [LOCALE_PREF_KEY]. The application exits
     * after a successful commit and the next start applies the new locale.
     *
     * If the commit fails, the previous UI-visible value is restored before returning
     * false. Android SharedPreferences updates the in-memory map before reporting the
     * disk result, so this rollback is required to keep the UI from showing a change
     * that did not actually survive.
     */
    @JvmStatic
    fun setUserLocale(prefs: SharedPreferences, tag: String): Boolean {
        val normalized = normalizeLocaleTag(tag)
        val hadPrevious = prefs.contains(LOCALE_PREF_KEY)
        val previousValue = if (hadPrevious) prefs.getString(LOCALE_PREF_KEY, null) else null

        val written = prefs.edit()
            .putString(LOCALE_PREF_KEY, normalized)
            .commit()

        if (!written) {
            Log.e(TAG, "setUserLocale commit failed for tag: $normalized; rolling back UI-visible state")
            val rollback = prefs.edit()
            if (hadPrevious && previousValue != null) {
                rollback.putString(LOCALE_PREF_KEY, previousValue)
            } else {
                rollback.remove(LOCALE_PREF_KEY)
            }
            if (!rollback.commit()) {
                Log.e(TAG, "setUserLocale rollback commit also failed")
            }
            return false
        }
        return true
    }

    /**
     * Read the normalized user locale for the About UI and repair an invalid persisted value.
     *
     * This is a cold-path helper: it only runs when the user opens the About screen, not on
     * every application start, so the one-off [apply] write for invalid legacy values does not
     * affect the fast path. The View layer must not call this during repeated bind/measure
     * passes; call it once per screen creation or resume.
     */
    @JvmStatic
    fun getUserLocaleForUi(prefs: SharedPreferences?): String {
        prefs ?: return AUTO
        val raw = prefs.getString(LOCALE_PREF_KEY, null) ?: return AUTO
        val normalized = normalizeLocaleTag(raw)
        if (raw != normalized) {
            Log.w(TAG, "Invalid persisted locale '$raw'; repairing to '$normalized'")
            prefs.edit().putString(LOCALE_PREF_KEY, normalized).apply()
        }
        return normalized
    }

    /**
     * Return a human-readable summary of the current user locale for the About row.
     */
    @JvmStatic
    fun getUserLocaleSummary(context: Context, prefs: SharedPreferences?): String =
        getUserLocaleSummary(context.getString(R.string.array_system_default), prefs)

    /**
     * Testable overload of [getUserLocaleSummary] that does not require an Android [Context].
     */
    @JvmStatic
    fun getUserLocaleSummary(systemDefaultLabel: String, prefs: SharedPreferences?): String {
        val tag = getUserLocaleForUi(prefs)
        val (displayEntries, entryValues) = try {
            buildLocaleDisplayData(systemDefaultLabel)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            Log.e(TAG, "Cannot build locale display data", t)
            return systemDefaultLabel
        }
        val index = entryValues.indexOf(tag)
        return if (index >= 0) displayEntries[index].toString() else systemDefaultLabel
    }

    /** Gateway for the framework locale service; keeps the production implementation unit-testable. */
    internal interface AppLocaleGateway {
        fun getCurrentLocales(): LocaleListCompat
        fun setLocales(locales: LocaleListCompat): Boolean
    }

    /** Production gateway backed by [Context.getSystemService]. */
    private class ContextAppLocaleGateway(private val context: Context) : AppLocaleGateway {
        override fun getCurrentLocales(): LocaleListCompat = getCurrentApplicationLocales(context)
        override fun setLocales(locales: LocaleListCompat): Boolean = setFrameworkApplicationLocales(context, locales)
    }

    /**
     * Apply the stored user locale if it differs from the current runtime state.
     *
     * Called once during application start-up, so the switched-off case has to be cheap:
     * a user on `auto` who has never had a locale applied returns immediately, without a
     * single call into `LocaleManager` or `Resources.getSystem()`. See
     * [APPLIED_LOCALE_PREF_KEY] for why that shortcut is safe.
     *
     * [context] is required in production: without it there is no way to reach the
     * framework locale service, and the call cannot do anything.
     */
    @JvmStatic
    fun apply(
        prefs: SharedPreferences?,
        context: Context,
    ): Boolean = apply(prefs, ContextAppLocaleGateway(context))

    /**
     * Core reconcile logic, parameterized by a [AppLocaleGateway] so unit tests can avoid
     * an Android framework dependency while still exercising the same state machine.
     */
    internal fun apply(
        prefs: SharedPreferences?,
        gateway: AppLocaleGateway,
    ): Boolean {
        val tag = getUserLocale(prefs)

        // Fast path for a feature that is switched off. `auto` with nothing ever applied
        // by us means the framework locale is not ours to manage and the process default
        // is already the system locale: there is nothing to reconcile, so do not reach for
        // LocaleManager or Resources.getSystem() at all. This is the state every user who
        // never opens the language setting is in, and it runs on every application start.
        if (tag == AUTO && !hasAppliedLocale(prefs)) return false

        val targetLocaleList = toLocaleListCompat(tag)
        val currentLocaleList = gateway.getCurrentLocales()
        val effective = getEffectiveLocale(tag)
        val currentDefault = Locale.getDefault()

        val appLocaleChanged = !areLocaleListsEqual(currentLocaleList, targetLocaleList)
        val defaultChanged = currentDefault != effective

        if (defaultChanged) {
            Locale.setDefault(effective)
        }

        var applied = true
        if (appLocaleChanged) {
            Log.i(TAG, "Applying locale: tag=$tag")
            applied = gateway.setLocales(targetLocaleList)
        }

        // Keep the marker in step with what is now in force. This also runs when nothing
        // changed, which is what migrates installs whose explicit locale was applied
        // before the marker existed: without it their eventual switch back to `auto`
        // would take the fast path and never clear the framework locale.
        if (applied) syncAppliedMarker(prefs, tag)

        return appLocaleChanged
    }

    /**
     * Whether this application currently has a locale of its own pushed into the framework.
     *
     * An absent marker means the framework locale is not ours to manage, so `auto` has
     * nothing to undo.
     */
    private fun hasAppliedLocale(prefs: SharedPreferences?): Boolean =
        prefs?.getString(APPLIED_LOCALE_PREF_KEY, null) != null

    /**
     * Force the next [apply] to take the full reconcile path.
     *
     * Restoring a settings backup replaces the whole preference file with values from
     * another device, whose framework locale has nothing to do with this one's. The fast
     * path would then trust a marker that describes the wrong device - or find no marker
     * at all and leave a locale this app had set still in force - so drop the shortcut
     * once and let the next start work it out from `LocaleManager`.
     */
    @JvmStatic
    fun invalidateFastPath(prefs: SharedPreferences?) {
        prefs?.edit()?.putString(APPLIED_LOCALE_PREF_KEY, RECONCILE_MARKER)?.apply()
    }

    /**
     * Stages the local force-reconcile marker into an existing [SharedPreferences.Editor].
     *
     * This is used during restore so that the reconcile marker is written in the same
     * primary transaction as the restored preference set, rather than in a separate
     * asynchronous [apply]. The staged value is always locally generated and never
     * taken from a source backup.
     */
    @JvmStatic
    fun stageReconcileMarker(editor: SharedPreferences.Editor) {
        editor.putString(APPLIED_LOCALE_PREF_KEY, RECONCILE_MARKER)
    }

    /**
     * Record what is now in force, so the next start can take the fast path.
     *
     * Writes only on an actual change: this runs on every start of an install that uses
     * an explicit language, and there is no reason to touch storage each time.
     */
    private fun syncAppliedMarker(prefs: SharedPreferences?, tag: String) {
        prefs ?: return
        val stored = prefs.getString(APPLIED_LOCALE_PREF_KEY, null)
        if (tag == AUTO) {
            // Back under system control: drop the marker rather than store a value that
            // only means "nothing".
            if (stored != null) prefs.edit().remove(APPLIED_LOCALE_PREF_KEY).apply()
        } else if (stored != tag) {
            prefs.edit().putString(APPLIED_LOCALE_PREF_KEY, tag).apply()
        }
    }

    /**
     * Applies the locale through the framework rather than through AppCompat.
     *
     * [AppCompatDelegate.setApplicationLocales] is a silent no-op from here. On API 33+ it
     * resolves `LocaleManager` by walking the set of **live AppCompat Activity delegates**
     * (`getLocaleManagerForApplication`), and returns without doing anything when that set
     * is empty. This runs from `Application.onCreate`, before any Activity exists, so the
     * set is always empty and the user's language was never applied — the setting appeared
     * to save and then do nothing.
     *
     * `LocaleManager` is API 33 and `minSdk` is 34, so it is always present.
     */
    private fun setFrameworkApplicationLocales(context: Context, locales: LocaleListCompat): Boolean {
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager == null) {
            Log.e(TAG, "LocaleManager unavailable; locale not applied")
            return false
        }
        return try {
            val localeArray = Array(locales.size()) { locales.get(it) }
            manager.applicationLocales = LocaleList(*localeArray)
            true
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            Log.e(TAG, "LocaleManager rejected the locale list", t)
            false
        }
    }

    /**
     * Compute the effective locale from a user tag.
     *
     * `auto` resolves to the current system locale. The callback form lets unit tests
     * inject a deterministic system locale.
     */
    @JvmStatic
    fun getEffectiveLocale(
        tag: String,
        systemLocaleProvider: () -> Locale = { getSystemLocale() }
    ): Locale = when (normalizeLocaleTag(tag)) {
        "auto" -> try {
            systemLocaleProvider()
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            Log.w(TAG, "system locale provider failed: ${t.message}; falling back to JVM default")
            Locale.getDefault()
        }
        else -> try {
            Locale.forLanguageTag(tag)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            try {
                systemLocaleProvider()
            } catch (t2: Throwable) {
                FatalErrors.rethrowIfFatal(t2)
                Locale.getDefault()
            }
        }
    }

    /** Map a user tag to the [LocaleListCompat] that AppCompat expects. */
    @JvmStatic
    fun toLocaleListCompat(tag: String): LocaleListCompat = try {
        when (val normalized = normalizeLocaleTag(tag)) {
            "auto" -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(normalized)
        }
    } catch (t: Throwable) {
        FatalErrors.rethrowIfFatal(t)
        Log.w(TAG, "toLocaleListCompat failed: ${t.message}; returning empty list")
        LocaleListCompat.getEmptyLocaleList()
    }

    /** Current system primary locale. */
    @JvmStatic
    fun getSystemLocale(): Locale = try {
        val sysLocales = Resources.getSystem().configuration.locales
        if (sysLocales.isEmpty) Locale.getDefault() else sysLocales[0]
    } catch (t: Throwable) {
        FatalErrors.rethrowIfFatal(t)
        Log.w(TAG, "getSystemLocale failed: ${t.message}; falling back to JVM default")
        Locale.getDefault()
    }

    /**
     * Build the parallel arrays needed by a [ListPreference] for the language selector.
     *
     * The returned [Pair] is `(displayEntries, entryValues)`.
     */
    @JvmStatic
    fun buildLocaleDisplayData(context: Context): Pair<Array<CharSequence>, Array<String>> =
        buildLocaleDisplayData(context.getString(R.string.array_system_default))

    /**
     * Testable version of the display data builder. The [systemDefaultLabel] is the only
     * Android-dependent value; every other label is derived from [java.util.Locale].
     */
    @JvmStatic
    fun buildLocaleDisplayData(systemDefaultLabel: String): Pair<Array<CharSequence>, Array<String>> {
        val displayNames = ArrayList<CharSequence>(SUPPORTED_LOCALE_TAGS.size)
        val values = ArrayList<String>(SUPPORTED_LOCALE_TAGS.size)
        for (tag in SUPPORTED_LOCALE_TAGS) {
            values.add(tag)
            displayNames.add(when (tag) {
                "auto" -> systemDefaultLabel
                "zh-TW" -> "繁體中文（台灣）"
                else -> buildLanguageDisplayName(tag)
            })
        }
        return Pair(displayNames.toTypedArray(), values.toTypedArray())
    }

    private fun buildLanguageDisplayName(tag: String): String {
        val loc = Locale.forLanguageTag(tag)
        val sb = StringBuilder(loc.getDisplayLanguage(loc))
        if (sb.isNotEmpty()) sb.setCharAt(0, Character.toUpperCase(sb[0]))
        if (tag == "pt-BR") sb.append(" (Brasil)")
        return sb.toString()
    }

    /** Exit the settings application after a successful locale save. */
    @JvmStatic
    fun exitApplicationAfterLocaleSave(activity: Activity) {
        activity.finishAffinity()
        Process.killProcess(Process.myPid())
    }

    /**
     * The locale list currently in force, read from the same place [apply] writes to.
     *
     * Reading through AppCompat has the same blind spot as writing through it: with no live
     * Activity delegate it reports an empty list, so the comparison in [apply] would think
     * nothing is set and re-apply on every start.
     */
    @JvmStatic
    private fun getCurrentApplicationLocales(context: Context): LocaleListCompat = try {
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager == null) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.wrap(manager.applicationLocales)
    } catch (t: Throwable) {
        FatalErrors.rethrowIfFatal(t)
        Log.w(TAG, "getCurrentApplicationLocales failed: ${t.message}; returning empty list")
        LocaleListCompat.getEmptyLocaleList()
    }

    /** Compare two [LocaleListCompat] by their contained locales. */
    @JvmStatic
    private fun areLocaleListsEqual(a: LocaleListCompat, b: LocaleListCompat): Boolean {
        if (a.isEmpty && b.isEmpty) return true
        val size = a.size()
        if (size != b.size()) return false
        for (i in 0 until size) {
            if (a.get(i) != b.get(i)) return false
        }
        return true
    }

}
