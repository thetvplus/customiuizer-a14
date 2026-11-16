package tv.withaibuild.customiuizer.mods.notificationautoexpand

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Executable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NotificationAutoExpandEffectTest {

    @Test
    fun fast_mOnKeyguardTrue_proceedsWithoutGetEntryOrReads() {
        val row = NotificationAutoExpandFixtures.ThrowingGetEntryRow().apply { mOnKeyguard = true }
        val effect = fastEffectForRoot(row.javaClass)
        val chain = makeChain(row)

        effect.intercept(chain)

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun fast_mode2_notSelected_expandsAndProceeds() {
        val row = NotificationAutoExpandFixtures.ExpandableNotificationRow("com.unknown").apply {
            mOnKeyguard = false
        }
        val effect = fastEffect(modeRaw = "2", selectedApps = setOf("com.selected"))
        val chain = makeChain(row)

        effect.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue("unknown package must be expanded in mode 2", row.expandedPackages.contains("com.unknown"))
    }

    @Test
    fun fast_mode2_selected_doesNotExpand() {
        val row = NotificationAutoExpandFixtures.ExpandableNotificationRow("com.selected").apply {
            mOnKeyguard = false
        }
        val effect = fastEffect(modeRaw = "2", selectedApps = setOf("com.selected"))
        val chain = makeChain(row)

        effect.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue("selected package must not be expanded in mode 2", row.expandedPackages.isEmpty())
    }

    @Test
    fun fast_mode3_selected_expandsAndProceeds() {
        val row = NotificationAutoExpandFixtures.ExpandableNotificationRow("com.selected").apply {
            mOnKeyguard = false
        }
        val effect = fastEffect(modeRaw = "3", selectedApps = setOf("com.selected"))
        val chain = makeChain(row)

        effect.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue("selected package must be expanded in mode 3", row.expandedPackages.contains("com.selected"))
    }

    @Test
    fun fast_mode3_notSelected_doesNotExpand() {
        val row = NotificationAutoExpandFixtures.ExpandableNotificationRow("com.unknown").apply {
            mOnKeyguard = false
        }
        val effect = fastEffect(modeRaw = "3", selectedApps = setOf("com.selected"))
        val chain = makeChain(row)

        effect.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue("unknown package must not be expanded in mode 3", row.expandedPackages.isEmpty())
    }

    @Test
    fun inactiveAndMalformedModesLeaveNativeInChargeBeforeReadingTheRow() {
        val row = NotificationAutoExpandFixtures.ThrowingGetEntryRow()
        for (mode in listOf("1", "4", "not a number")) {
            val chain = makeChain(row)
            fastEffectForRoot(row.javaClass, modeRaw = mode).intercept(chain)
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun nativePackageOverrideAndPreferenceGenerationSurviveAnInFlightRefresh() {
        val prefs = preferences("3", setOf("com.effective"))
        val notification = object : StatusBarNotification(
            "com.source", "com.source", 1, null, 1000, 1, 0, Notification(), Process.myUserHandle(), 0,
        ) {
            override fun getPackageName() = "com.effective"
        }
        val row = object : NotificationAutoExpandFixtures.BaseExpandableNotificationRow() {
            var expansions = 0
            private val nativeEntry = object { @JvmField val mSbn = notification }

            fun getEntry(): Any {
                prefs.replaceSnapshot(mapOf("system_expandnotifs" to "3", "system_expandnotifs_apps" to setOf("com.other")))
                return nativeEntry
            }

            fun setSystemExpanded(expanded: Boolean) { if (expanded) expansions++ }
        }
        val effect = NotificationAutoExpandEffect(NotificationAutoExpandResolver.resolve(row.javaClass)!!, prefs)
        repeat(2) {
            val chain = makeChain(row)
            effect.intercept(chain)
            assertEquals(1, chain.proceedCount)
            assertEquals(1, row.expansions)
        }
    }

    @Test
    fun fast_abiNull_selectsLegacy() {
        val row = NotificationAutoExpandFixtures.ExpandableNotificationRow("com.legacy").apply {
            mOnKeyguard = false
        }
        val effect = NotificationAutoExpandEffect(null, preferences("3", setOf("com.legacy")))
        val chain = makeChain(row)

        effect.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue("legacy mode 3 with selected app must expand", row.expandedPackages.contains("com.legacy"))
    }

    @Test
    fun fast_subclassObject_selectsLegacy() {
        val row = NotificationAutoExpandFixtures.SubExpandableNotificationRow("com.legacy").apply {
            mOnKeyguard = false
        }
        (row as NotificationAutoExpandFixtures.BaseExpandableNotificationRow).mOnKeyguard = true
        val effect = fastEffect(modeRaw = "3", selectedApps = setOf("com.legacy"))
        val chain = makeChain(row)

        effect.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue("legacy mode 3 with selected app must expand", row.expandedPackages.contains("com.legacy"))
    }

    @Test
    fun fast_getEntryThrows_propagatesAndProceedNotCalled() {
        val row = NotificationAutoExpandFixtures.ThrowingGetEntryRow().apply {
            mOnKeyguard = false
        }
        val effect = fastEffectForRoot(row.javaClass)
        val chain = makeChain(row)

        try {
            effect.intercept(chain)
            fail("Expected InvocationTargetError from getEntry failure")
        } catch (e: XposedHelpers.InvocationTargetError) {
            assertEquals("chain.proceed must not be called", 0, chain.proceedCount)
            val cause = e.cause
            assertTrue(
                "Cause must be the simulated getEntry failure",
                cause is RuntimeException && cause.message == "simulated getEntry failure",
            )
        }
    }

    @Test
    fun fast_getPackageNameFailure_stopsBeforeProceed() {
        val row = object : NotificationAutoExpandFixtures.BaseExpandableNotificationRow() {
            val entry = NotificationAutoExpandFixtures.ThrowingPackageNameEntry("com.example")

            @Suppress("unused")
            fun getEntry(): Any = entry

            @Suppress("unused")
            fun setSystemExpanded(expanded: Boolean) {}
        }.apply { mOnKeyguard = false }

        val effect = fastEffectForRoot(row.javaClass)
        val chain = makeChain(row)

        try {
            effect.intercept(chain)
            fail("Expected InvocationTargetError from getPackageName failure")
        } catch (e: XposedHelpers.InvocationTargetError) {
            assertEquals("chain.proceed must not be called", 0, chain.proceedCount)
        }
    }

    @Test
    fun fast_mSbnMissing_throwsNoSuchFieldErrorWithoutLegacyRetryOrProceed() {
        val row = NotificationAutoExpandFixtures.EntryWithoutMbnRow("com.example").apply {
            mOnKeyguard = false
        }

        val effect = fastEffectForRoot(row.javaClass)
        val chain = makeChain(row)

        val thrown = try {
            effect.intercept(chain)
            null
        } catch (e: NoSuchFieldError) {
            e
        }

        assertNotNull(
            "missing mSbn must throw NoSuchFieldError",
            thrown,
        )
        assertEquals(
            "FAST mSbn failure must not retry through LEGACY getEntry",
            1,
            row.getEntryCalls,
        )
        assertEquals(
            "chain.proceed must not be called after FAST mSbn failure",
            0,
            chain.proceedCount,
        )
    }

    @Test
    fun fast_setSystemExpandedFailure_stopsBeforeProceed() {
        val row = object : NotificationAutoExpandFixtures.BaseExpandableNotificationRow() {
            val entry = NotificationAutoExpandFixtures.NotificationEntry("com.selected")

            @Suppress("unused")
            fun getEntry(): Any = entry

            @Suppress("unused")
            fun setSystemExpanded(expanded: Boolean) {
                throw RuntimeException("simulated setSystemExpanded failure")
            }
        }.apply { mOnKeyguard = false }

        val effect = fastEffectForRoot(row.javaClass, modeRaw = "3", selectedApps = setOf("com.selected"))
        val chain = makeChain(row)

        try {
            effect.intercept(chain)
            fail("Expected InvocationTargetError from setSystemExpanded failure")
        } catch (e: XposedHelpers.InvocationTargetError) {
            assertEquals("chain.proceed must not be called", 0, chain.proceedCount)
        }
    }

    @Test
    fun fast_chainProceedThrows_rethrown() {
        val row = NotificationAutoExpandFixtures.ExpandableNotificationRow("com.example").apply {
            mOnKeyguard = false
        }
        val effect = fastEffect()
        val chain = makeChain(row, throwOnProceed = true)

        try {
            effect.intercept(chain)
            fail("Expected RuntimeException from chain.proceed")
        } catch (e: RuntimeException) {
            assertEquals("simulated chain.proceed failure", e.message)
        }
    }

    @Test
    fun fast_setSystemExpandedAtMostOnce() {
        val row = NotificationAutoExpandFixtures.ExpandableNotificationRow("com.selected").apply {
            mOnKeyguard = false
        }
        val effect = fastEffect(modeRaw = "3", selectedApps = setOf("com.selected"))
        val chain = makeChain(row)

        effect.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(1, row.expandedPackages.size)
    }

    @Test(expected = IllegalAccessError::class)
    fun fast_mOnKeyguardIllegalAccess_exceptionMappedToIllegalAccessError() {
        val row = NotificationAutoExpandFixtures.PrivateFieldRow().apply { setMOnKeyguard(false) }
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.PrivateFieldRow::class.java,
        )!!
        abi.mOnKeyguardField.isAccessible = false

        val effect = NotificationAutoExpandEffect(abi, preferences())
        val chain = makeChain(row)

        try {
            effect.intercept(chain)
        } finally {
            abi.mOnKeyguardField.isAccessible = true
        }
    }

    @Test
    fun fast_illegalArgumentException_propagates() {
        val badField = NotificationAutoExpandFixtures.BadFieldTarget::class.java.getDeclaredField("badMOnKeyguard")
        badField.isAccessible = true
        val badGetEntry = NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java.getDeclaredMethod("getEntry")
        val badSetSystemExpanded = NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java.getDeclaredMethod("setSystemExpanded", Boolean::class.javaPrimitiveType!!)

        val abi = NotificationAutoExpandAbi(
            NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java,
            badField,
            badGetEntry,
            badSetSystemExpanded,
        )

        val row = NotificationAutoExpandFixtures.ExpandableNotificationRow("com.example").apply { mOnKeyguard = false }
        val effect = NotificationAutoExpandEffect(abi, preferences())
        val chain = makeChain(row)

        try {
            effect.intercept(chain)
            fail("Expected IllegalArgumentException for mismatched field receiver")
        } catch (e: IllegalArgumentException) {
            assertEquals(0, chain.proceedCount)
        }
    }

    private fun fastEffect(
        modeRaw: String = "2",
        selectedApps: Set<String> = emptySet(),
    ): NotificationAutoExpandEffect {
        return fastEffectForRoot(
            NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java,
            modeRaw,
            selectedApps,
        )
    }

    private fun fastEffectForRoot(
        rootClass: Class<*>,
        modeRaw: String = "2",
        selectedApps: Set<String> = emptySet(),
    ): NotificationAutoExpandEffect {
        return NotificationAutoExpandEffect(
            NotificationAutoExpandResolver.resolve(rootClass)!!,
            preferences(modeRaw, selectedApps),
        )
    }

    private fun preferences(mode: String = "2", apps: Set<String> = emptySet()) = PrefMap().apply {
        replaceSnapshot(mapOf("system_expandnotifs" to mode, "system_expandnotifs_apps" to apps))
    }

    private fun makeChain(target: Any?, throwOnProceed: Boolean = false): FakeChain {
        return FakeChain(target, throwOnProceed)
    }

    private class FakeChain(
        private val target: Any?,
        private val throwOnProceed: Boolean,
    ) : XposedInterface.Chain {

        var proceedCount = 0
            private set

        override fun getExecutable(): Executable =
            NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java.getDeclaredMethod("setFeedbackIcon")

        override fun getThisObject(): Any? = target
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = null

        override fun proceed(): Any? {
            proceedCount++
            if (throwOnProceed) throw RuntimeException("simulated chain.proceed failure")
            return null
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            if (throwOnProceed) throw RuntimeException("simulated chain.proceed failure")
            return null
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }
}
