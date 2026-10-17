package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSurfaceControlArgsTest {

    private val INT = Int::class.javaPrimitiveType!!

    @Test
    fun aospLegacyConstructor_flagsAtIndexFour() {
        val types = arrayOf(
            String::class.java,
            INT, INT, INT, INT,
            Any::class.java,
            INT,
            INT,
        )
        assertEquals(4, WindowSurfaceControlArgs.flagsIndex(types))
        assertEquals(6, WindowSurfaceControlArgs.windowTypeIndex(types, 4))
    }

    @Test
    fun miuiConstructor_flagsAtIndexTwo() {
        val types = arrayOf(
            Any::class.java,
            String::class.java,
            INT,
            Any::class.java,
            INT,
        )
        assertEquals(2, WindowSurfaceControlArgs.flagsIndex(types))
        assertEquals(4, WindowSurfaceControlArgs.windowTypeIndex(types, 2))
    }

    @Test
    fun aosp14BuilderConstructor_hasNoFlagsSlot() {
        val types = arrayOf(
            Any::class.java,
            Any::class.java,
            INT,
            INT,
        )
        assertEquals(-1, WindowSurfaceControlArgs.flagsIndex(types))
        assertEquals(-1, WindowSurfaceControlArgs.windowTypeIndex(types, -1))
    }
}
