package tv.withaibuild.customiuizer.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.GlobalActions

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaintenanceJournalTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val profile = "pref_key_various_trim_miui_daemon_network"
    private val first = MaintenanceJournal.Target("com.miui.daemon", "com.miui.daemon.First")
    private val second = MaintenanceJournal.Target("com.miui.daemon", "com.miui.daemon.Second")
    private val backend = object : MaintenanceJournal.Backend {
        var id = "sdk34|user0|certA|version1"
        val states = mutableMapOf(first to 1, second to 0)
        override fun identity(packageName: String) = id
        override fun state(target: MaintenanceJournal.Target): Int = states.getValue(target)
    }
    private lateinit var storage: SharedPreferences
    private lateinit var journal: MaintenanceJournal
    @Before fun setup() {
        storage = context.getSharedPreferences("journal", 0)
        storage.edit().clear().commit()
        journal = MaintenanceJournal(storage, backend)
    }
    private fun intent(states: IntArray = intArrayOf(2, 2)) = Intent(GlobalActions.SET_XIAOMI_COMPONENTS_ACTION)
        .putExtra(GlobalActions.EXTRA_PACKAGE_NAMES, arrayOf(first.packageName, second.packageName))
        .putExtra(GlobalActions.EXTRA_COMPONENT_NAMES, arrayOf(first.component, second.component))
        .putExtra(GlobalActions.EXTRA_COMPONENT_STATES, states)

    @Test fun disabledByDefaultDoesNoWorkAndTheOriginalIsDurableBeforeMutation() {
        assertFalse(journal.contains(profile))
        journal.prepare(profile, intent(), true)
        assertArrayEquals(intArrayOf(1, 0), MaintenanceJournal(storage, backend).snapshot(profile)!!.original)
        assertEquals(mapOf(first to 1, second to 0), backend.states)
    }

    @Test fun restrictionAndRestoreVerifyRealStateAndKeepTheExactOriginals() {
        val attempt = journal.prepare(profile, intent(), true)
        assertFalse(attempt.verify())
        backend.states[first] = 2; backend.states[second] = 2
        assertTrue(attempt.verify()); attempt.finish()
        assertThrows(IllegalArgumentException::class.java) { journal.prepare(profile, intent(), true) }
        val restore = journal.prepare(profile, intent(intArrayOf(1, 0)), false)
        backend.states[first] = 1; backend.states[second] = 0
        assertTrue(restore.verify()); restore.finish()
        assertFalse(journal.contains(profile))
    }

    @Test fun interruptionAndPartialFailureRetainRecoveryWithoutOverwritingTheOriginal() {
        journal.prepare(profile, intent(), true)
        backend.states[first] = 2
        journal = MaintenanceJournal(storage, backend)
        assertThrows(IllegalArgumentException::class.java) { journal.prepare(profile, intent(), true) }
        val restore = journal.prepare(profile, intent(intArrayOf(1, 0)), false)
        assertFalse(restore.verify()); restore.failed()
        assertArrayEquals(intArrayOf(1, 0), journal.snapshot(profile)!!.original)
    }

    @Test fun completeRollbackClearsOnlyTheUnusedNewRestrictionRecord() {
        journal.prepare(profile, intent(), true).failed()
        assertFalse(journal.contains(profile))
    }

    @Test fun lateResultCannotCommitOrDeleteANewerRecoveryOperation() {
        val stale = journal.prepare(profile, intent(), true)
        val recovery = journal.prepare(profile, intent(intArrayOf(1, 0)), false)
        assertFalse(stale.verify())
        assertThrows(IllegalStateException::class.java) { stale.finish() }
        stale.failed()
        assertTrue(journal.contains(profile))
        assertTrue(recovery.verify())
        recovery.finish()
        assertFalse(journal.contains(profile))
    }

    @Test fun changedUserSignatureVersionOrRomRefusesRecoveryAndKeepsTheRecord() {
        journal.prepare(profile, intent(), true)
        for (identity in listOf("sdk34|user10|certA|version1", "sdk34|user0|certB|version1",
            "sdk34|user0|certA|version2", "sdk35|user0|certA|version1")) {
            backend.id = identity
            assertThrows(IllegalArgumentException::class.java) { journal.prepare(profile, intent(intArrayOf(1, 0)), false) }
            assertTrue(journal.contains(profile))
        }
    }

    @Test fun staleBackupCannotReplaceLocalRecoveryTargetsOrStates() {
        journal.prepare(profile, intent(), true)
        assertThrows(IllegalArgumentException::class.java) { journal.prepare(profile, intent(intArrayOf(0, 0)), false) }
        assertArrayEquals(intArrayOf(1, 0), journal.snapshot(profile)!!.original)
    }

    @Test fun missingTargetAndFailedPersistenceNeverProduceAnAttempt() {
        backend.states.remove(first)
        assertThrows(NoSuchElementException::class.java) { journal.prepare(profile, intent(), true) }
        assertFalse(journal.contains(profile))
        backend.states[first] = 1
        val failing = object : SharedPreferences by storage {
            override fun edit(): SharedPreferences.Editor {
                val editor = storage.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value)
                        return this
                    }
                    override fun commit() = false
                }
            }
        }
        assertThrows(IllegalStateException::class.java) { MaintenanceJournal(failing, backend).prepare(profile, intent(), true) }
        assertFalse(journal.contains(profile))
    }

    @Test fun duplicateOrUnboundedTargetsAndInvalidStatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { journal.prepare(profile, intent(intArrayOf(2, 99)), true) }
        assertThrows(IllegalArgumentException::class.java) { journal.prepare(profile,
            intent().putExtra(GlobalActions.EXTRA_COMPONENT_NAMES, arrayOf(first.component, first.component)), true) }
        assertFalse(journal.contains(profile))
    }

    @Test @Suppress("DEPRECATION")
    fun nativeIdentityAndBridgeRejectPackageReplacementAndUnverifiedSigners() {
        val packageId = "com.miui.daemon"
        val info = PackageInfo().apply {
            packageName = packageId; versionCode = 30
            applicationInfo = ApplicationInfo().apply {
                packageName = packageId; uid = 1000
                flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_INSTALLED
            }
            signatures = arrayOf(Signature(byteArrayOf(1, 2, 3)))
        }
        shadowOf(context.packageManager).installPackage(info)
        val native = MaintenanceJournal.NativeBackend(context)
        val wire = Intent(GlobalActions.SET_MIUI_DAEMON_STATE_ACTION)
            .putExtra(MaintenanceJournal.EXTRA_IDENTITY, org.json.JSONObject().put(packageId, native.identity(packageId)).toString())
        assertTrue(MaintenanceJournal.matches(context, wire))
        info.versionCode = 31
        shadowOf(context.packageManager).installPackage(info)
        assertFalse(MaintenanceJournal.matches(context, wire))
        info.signatures = null
        shadowOf(context.packageManager).installPackage(info)
        assertThrows(IllegalStateException::class.java) { native.identity(packageId) }
    }

    @Test @Config(sdk = [35]) fun wrongAndroidVersionCannotPrepareNativeIdentity() {
        assertThrows(IllegalStateException::class.java) { MaintenanceJournal.NativeBackend(context).identity("com.miui.daemon") }
    }
}
