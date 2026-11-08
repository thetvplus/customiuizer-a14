package tv.withaibuild.customiuizer.mods.media

import org.junit.Assert.*
import org.junit.Test

/** Host mutation/ownership checks. This does not stand in for the ROM's layout solver. */
class MediaCardLayoutTest {
    private val names = listOf("album_art", "icon", "header_title", "header_artist", "media_seamless", "media_progress_bar",
        "actionPlayPause", "actionNext", "actionPrev", "action0", "action1", "action2", "action3", "action4", "dismiss", "media_bg")
    private val ids = names.mapIndexed { index, name -> name to index + 1 }.toMap()
    private val layout = MediaCardLayout(Sets::class.java)
    class Layout {
        @JvmField var leftToLeft = 17; @JvmField var leftToRight = 17
        @JvmField var rightToLeft = 17; @JvmField var rightToRight = 17
        @JvmField var startToStart = 17; @JvmField var startToEnd = 17
        @JvmField var endToStart = 17; @JvmField var endToEnd = 17
        @JvmField var topToTop = 17; @JvmField var topToBottom = 17
        @JvmField var bottomToTop = 17; @JvmField var bottomToBottom = 17
        @JvmField var baselineToBaseline = 17; @JvmField var baselineToTop = 17; @JvmField var baselineToBottom = 17
        @JvmField var mWidth = 99; @JvmField var mHeight = 99
        @JvmField var startMargin = 17; @JvmField var endMargin = 17; @JvmField var topMargin = 17
        @JvmField var horizontalBias = .5f
    }
    class Constraint(val visibility: Int, val alpha: Float, val nativeAction: Any) : Cloneable {
        @JvmField val layout = Layout()
        @JvmField val mCustomConstraints = HashMap<String, Any>()
        var failure: Throwable? = null
        public override fun clone(): Constraint {
            failure?.let { throw it }
            return Constraint(visibility, alpha, nativeAction).also { copy ->
            Layout::class.java.fields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .forEach { it.set(copy.layout, it.get(layout)) }
            }
        }
    }
    class Sets {
        @JvmField val mConstraints = LinkedHashMap<Int, Constraint>()
        @JvmField val mSavedAttributes = HashMap<String, Any>()
        @JvmField var mForceId = true
        @JvmField var mIdString = "native"
        @JvmField var mMatchLabels = arrayOf("native")
        @JvmField var mRotate = 0
        @JvmField var derivedState = "native"
        fun getConstraint(id: Int): Constraint? = mConstraints[id]
        val values get() = mConstraints
    }
    private fun native() = Sets().apply {
        ids.values.forEach { id -> values[id] = Constraint(if (id % 2 == 0) 8 else 0, id / 20f, Any()) }
        values.getValue(ids.getValue("media_bg")).layout.mHeight = 1000
    }
    @Test fun presentationCopiesLeaveNativeVisibilityActionsAndOriginalSetsIntact() {
        val original = native()
        val custom = Any()
        original.values.values.forEach { it.mCustomConstraints["native"] = custom }
        original.mSavedAttributes["native"] = custom
        val result = layout.prepare(original, ids, 1f) as Sets
        assertNotSame(original, result)
        assertSame(custom, result.mSavedAttributes["native"])
        assertNotSame(original.mMatchLabels, result.mMatchLabels)
        for ((id, before) in original.values) {
            val after = result.values.getValue(id)
            assertEquals(before.visibility, after.visibility)
            assertEquals(before.alpha, after.alpha, 0f)
            assertSame(before.nativeAction, after.nativeAction)
            assertSame(custom, after.mCustomConstraints["native"])
            assertNotSame(before.mCustomConstraints, after.mCustomConstraints)
            assertEquals(99, before.layout.mWidth)
            assertEquals(17, before.layout.leftToLeft)
        }
        assertEquals(99, result.values.getValue(ids.getValue("dismiss")).layout.mWidth)
    }
    @Test fun missingNativeConstraintRejectsTheCardWithoutSynthesizingControls() {
        val original = native()
        original.values.remove(ids.getValue("action4"))
        assertTrue(runCatching { layout.prepare(original, ids, 1f) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(original.values.values.all { it.layout.leftToLeft == 17 && it.layout.mWidth == 99 })
    }
    @Test fun failedNativeCopyCannotPublishOrMutateAPartiallyPreparedLayout() {
        val original = native()
        val failure = IllegalStateException("native clone")
        original.values.getValue(ids.getValue("header_artist")).failure = failure
        val thrown = runCatching { layout.prepare(original, ids, 1f) }.exceptionOrNull()
        assertSame(failure, thrown?.cause)
        assertEquals(ids.size, original.values.size)
        assertTrue(original.values.values.all { it.layout.leftToLeft == 17 && it.layout.mWidth == 99 })
    }
    @Test fun newNativeLayoutRetainsNewBindingStateInsteadOfRestoringAnOldSnapshot() {
        val first = native()
        val styled = layout.prepare(first, ids, 1f) as Sets
        val action = ids.getValue("action0")
        val replacement = Any()
        styled.values[action] = Constraint(8, 0f, replacement)
        val refreshed = layout.prepare(styled, ids, 1f) as Sets
        assertEquals(8, refreshed.values.getValue(action).visibility)
        assertEquals(0f, refreshed.values.getValue(action).alpha, 0f)
        assertSame(replacement, refreshed.values.getValue(action).nativeAction)
    }
    @Test fun densityAndDirectionalConnectionsKeepHeaderAndRouteSeparated() {
        for (density in listOf(1f, 2.75f, 3.5f)) {
            val result = layout.prepare(native(), ids, density) as Sets
            val art = result.values.getValue(ids.getValue("album_art"))
            assertEquals((52 * density + .5f).toInt(), art.layout.mWidth)
            assertEquals(0f, art.layout.horizontalBias, 0f)
            val title = result.values.getValue(ids.getValue("header_title"))
            assertEquals(ids.getValue("album_art"), title.layout.startToEnd)
            assertEquals(ids.getValue("media_seamless"), title.layout.endToStart)
            assertEquals(0, title.layout.mWidth)
            assertEquals(-1, title.layout.leftToLeft)
            assertEquals(-1, title.layout.rightToRight)
        }
    }
    @Test fun everyActionFitsItsNativeHostAndRetainsAnAccessibleTouchTarget() {
        for ((compact, height) in listOf(true to 140, false to 168)) {
            for (density in listOf(1f, 2.75f, 3.5f)) {
                val result = layout.prepare(native(), ids, density, compact) as Sets
                for (name in names.filter { it.startsWith("action") }) {
                    val action = result.values.getValue(ids.getValue(name)).layout
                    assertTrue(name, action.topMargin + action.mHeight <= (height * density + .5f).toInt())
                    assertTrue(name, action.mWidth >= (48 * density + .5f).toInt())
                    assertTrue(name, action.mHeight >= (48 * density + .5f).toInt())
                }
            }
        }
    }
    @Test fun aShorterNativeHostRejectsThePresentationBeforeCloningOrMutation() {
        val original = native()
        original.values.getValue(ids.getValue("media_bg")).layout.mHeight = 139
        assertTrue(runCatching { layout.prepare(original, ids, 1f, compact = true) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(original.values.values.all { it.layout.leftToLeft == 17 && it.layout.mWidth == 99 })
    }
}
