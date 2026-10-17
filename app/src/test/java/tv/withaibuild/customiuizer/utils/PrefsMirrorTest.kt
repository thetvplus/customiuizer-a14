package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mirror is the only route a setting takes from the settings app to the module, and the
 * module reads it once per hooked process to decide which hooks to install. A key that never
 * arrives is therefore not a delayed setting but a feature that stays off, with nothing in
 * any log to say so - which is how "album art as wallpaper" can look broken while the module
 * itself is loaded and healthy.
 */
class PrefsMirrorTest {

    private val ignored = setOf("pref_key_miuizer_locale", "pref_key_miuizer_launchericon")

    @Test
    fun anIdenticalSnapshotNeedsNoWrites() {
        val both = mapOf("system_albumartonlock" to true, "system_dimtime" to 30)
        val plan = PrefsMirror.plan(both, both, ignored)

        assertTrue(plan.isEmpty)
        assertEquals(0, plan.size)
    }

    @Test
    fun aChangeMissedWhileUnboundIsPushedOnReconcile() {
        // The whole point: the user turned it on with no service bound, the incremental
        // mirror dropped it, and nothing else would ever send it.
        val local = mapOf("system_albumartonlock" to true)
        val remote = emptyMap<String, Any?>()

        val plan = PrefsMirror.plan(local, remote, ignored)

        assertEquals(mapOf<String, Any>("system_albumartonlock" to true), plan.puts)
        assertTrue(plan.removes.isEmpty())
    }

    @Test
    fun aStaleRemoteValueIsOverwrittenRatherThanLeft() {
        val plan = PrefsMirror.plan(
            mapOf("system_albumartonlock_blur" to 12),
            mapOf("system_albumartonlock_blur" to 4),
            ignored
        )

        assertEquals(mapOf<String, Any>("system_albumartonlock_blur" to 12), plan.puts)
    }

    @Test
    fun aKeyTheUserClearedIsRemovedRemotely() {
        val plan = PrefsMirror.plan(
            emptyMap(),
            mapOf("system_albumartonlock" to true),
            ignored
        )

        assertTrue(plan.puts.isEmpty())
        assertEquals(setOf("system_albumartonlock"), plan.removes)
    }

    @Test
    fun settingsAppOnlyKeysAreLeftAloneInBothDirections() {
        // Not pushed, because the module has no use for them; not removed either, because
        // this mirror does not own them and a guess here is another silent write.
        val plan = PrefsMirror.plan(
            mapOf("pref_key_miuizer_locale" to "ru", "system_dimtime" to 30),
            mapOf("pref_key_miuizer_launchericon" to 2, "system_dimtime" to 30),
            ignored
        )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun everyPreferenceTypeSurvivesTheDiff() {
        val local = mapOf(
            "flag" to true,
            "count" to 7,
            "stamp" to 42L,
            "ratio" to 1.5f,
            "name" to "value",
            "apps" to setOf("a", "b")
        )

        val plan = PrefsMirror.plan(local, emptyMap(), ignored)

        assertEquals(local.size, plan.puts.size)
        assertEquals(setOf("a", "b"), plan.puts["apps"])
        assertEquals(42L, plan.puts["stamp"])
    }

    @Test
    fun aSetWithTheSameMembersIsNotRewritten() {
        // Order is not part of a string set's identity; treating it as one would rewrite
        // every app-list preference on every single bind.
        val plan = PrefsMirror.plan(
            mapOf("apps" to setOf("a", "b")),
            mapOf("apps" to setOf("b", "a")),
            ignored
        )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun aNullLocalValueCountsAsRemovedNotAsAWrite() {
        // The incremental path already reads a null as "removed"; disagreeing here would let
        // a reconcile undo what a change event just did.
        val plan = PrefsMirror.plan(
            mapOf("system_albumartonlock" to null),
            mapOf("system_albumartonlock" to true),
            ignored
        )

        assertTrue(plan.puts.isEmpty())
        assertEquals(setOf("system_albumartonlock"), plan.removes)
    }

    @Test
    fun putsAndRemovesAreCountedTogether() {
        val plan = PrefsMirror.plan(
            mapOf("kept" to 1, "added" to 2),
            mapOf("kept" to 1, "dropped" to 3),
            ignored
        )

        assertEquals(mapOf<String, Any>("added" to 2), plan.puts)
        assertEquals(setOf("dropped"), plan.removes)
        assertEquals(2, plan.size)
    }
}
