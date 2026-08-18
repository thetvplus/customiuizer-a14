package tv.withaibuild.customiuizer.subs

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.prefs.PreferenceEx
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.PreferenceResourceResolver

class CategorySelector : SubFragment() {

    private var cat: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        cat = arguments?.getString("cat")
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val screen = findPreference<PreferenceScreen>("pref_key_cat")
        val cnt = screen?.preferenceCount ?: 0
        for (i in 0 until cnt) {
            screen?.getPreference(i)?.setOnPreferenceClickListener { preference ->
                if (preference !is PreferenceEx) return@setOnPreferenceClickListener false
                val bundle = Bundle().apply { putString("sub", preference.key) }
                val category = cat ?: return@setOnPreferenceClickListener false
                val resource = PreferenceResourceResolver.resolve(category, preference.key)
                when (cat) {
                    "pref_key_system" -> openSubFragment(System(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.system_mods, resource)
                    "pref_key_launcher" -> openSubFragment(Launcher(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.launcher_title, resource)
                    "pref_key_controls" -> openSubFragment(Controls(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.controls_mods, resource)
                    "pref_key_various" -> openSubFragment(Various(), bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp, R.string.various_mods, resource)
                }
                true
            }
        }
    }
}
