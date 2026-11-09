package tv.withaibuild.customiuizer.subs

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceGroup
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

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val screen = findPreference<PreferenceScreen>("pref_key_cat")
        if (screen != null) bindDirectory(screen)
    }

    private fun bindDirectory(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val entry = group.getPreference(i)
            if (entry is PreferenceGroup) {
                bindDirectory(entry)
                continue
            }
            entry.setOnPreferenceClickListener { preference ->
                if (preference !is PreferenceEx) return@setOnPreferenceClickListener false
                val category = cat ?: return@setOnPreferenceClickListener false
                val bundle = Bundle().apply {
                    putString("sub", preference.key)
                    putString("cat", category)
                    putBoolean("isStandalone", PreferenceResourceResolver.isStandalonePage(preference.key))
                }
                val resource = PreferenceResourceResolver.resolve(category, preference.key)
                val fragment = PreferenceResourceResolver.createFragment(category, preference.key) ?: return@setOnPreferenceClickListener false
                openSubFragment(fragment, bundle, AppHelper.SettingsType.Preference, AppHelper.ActionBarType.HomeUp,
                    preference.title?.toString().orEmpty(), resource)
                true
            }
        }
    }
}
