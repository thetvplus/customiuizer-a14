package tv.withaibuild.customiuizer.prefs

import android.app.Application
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import tv.withaibuild.customiuizer.R

/** Every generated page must bind dependencies against its own root exactly once per attachment. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ComposeSettingsPagesTest {
    @Test fun volumeStyleDefaultsToSideAndKeepsTheSavedCompactChoice() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val master = "pref_key_system_volume_ios"
        val style = "pref_key_system_volume_ios_style"
        val compact = "pref_key_system_volume_ios_compact"
        try {
            prefs.edit().putBoolean(master, true).putBoolean(compact, true).remove(style).commit()
            for (expected in listOf("1", "2")) {
                val manager = PreferenceManager(context)
                val screen = ComposeSettingsPages.create(R.id.prefs_controls, context, manager)
                manager.setPreferences(screen); screen.onAttached()
                try {
                    val choice = requireNotNull(manager.findPreference<androidx.preference.ListPreference>(style))
                    assertEquals(expected, choice.value)
                    assertTrue(choice.isEnabled)
                    assertTrue(requireNotNull(manager.findPreference<androidx.preference.TwoStatePreference>(compact)).isChecked)
                    choice.value = "2"
                } finally { screen.onDetached() }
            }
            assertTrue(prefs.getBoolean(compact, false))
        } finally { prefs.edit().remove(master).remove(style).remove(compact).commit() }
    }

    @Test fun deckMasterControlsEveryOptionWithoutLosingSavedMotionOrSizing() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=PreferenceManager.getDefaultSharedPreferences(context)
        val master="pref_key_system_recents_ios18_deck"
        val auto=master+"_size_auto"
        val motion=master+"_motion"
        val size=master+"_size"
        val keys=listOf(master,auto,motion,size)
        try {
            for(enabled in listOf(false,true)) for(automatic in listOf(false,true)) {
                prefs.edit().putBoolean(master,enabled).putBoolean(auto,automatic).putBoolean(motion,false).putInt(size,72).commit()
                val manager=PreferenceManager(context)
                val screen=ComposeSettingsPages.create(R.id.prefs_system_recents,context,manager)
                manager.setPreferences(screen);screen.onAttached()
                try {
                    val owner=requireNotNull(manager.findPreference<androidx.preference.TwoStatePreference>(master))
                    val motionRow=requireNotNull(manager.findPreference<androidx.preference.TwoStatePreference>(motion))
                    for(active in listOf(enabled,!enabled,enabled)) {
                        owner.isChecked=active
                        for(key in listOf(auto,motion,master+"_header_icon",master+"_header_title"))
                            assertEquals(key,active,requireNotNull(manager.findPreference<Preference>(key)).isEnabled)
                        assertEquals(active && !automatic,requireNotNull(manager.findPreference<Preference>(size)).isEnabled)
                        assertFalse(motionRow.isChecked)
                        assertEquals(72,prefs.getInt(size,0))
                    }
                } finally { screen.onDetached() }
            }
        } finally { val editor=prefs.edit();keys.forEach(editor::remove);editor.commit() }
    }

    @Test fun homeIndicatorReverseDependencySurvivesRestoreReentryAndComposeObservation() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=PreferenceManager.getDefaultSharedPreferences(context)
        val hide="pref_key_controls_nonavbar"
        val indicator="pref_key_controls_ios_home_indicator"
        prefs.edit().remove(hide).remove(indicator).commit()
        fun check(expectedEnabled: Boolean,expectedChecked: Boolean) {
            val manager=PreferenceManager(context)
            val screen=ComposeSettingsPages.create(R.id.prefs_controls_navbar,context,manager)
            manager.setPreferences(screen);screen.onAttached()
            try {
                val owner=requireNotNull(manager.findPreference<androidx.preference.TwoStatePreference>(hide))
                val row=requireNotNull(manager.findPreference<androidx.preference.TwoStatePreference>(indicator))
                assertTrue(owner.disableDependentsState)
                tv.withaibuild.customiuizer.ui.SettingsPreferenceList(screen).use { model ->
                    assertSame(row,model.rows.single { it.preference.key==indicator }.preference)
                    assertEquals(expectedEnabled,row.isEnabled);assertEquals(expectedChecked,row.isChecked)
                    owner.isChecked=!owner.isChecked
                    assertEquals(!expectedEnabled,row.isEnabled)
                    owner.isChecked=!owner.isChecked
                    assertEquals(expectedEnabled,row.isEnabled)
                }
            } finally { screen.onDetached() }
        }
        check(true,false)
        for(hidden in listOf(false,true)) for(enabled in listOf(false,true)) {
            prefs.edit().putBoolean(hide,hidden).putBoolean(indicator,enabled).commit()
            repeat(2) { check(!hidden,enabled) }
        }
        prefs.edit().remove(hide).remove(indicator).commit()
    }
    @Test fun allTypedPagesAttachDetachAndReattachWithoutMissingOrDuplicateDependencies() {
        val context = RuntimeEnvironment.getApplication()
        val pages = ComposeSettingsPages::class.java.declaredMethods.filter { it.name.startsWith("prefs_") }
        assertTrue("Canonical and lazy pages must both be covered", pages.size > 70)
        for (page in pages) {
            val manager = PreferenceManager(context)
            val id = R.id::class.java.getField(page.name).getInt(null)
            val screen = ComposeSettingsPages.create(id, context, manager)
            manager.setPreferences(screen)
            fun visit(group: PreferenceGroup): List<Preference> = buildList {
                for (index in 0 until group.preferenceCount) {
                    val child = group.getPreference(index); add(child)
                    if (child is PreferenceGroup) addAll(visit(child))
                }
            }
            val rows = visit(screen)
            repeat(2) {
                screen.onAttached()
                for (row in rows) {
                    val dependency = row.dependency ?: continue
                    val owner = requireNotNull(manager.findPreference<Preference>(dependency))
                    val dependents = ReflectionHelpers.getField<List<Preference>?>(owner, "mDependents").orEmpty()
                    assertEquals("${page.name}: ${row.key} must have one dependency binding", 1, dependents.count { it === row })
                }
                screen.onDetached()
                for (row in rows) {
                    val dependency = row.dependency ?: continue
                    val owner = requireNotNull(manager.findPreference<Preference>(dependency))
                    assertFalse(ReflectionHelpers.getField<List<Preference>?>(owner, "mDependents").orEmpty().any { it === row })
                }
            }
        }
    }
}
