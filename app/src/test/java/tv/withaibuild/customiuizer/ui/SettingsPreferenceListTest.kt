package tv.withaibuild.customiuizer.ui

import android.app.Application
import android.os.Looper
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.preferenceGroupInputRow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsPreferenceListTest {
    @Test fun valueOnlyChangesReuseMetadataUntilItsActualLayoutInputsChange() {
        val context = RuntimeEnvironment.getApplication()
        val pref = androidx.preference.CheckBoxPreference(context).apply { title = "Choice" }
        val input = preferenceGroupInputRow(pref)
        repeat(1000) {
            pref.isChecked = it % 2 == 0
            assertSame(input, preferenceGroupInputRow(pref, input))
        }
        pref.isSelectable = false; pref.title = ""; pref.summary = "Info"
        val supporting = preferenceGroupInputRow(pref, input)
        assertNotSame(input, supporting)
        assertTrue(supporting.supportingText)
        assertSame(supporting, preferenceGroupInputRow(pref, supporting))
        val manager = PreferenceManager(context)
        manager.createPreferenceScreen(context).addPreference(pref)
        val nested = preferenceGroupInputRow(pref, supporting)
        assertNotSame(supporting, nested)
        assertTrue(nested.isTopLevel)
    }

    @Test fun valueAndHierarchyChangesKeepUnchangedRowsAndChromeOwners() {
        val context = RuntimeEnvironment.getApplication()
        val manager = PreferenceManager(context)
        val screen = manager.createPreferenceScreen(context)
        manager.setPreferences(screen)
        repeat(160) { index -> screen.addPreference(Preference(context).apply { key = "row$index"; title = "Item $index" }) }
        val note = Preference(context).apply { key = "note"; isSelectable = false }
        screen.addPreference(note)
        screen.onAttached()
        SettingsPreferenceList(screen).use { model ->
            var fresh = 0
            var freshChrome = 0
            val timings = ArrayList<Long>()
            repeat(10) { index ->
                val list = model.rows
                val before = model.rows.toSet()
                val beforeChrome = model.chrome
                val start = System.nanoTime()
                note.summary = if (index % 2 == 0) "Info" else null
                shadowOf(Looper.getMainLooper()).idle()
                timings.add(System.nanoTime() - start)
                fresh += model.rows.count { it !in before }
                freshChrome += model.chrome.count { next -> beforeChrome.none { it === next } }
                assertSame("A supporting-text change only invalidates its existing row", list, model.rows)
            }
            assertEquals(0, fresh)
            assertEquals("Only the preceding divider changes with this note", 10, freshChrome)
            val before = model.rows.toSet()
            val first = model.rows.first().preference
            first.isVisible = false; shadowOf(Looper.getMainLooper()).idle()
            val hiddenFresh = model.rows.count { it !in before }
            val hidden = model.rows.toSet()
            first.isVisible = true; shadowOf(Looper.getMainLooper()).idle()
            val shownFresh = model.rows.count { it !in hidden }
            assertEquals(0, hiddenFresh)
            assertEquals("Only the newly visible owner needs a Row", 1, shownFresh)
            val chrome = model.chrome
            val owners = model.rows.toSet()
            val moved = screen.getPreference(80)
            moved.order = -10; shadowOf(Looper.getMainLooper()).idle()
            assertSame(moved, model.rows.first().preference)
            assertTrue(model.rows.all { it in owners })
            assertSame("Moving equal-shaped cards does not change grouping", chrome, model.chrome)
            println("A14_MODEL_METRICS rows=161 summaryChanges=10 freshRows=$fresh freshChrome=$freshChrome hideFresh=$hiddenFresh showFresh=$shownFresh hostMedianNs=${timings.sorted()[5]}")
            assertEquals(161, model.rows.size)
        }
        screen.onDetached()
    }

    @Test fun closedPresentationStopsRowUpdatesAndCanBeClosedAgain() {
        val context = RuntimeEnvironment.getApplication()
        val manager = PreferenceManager(context)
        val screen = manager.createPreferenceScreen(context)
        manager.setPreferences(screen)
        val pref = Preference(context).apply { key = "entry"; title = "Entry" }
        screen.addPreference(pref)
        val model = SettingsPreferenceList(screen)
        val row = model.rows.single()
        model.close(); model.close()
        pref.title = "Changed"
        pref.isVisible = false
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(row, model.rows.single())
        assertEquals(0, row.revision.intValue)
    }

    @Test fun aReplacementPreferenceCannotBorrowTheRemovedOwnersRow() {
        val context = RuntimeEnvironment.getApplication()
        val manager = PreferenceManager(context)
        val screen = manager.createPreferenceScreen(context)
        manager.setPreferences(screen)
        val old = Preference(context).apply { key = "entry"; title = "Old" }
        screen.addPreference(old)
        SettingsPreferenceList(screen).use { model ->
            val row = model.rows.single()
            screen.removePreference(old)
            val replacement = Preference(context).apply { key = old.key; title = "Replacement" }
            screen.addPreference(replacement)
            shadowOf(Looper.getMainLooper()).idle()
            assertNotSame(row, model.rows.single())
            assertSame(replacement, model.rows.single().preference)
        }
    }
}
