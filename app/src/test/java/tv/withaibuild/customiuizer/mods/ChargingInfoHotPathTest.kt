package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedModuleInterface
import java.io.FileNotFoundException
import java.lang.reflect.Modifier
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallState
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallStateTestAccess
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.feature.ChargingInfoFeature
import tv.withaibuild.customiuizer.utils.PrefMap

class ChargingInfoHotPathTest {

    /**
     * A [Map] that records every [get] call. This is the "equivalent recorder"
     * for the counting-PrefMap requirement: all public PrefMap typed getters
     * eventually call [getValue], which calls [currentSnapshot] and then
     * [Map.get]. Replacing the snapshot with this map lets the tests observe
     * every key read without changing production signatures.
     */
    private class CountingMap(
        private val backing: HashMap<String, Any>
    ) : Map<String, Any> by backing {
        val gets = mutableListOf<String?>()

        override fun get(key: String): Any? {
            gets.add(key)
            return backing[key]
        }
    }

    @Test
    fun buildChargingInfoDetails_masterMissing_allDetailsTrue_returnsNull() {
        val (prefs, counter) = countingPrefMap(mapOf(
            "system_charginginfo_current" to true,
            "system_charginginfo_voltage" to true,
            "system_charginginfo_wattage" to true,
            "system_charginginfo_temp" to true,
        ))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { throw AssertionError("caller classification must not be reached") },
            batteryPropsProvider = { throw AssertionError("sysfs read must not be reached") }
        )

