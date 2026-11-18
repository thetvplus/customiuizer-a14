package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field

class ModuleHelperFatalBoundaryTest {

    @Before
    fun resetThermalStateBefore() {
        setThermalId(-1)
        setThermalIdScanned(false)
    }

    @After
    fun resetThermalStateAfter() {
        setThermalId(-1)
        setThermalIdScanned(false)
    }

    @Test(expected = ThreadDeath::class)
    fun callMethodSilentlyPropagatesThreadDeath() {
        ModuleHelper.callMethodSilently(FatalThrowingTarget(), "throwThreadDeath")
    }

    @Test(expected = InternalError::class)
    fun callMethodSilentlyPropagatesVirtualMachineError() {
        ModuleHelper.callMethodSilently(FatalThrowingTarget(), "throwInternalError")
    }

    @Test(expected = OutOfMemoryError::class)
    fun callMethodSilentlyPropagatesOutOfMemoryError() {
        ModuleHelper.callMethodSilently(FatalThrowingTarget(), "throwOutOfMemoryError")
    }

    @Test
    fun callMethodSilentlyReturnsNotExistSymbolForMissingMethod() {
        val result = ModuleHelper.callMethodSilently(Object(), "nonExistentMethod")
        assertSame(ModuleHelper.NOT_EXIST_SYMBOL, result)
    }

    @Test
    fun getObjectFieldSilentlyReturnsNotExistSymbolForMissingField() {
        val result = ModuleHelper.getObjectFieldSilently(Object(), "nonExistentField")
        assertSame(ModuleHelper.NOT_EXIST_SYMBOL, result)
    }

    @Test
    fun getStaticObjectFieldSilentlyReturnsNotExistSymbolForMissingField() {
        val result = ModuleHelper.getStaticObjectFieldSilently(ModuleHelperFatalBoundaryTest::class.java, "nonExistentField")
        assertSame(ModuleHelper.NOT_EXIST_SYMBOL, result)
    }

    @Test(expected = ThreadDeath::class)
    fun thermalTypeReadPropagatesDirectThreadDeath() {
        try {
            ModuleHelper.resolveCpuThermalSource(
                listZones = { intArrayOf(2) },
                readType = { throw ThreadDeath() }
            )
        } finally {
            assertFalse(getThermalIdScanned())
        }
    }

    @Test(expected = OutOfMemoryError::class)
    fun thermalTypeReadPropagatesDirectOutOfMemoryError() {
        try {
            ModuleHelper.resolveCpuThermalSource(
                listZones = { intArrayOf(2) },
                readType = { throw OutOfMemoryError("oom") }
            )
        } finally {
            assertFalse(getThermalIdScanned())
        }
    }

    @Test(expected = InternalError::class)
    fun thermalTypeReadPropagatesWrappedVirtualMachineError() {
        try {
            ModuleHelper.resolveCpuThermalSource(
                listZones = { intArrayOf(2) },
                readType = { throw java.lang.reflect.InvocationTargetException(InternalError("vm"), null) }
            )
        } finally {
            assertFalse(getThermalIdScanned())
        }
    }

    @Test
    fun getCPUThermalIdMemoizesNoMatchResult() {
        // Reset static state so this test is independent.
        setThermalId(-1)
        setThermalIdScanned(false)

        val result = ModuleHelper.getCPUThermalId()
        assertEquals(-1, result)
        assertTrue(getThermalIdScanned())
        assertEquals(-1, getThermalId())

        // A second call must not re-scan.
        val second = ModuleHelper.getCPUThermalId()
        assertEquals(-1, second)
    }

    class FatalThrowingTarget {

        @Suppress("unused")
        fun throwThreadDeath() {
            throw ThreadDeath()
        }

        @Suppress("unused")
        fun throwInternalError() {
            throw InternalError("vm")
        }

        @Suppress("unused")
        fun throwOutOfMemoryError() {
            throw OutOfMemoryError("oom")
        }
    }

    companion object {
        private fun getThermalId(): Int {
            return getStaticField("thermalId").getInt(null)
        }

        private fun setThermalId(value: Int) {
            getStaticField("thermalId").setInt(null, value)
        }

        private fun getThermalIdScanned(): Boolean {
            return getStaticField("thermalIdScanned").getBoolean(null)
        }

        private fun setThermalIdScanned(value: Boolean) {
            getStaticField("thermalIdScanned").setBoolean(null, value)
        }

        private fun getStaticField(name: String): Field {
            val field = ModuleHelper::class.java.getDeclaredField(name)
            field.isAccessible = true
            return field
        }
    }
}
