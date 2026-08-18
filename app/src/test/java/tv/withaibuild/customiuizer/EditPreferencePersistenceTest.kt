package tv.withaibuild.customiuizer

import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.prefs.SpinnerEx
import tv.withaibuild.customiuizer.prefs.SpinnerExFake

class EditPreferencePersistenceTest {

    private class FakeNode(
        val name: String,
        val kind: EditPersistenceKind,
        val tag: String? = null,
        val spinnerInt: Int = 0,
        val fakeValue: String? = null,
        val fakeExtras: List<Pair<String, String>> = emptyList(),
        val text: String = "",
        val children: MutableList<FakeNode> = mutableListOf(),
    ) {
        fun add(child: FakeNode): FakeNode {
            children.add(child)
            return this
        }
    }

    @Test
    fun spinnerEx_is_a_view_group_so_generic_skip_containers_cannot_see_it() {
        assertTrue(ViewGroup::class.java.isAssignableFrom(SpinnerEx::class.java))
        assertTrue(SpinnerEx::class.java.isAssignableFrom(SpinnerExFake::class.java))
        assertFalse(TextView::class.java.isAssignableFrom(SpinnerEx::class.java))
    }

    @Test
    fun collector_includes_spinner_leaves_once_and_skips_adapter_chrome() {
        val tree = multiActionTree()
        val collected = collect(tree)
        val names = collected.map { it.name }

        assertEquals(
            listOf("title", "action", "app_label", "app", "shortcut_label", "shortcut", "activity_label", "activity", "activity_class", "toggle_label", "toggle"),
            names,
        )
        assertEquals(1, collected.count { it.name == "action" })
        assertEquals(1, collected.count { it.name == "app" })
        assertEquals(1, collected.count { it.name == "toggle" })
        assertFalse(names.contains("action_chrome"))
        assertFalse(names.contains("app_chrome"))
        assertFalse(names.contains("fields"))
        assertFalse(names.contains("apps_group"))
        assertFalse(names.contains("container"))
    }

    @Test
    fun legacy_skip_view_groups_misses_spinner_ex_and_only_sees_chrome_text() {
        val tree = multiActionTree()
        val legacy = legacyCollectSkippingViewGroups(tree)
        val names = legacy.map { it.name }

        assertFalse(names.contains("action"))
        assertFalse(names.contains("app"))
        assertFalse(names.contains("shortcut"))
        assertFalse(names.contains("activity"))
        assertFalse(names.contains("toggle"))
        assertTrue(names.contains("action_chrome"))
        assertTrue(names.contains("app_chrome"))
    }

    @Test
    fun include_containers_true_would_repeat_parents_new_collector_does_not() {
        val parent = FakeNode("parent", EditPersistenceKind.GROUP)
        parent.add(FakeNode("a", EditPersistenceKind.TEXT, tag = "a"))
        parent.add(FakeNode("b", EditPersistenceKind.TEXT, tag = "b"))

        val duplicatedParents = ArrayList<String>()
        for (child in parent.children) {
            duplicatedParents.add(parent.name)
            duplicatedParents.add(child.name)
        }
        assertEquals(listOf("parent", "a", "parent", "b"), duplicatedParents)

        val collected = collect(parent)
        assertEquals(listOf("a", "b"), collected.map { it.name })
        assertEquals(0, collected.count { it.name == "parent" })
    }

    @Test
    fun selected_action_is_written_and_reloads_to_the_same_value() {
        val key = "pref_key_launcher_swipedown"
        val launcherValues = intArrayOf(1, 2, 3, 4, 5, 6, 7, 17, 12, 26, 8, 9, 20, 10, 13, 14, 22, 23)
        val selectedAction = 4
        val tree = multiActionTree(actionValue = selectedAction, appValue = "com.example/.Main")

        val ints = LinkedHashMap<String, Int>()
        val strings = LinkedHashMap<String, String?>()
        var fakeApplyCount = 0

        for (node in collect(tree)) {
            applyTaggedEditPersistence(
                node.kind,
                node.tag,
                onSpinnerInt = { ints[node.tag!!] = node.spinnerInt },
                onSpinnerFake = {
                    strings[node.tag!!] = node.fakeValue
                    for (extra in node.fakeExtras) {
                        strings[extra.first] = extra.second
                    }
                    fakeApplyCount += 1
                },
                onText = { strings[node.tag!!] = node.text },
            )
        }

        assertEquals(selectedAction, ints["${key}_action"])
        assertEquals(2, ints["${key}_toggle"])
        assertEquals("com.example/.Main", strings["${key}_app"])
        assertEquals("pkg/shortcut", strings["${key}_shortcut"])
        assertEquals("intent://shortcut", strings["${key}_shortcut_intent"])
        assertEquals("pkg/.Activity", strings["${key}_activity"])
        assertEquals(3, fakeApplyCount)

        val reloadedIndex = SpinnerEx.resolveSelectionIndex(ints["${key}_action"]!!, launcherValues)
        assertEquals(3, reloadedIndex)
        assertEquals(selectedAction, SpinnerEx.resolveSelectedValue(reloadedIndex, launcherValues))

        val missingKeyReloadsAsNone = SpinnerEx.resolveSelectionIndex(1, launcherValues)
        assertEquals(0, missingKeyReloadsAsNone)
        assertEquals(1, SpinnerEx.resolveSelectedValue(missingKeyReloadsAsNone, launcherValues))
    }