        assertNull(result)
    }

    @Test
    fun buildChargingInfoDetails_masterFalse_allDetailsTrue_returnsNull() {
        val (prefs, counter) = countingPrefMap(mapOf(
            "system_charginginfo" to false,
            "system_charginginfo_current" to true,
            "system_charginginfo_voltage" to true,
            "system_charginginfo_wattage" to true,
            "system_charginginfo_temp" to true,
        ))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { throw AssertionError("caller classification must not be reached") },
            batteryPropsProvider = { throw AssertionError("sysfs read must not be reached") }
        )

        assertNull(result)
        assertEquals("master=false must read exactly one key", listOf("system_charginginfo"), counter.gets)
    }

    @Test
    fun buildChargingInfoDetails_masterFalse_doesNotCallKeyguardClassifier() {
        var callerCalls = 0
        val (prefs, counter) = countingPrefMap(mapOf(
            "system_charginginfo" to false,
            "system_charginginfo_current" to true,
        ))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { callerCalls++; true },
            batteryPropsProvider = { throw AssertionError("sysfs read must not be reached") }
        )

        assertNull(result)
        assertEquals("caller classification must not run when master is false", 0, callerCalls)
    }

    @Test
    fun buildChargingInfoDetails_masterFalse_doesNotCallSysfsProvider() {
        var providerCalls = 0
        val (prefs, counter) = countingPrefMap(mapOf("system_charginginfo" to false))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { throw AssertionError("caller classification must not be reached") },
            batteryPropsProvider = { providerCalls++; Properties() }
        )

        assertNull(result)
        assertEquals("sysfs provider must not run when master is false", 0, providerCalls)
    }

    @Test
    fun buildChargingInfoDetails_masterTrue_allDetailsFalse_returnsNullAndStopsAtAllDisabled() {
        val (prefs, counter) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to false,
            "system_charginginfo_voltage" to false,
            "system_charginginfo_wattage" to false,
            "system_charginginfo_temp" to false,
        ))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { throw AssertionError("caller classification must not be reached") },
            batteryPropsProvider = { throw AssertionError("sysfs read must not be reached") }
        )

        assertNull(result)
        assertEquals(
            "master=true all-off must read master then four detail switches",
            listOf(
                "system_charginginfo",
                "system_charginginfo_current",
                "system_charginginfo_voltage",
                "system_charginginfo_wattage",
                "system_charginginfo_temp",
            ),
            counter.gets
        )
    }

    @Test
    fun buildChargingInfoDetails_masterTrue_oneDetailEnabled_usesExistingOutput() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_CURRENT_NOW", "2500000")
        }
        val (prefs, counter) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
        ))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { props }
        )

        assertNotNull(result)
        assertTrue("current should be rendered", result!!.contains("A"))
    }

    @Test
    fun buildChargingInfoDetails_masterTrueThenFalseThenTrue_togglesOutput() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_CURRENT_NOW", "2500000")
        }

        // true -> output
        val (prefsTrue, _) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
        ))
        val enabled = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefsTrue,
            isKeyguardCaller = { true },
            batteryPropsProvider = { props }
        )
        assertNotNull(enabled)

        // false -> null (a new PrefMap instance is fine here; the function is pure)
        val (prefsFalse, _) = countingPrefMap(mapOf("system_charginginfo" to false))
        val disabled = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefsFalse,
            isKeyguardCaller = { throw AssertionError("must not classify when disabled") },
            batteryPropsProvider = { throw AssertionError("must not read sysfs when disabled") }
        )
        assertNull(disabled)

        // true again -> output
        val (prefsTrueAgain, _) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
        ))
        val reenabled = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefsTrueAgain,
            isKeyguardCaller = { true },
            batteryPropsProvider = { props }
        )
        assertNotNull(reenabled)
        assertEquals(enabled, reenabled)
    }

    @Test
    fun buildChargingInfoDetails_masterFalse_preservesOriginalHint() {
        val (prefs, _) = countingPrefMap(mapOf("system_charginginfo" to false))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { throw AssertionError("must not classify when disabled") },
            batteryPropsProvider = { throw AssertionError("must not read sysfs when disabled") }
        )

        assertNull(result)
        // A null return tells the Hook to keep the original result/throwable.
    }

    @Test
    fun computeChargingInfoReplacement_chargeOver100_returnsNull() {
        val replacement = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 101,
            hint = "50%",
            prefs = countingPrefMap(mapOf("system_charginginfo" to true)).first,
            isKeyguardCaller = { throw AssertionError("must not classify when charge > 100") },
            batteryPropsProvider = { throw AssertionError("must not read sysfs when charge > 100") }
        )

        assertNull(replacement)
    }

    @Test
    fun computeChargingInfoReplacement_hintNull_returnsNull() {
        val replacement = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 50,
            hint = null,
            prefs = countingPrefMap(mapOf("system_charginginfo" to true)).first,
            isKeyguardCaller = { throw AssertionError("must not classify when hint is null") },
            batteryPropsProvider = { throw AssertionError("must not read sysfs when hint is null") }
        )

        assertNull(replacement)
    }

    @Test
    fun computeChargingInfoReplacement_masterMissing_returnsNull() {
        val (prefs, counter) = countingPrefMap(mapOf("system_charginginfo_current" to true))

        val replacement = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { throw AssertionError("must not classify when master is missing") },
            batteryPropsProvider = { throw AssertionError("must not read sysfs when master is missing") }
        )

        assertNull(replacement)
        assertEquals(listOf("system_charginginfo"), counter.gets)
    }

    @Test
    fun computeChargingInfoReplacement_masterFalse_returnsNull() {
        val (prefs, counter) = countingPrefMap(mapOf("system_charginginfo" to false))

        val replacement = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { throw AssertionError("must not classify when master is false") },
            batteryPropsProvider = { throw AssertionError("must not read sysfs when master is false") }
        )

        assertNull(replacement)
        assertEquals(listOf("system_charginginfo"), counter.gets)
    }

    @Test
    fun computeChargingInfoReplacement_masterTrueDetailEnabled_returnsReplacement() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_CURRENT_NOW", "2500000")
        }
        val (prefs, counter) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
        ))

        val replacement = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { props }
        )

        assertNotNull(replacement)
        assertTrue(replacement!!.contains("A"))
        assertEquals(
            listOf(
                "system_charginginfo",
                "system_charginginfo_current",
                "system_charginginfo_voltage",
                "system_charginginfo_wattage",
                "system_charginginfo_temp",
                "system_charginginfo_view",
            ),
            counter.gets
        )
    }

    @Test
    fun computeChargingInfoReplacement_trueFalseTrue_usesSamePrefMapInstance() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_CURRENT_NOW", "2500000")
        }

        val prefs = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
        )).first

        val enabled = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { props }
        )
        assertNotNull(enabled)

        // Simulate the user turning the master switch off on the same PrefMap
        // instance. put() publishes a new internal immutable Map snapshot, but
        // the PrefMap object itself is unchanged.
        prefs.put("system_charginginfo", false)
        val disabled = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { throw AssertionError("must not classify when disabled") },
            batteryPropsProvider = { throw AssertionError("must not read sysfs when disabled") }
        )
        assertNull(disabled)

        // Simulate the user turning the master switch on again.
        prefs.put("system_charginginfo", true)
        val reenabled = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { props }
        )
        assertNotNull(reenabled)
        assertEquals(enabled, reenabled)
    }

    @Test
    fun computeChargingInfoReplacement_resultAndThrowablePreservedByCaller() {
        // The helper itself never receives result/throwable. Its caller (the
        // Hook) is responsible for preserving the original values when the
        // replacement is null and for clearing the throwable when it is not.
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_CURRENT_NOW", "2500000")
        }

        val disabledPrefs = countingPrefMap(mapOf("system_charginginfo" to false)).first
        var result = "original hint"
        var throwable: Throwable? = RuntimeException("original")
        val replacement = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 50,
            hint = "50%",
            prefs = disabledPrefs,
            isKeyguardCaller = { throw AssertionError("must not classify when disabled") },
            batteryPropsProvider = { throw AssertionError("must not read sysfs when disabled") }
        )
        if (replacement == null) {
            // null -> preserve original result and throwable
        } else {
            result = replacement
            throwable = null
        }

        assertEquals("original hint", result)
        assertNotNull(throwable)

        val enabledPrefs = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
        )).first
        val enabledReplacement = SystemLockScreenHooks.computeChargingInfoReplacement(
            charge = 50,
            hint = "50%",
            prefs = enabledPrefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { props }
        )
        if (enabledReplacement != null) {
            result = enabledReplacement
            throwable = null
        }

        assertNotNull(result)
        assertNull(throwable)
    }

    @Test
    fun buildChargingInfoDetails_chargeOver100_returnsBeforeMasterSwitch() {
        var providerCalls = 0
        val (prefs, counter) = countingPrefMap(mapOf("system_charginginfo" to true))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 101,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { providerCalls++; Properties() }
        )

        assertNull(result)
        assertEquals("charge > 100 must not read any preferences", 0, counter.gets.size)
        assertEquals("charge > 100 must not call sysfs provider", 0, providerCalls)
    }

    @Test
    fun buildChargingInfoDetails_allDetailsDisabled_returnsBeforeCallerClassification() {
        val (prefs, counter) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to false,
            "system_charginginfo_voltage" to false,
            "system_charginginfo_wattage" to false,
            "system_charginginfo_temp" to false,
        ))

        var callerCalls = 0
        var providerCalls = 0
        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { callerCalls++; true },
            batteryPropsProvider = { providerCalls++; Properties() }
        )

        assertNull(result)
        assertEquals("caller classification must not run when all details are disabled", 0, callerCalls)
        assertEquals("battery props provider must not run when all details are disabled", 0, providerCalls)
    }

    @Test
    fun buildChargingInfoDetails_enabled_nonKeyguard_returnsBeforeSysfs() {
        var callerCalls = 0
        var providerCalls = 0
        val (prefs, counter) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
        ))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { callerCalls++; false },
            batteryPropsProvider = { providerCalls++; Properties() }
        )

        assertNull(result)
        assertEquals("caller classification must run exactly once", 1, callerCalls)
        assertEquals("battery props provider must not run for non-keyguard callers", 0, providerCalls)
    }

    @Test
    fun buildChargingInfoDetails_malformedSingleProperty_skipsOnlyThatProperty() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_CURRENT_NOW", "not_a_number")
            setProperty("POWER_SUPPLY_VOLTAGE_NOW", "4200000")
        }
        val (prefs, _) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
            "system_charginginfo_voltage" to true,
        ))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { props }
        )

        assertNotNull(result)
        assertTrue("voltage should still be rendered", result!!.contains("V"))
        assertFalse("malformed current value should be skipped", result.contains("A"))
    }

    @Test
    fun buildChargingInfoDetails_oomFromBusinessPath_isRethrown() {
        val (prefs, _) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
            "system_charginginfo_voltage" to true,
        ))

        try {
            SystemLockScreenHooks.buildChargingInfoDetails(
                charge = 50,
                hint = "50%",
                prefs = prefs,
                isKeyguardCaller = { true },
                batteryPropsProvider = { throw OutOfMemoryError("oom in sysfs") }
            )
            fail("OutOfMemoryError from the business path must propagate")
        } catch (e: OutOfMemoryError) {
            assertEquals("oom in sysfs", e.message)
        }
    }

    @Test
    fun buildChargingInfoDetails_nonFatalReadFailure_returnsOriginalHint() {
        val (prefs, _) = countingPrefMap(mapOf(
            "system_charginginfo" to true,
            "system_charginginfo_current" to true,
        ))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { throw FileNotFoundException("/sys/class/power_supply/battery/uevent") }
        )

        assertNull(result)
    }

    @Test
    fun chargingInfo_requiredHookFailure_isNotInstalled() {
        val result = SystemLockScreenHooks.ChargingInfoHook(fakePackageReadyParam())
        assertEquals(
            "missing ChargeUtils class must produce a transient failure, not INSTALLED",
            FeatureInstallResult.FAILED_TRANSIENT,
            result
        )
    }

    @Test
    fun chargingInfo_featureHasNoLocalInstallOwner() {
        for (field in SystemLockScreenHooks::class.java.declaredFields) {
            assertFalse(
                "isChargingInfoHooked local owner must not exist",
                field.name == "isChargingInfoHooked"
            )
        }
    }

    @Test
    fun chargingInfo_installIsIdempotentThroughFeatureInstallRegistry() {
        FeatureInstallStateTestAccess.clear()
        val registry = FeatureInstallRegistry()
        var installCount = 0

        val lpparam = fakePackageReadyParam()
        val prefs = PrefMap().apply { put("system_charginginfo", true) }
        val realFeature = ChargingInfoFeature(lpparam)

        val countingFeature = object : FeatureDefinition by realFeature {
            override fun create(): FeatureDefinition = this
            override fun install(): FeatureInstallResult {
                installCount++
                return FeatureInstallResult.INSTALLED
            }
        }

        registry.register(countingFeature)
        registry.register(countingFeature)
        val first = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, prefs, collectResults = true)
        val second = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, prefs, collectResults = true)

        assertEquals("ChargingInfo install() must be called exactly once by the registry", 1, installCount)
        assertEquals(listOf(FeatureInstallResult.INSTALLED), first)
        assertEquals(listOf(FeatureInstallResult.ALREADY_INSTALLED), second)
    }

    @Test
    fun baseSystemUiFeature_installHookRemainsAbstract() {
        val base = Class.forName("tv.withaibuild.customiuizer.mods.utils.feature.BaseSystemUiFeature")
        val installHook = base.getDeclaredMethod("installHook")
        assertTrue("BaseSystemUiFeature.installHook() must be abstract", Modifier.isAbstract(installHook.modifiers))
        assertTrue("BaseSystemUiFeature.installHook() must be protected", Modifier.isProtected(installHook.modifiers))
    }

    @Test
    fun baseSystemUiFeature_installRemainsFinal() {
        val base = Class.forName("tv.withaibuild.customiuizer.mods.utils.feature.BaseSystemUiFeature")
        val install = base.getMethod("install")
        assertTrue("BaseSystemUiFeature.install() must be final", Modifier.isFinal(install.modifiers))
        assertTrue("BaseSystemUiFeature.install() must be public", Modifier.isPublic(install.modifiers))
    }

    @Test
    fun chargingInfoFeature_directlyImplementsFeatureDefinition() {
        assertTrue(
            "ChargingInfoFeature must directly implement FeatureDefinition",
            FeatureDefinition::class.java.isAssignableFrom(ChargingInfoFeature::class.java)
        )
        assertFalse(
            "ChargingInfoFeature must not inherit BaseSystemUiFeature",
            Class.forName("tv.withaibuild.customiuizer.mods.utils.feature.BaseSystemUiFeature")
                .isAssignableFrom(ChargingInfoFeature::class.java)
        )
        val method = ChargingInfoFeature::class.java.getMethod("install")
        assertEquals(FeatureInstallResult::class.java, method.returnType)
    }

    private fun countingPrefMap(values: Map<String, Any>): Pair<PrefMap, CountingMap> {
        val prefs = PrefMap()
        val counting = CountingMap(HashMap(values))
        val field = PrefMap::class.java.getDeclaredField("snapshot").apply { isAccessible = true }
        val snapshot = field.get(prefs) as AtomicReference<Map<String, Any>>
        snapshot.set(counting)
        return prefs to counting
    }

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return java.lang.reflect.Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.android.systemui"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "isFirstPackage" -> true
                "toString" -> "FakePackageReadyParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as XposedModuleInterface.PackageReadyParam
    }
}