    @Test
    fun untagged_text_and_groups_do_not_write_prefs() {
        val title = FakeNode("title", EditPersistenceKind.TEXT, text = "Action")
        val group = FakeNode("group", EditPersistenceKind.GROUP).add(title)
        var wrote = false
        for (node in collect(group)) {
            wrote = applyTaggedEditPersistence(
                node.kind,
                node.tag,
                onSpinnerInt = { wrote = true },
                onSpinnerFake = { wrote = true },
                onText = { wrote = true },
            ) || wrote
        }
        assertFalse(wrote)
    }

    private fun collect(root: FakeNode): List<FakeNode> {
        return collectEditPersistenceViews(
            root,
            kindOf = { it.kind },
            childrenOf = { it.children },
        )
    }

    private fun legacyCollectSkippingViewGroups(root: FakeNode): List<FakeNode> {
        val out = ArrayList<FakeNode>()
        fun walk(node: FakeNode) {
            val isViewGroup = node.kind == EditPersistenceKind.GROUP ||
                node.kind == EditPersistenceKind.SPINNER_INT ||
                node.kind == EditPersistenceKind.SPINNER_FAKE
            if (isViewGroup) {
                for (child in node.children) walk(child)
            } else {
                out.add(node)
            }
        }
        walk(root)
        return out
    }

    private fun multiActionTree(
        actionValue: Int = 4,
        appValue: String? = "com.example/.Main",
    ): FakeNode {
        val key = "pref_key_launcher_swipedown"
        val fields = FakeNode("fields", EditPersistenceKind.GROUP)
            .add(FakeNode("title", EditPersistenceKind.TEXT, text = "Action"))
            .add(
                FakeNode("action", EditPersistenceKind.SPINNER_INT, tag = "${key}_action", spinnerInt = actionValue)
                    .add(FakeNode("action_chrome", EditPersistenceKind.TEXT, text = "Lock")),
            )
            .add(
                FakeNode("apps_group", EditPersistenceKind.GROUP)
                    .add(FakeNode("app_label", EditPersistenceKind.TEXT, text = "App"))
                    .add(
                        FakeNode("app", EditPersistenceKind.SPINNER_FAKE, tag = "${key}_app", fakeValue = appValue)
                            .add(FakeNode("app_chrome", EditPersistenceKind.TEXT, text = "Example")),
                    ),
            )
            .add(
                FakeNode("shortcuts_group", EditPersistenceKind.GROUP)
                    .add(FakeNode("shortcut_label", EditPersistenceKind.TEXT, text = "Shortcut"))
                    .add(
                        FakeNode(
                            "shortcut",
                            EditPersistenceKind.SPINNER_FAKE,
                            tag = "${key}_shortcut",
                            fakeValue = "pkg/shortcut",
                            fakeExtras = listOf("${key}_shortcut_intent" to "intent://shortcut"),
                        ),
                    ),
            )
            .add(
                FakeNode("activities_group", EditPersistenceKind.GROUP)
                    .add(FakeNode("activity_label", EditPersistenceKind.TEXT, text = "Activity"))
                    .add(FakeNode("activity", EditPersistenceKind.SPINNER_FAKE, tag = "${key}_activity", fakeValue = "pkg/.Activity"))
                    .add(FakeNode("activity_class", EditPersistenceKind.TEXT, text = "pkg/.Activity")),
            )
            .add(
                FakeNode("toggles_group", EditPersistenceKind.GROUP)
                    .add(FakeNode("toggle_label", EditPersistenceKind.TEXT, text = "Toggle"))
                    .add(FakeNode("toggle", EditPersistenceKind.SPINNER_INT, tag = "${key}_toggle", spinnerInt = 2)),
            )
        return FakeNode("container", EditPersistenceKind.GROUP).add(fields)
    }
}
