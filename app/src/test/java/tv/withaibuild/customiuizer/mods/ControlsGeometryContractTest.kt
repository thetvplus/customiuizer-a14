package tv.withaibuild.customiuizer.mods

import android.os.Bundle
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ControlsGeometryContractTest {
    private val saved = MainModule.mPrefs.getAll()
    @After fun restore() { MainModule.mPrefs.replaceSnapshot(saved); Controls.refreshControlsSnapshot() }
    @Test fun importedGestureSizesUseTheSameBoundsAndDefaultsAsTheRealSliders() {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(Path.of("src/main/res/xml/prefs_controls.xml").toFile()).getElementsByTagName("*")
        for (key in listOf("controls_fsg_coverage", "controls_fsg_width")) {
            val node = (0 until nodes.length).map(nodes::item).single {
                it.attributes?.getNamedItem("android:key")?.nodeValue == "pref_key_$key"
            }
            val min = node.attributes.getNamedItem("miuizer:minValue").nodeValue.toInt()
            val max = node.attributes.getNamedItem("miuizer:maxValue").nodeValue.toInt()
            val default = node.attributes.getNamedItem("android:defaultValue").nodeValue.toInt()
            for (raw in listOf(Int.MIN_VALUE, min, default, max, Int.MAX_VALUE)) {
                MainModule.mPrefs.put(key, raw); Controls.refreshControlsSnapshot()
                val actual = if (key.endsWith("coverage")) Controls.controlsConfig.fsgCoverage else Controls.controlsConfig.fsgWidth
                assertEquals(key, raw.coerceIn(min, max), actual)
            }
            MainModule.mPrefs.remove(key); Controls.refreshControlsSnapshot()
            assertEquals(default, if (key.endsWith("coverage")) Controls.controlsConfig.fsgCoverage else Controls.controlsConfig.fsgWidth)
        }
    }
    @Test fun assistGesturesRequireTheirExactTriggerTypeAndAnExplicitNativeSide() {
        fun bundle(side: Int?) = Bundle().apply {
            putInt("triggered_by", 83); putInt("invocation_type", 1)
            if (side != null) putInt("inDirection", side)
        }
        assertEquals("left", Controls.assistGestureSide(bundle(0)))
        assertEquals("right", Controls.assistGestureSide(bundle(1)))
        for (side in listOf(null, -1, 2, 100)) assertNull(Controls.assistGestureSide(bundle(side)))
        assertNull(Controls.assistGestureSide(bundle(0).apply { putInt("triggered_by", 1) }))
        assertNull(Controls.assistGestureSide(bundle(1).apply { putInt("invocation_type", 2) }))
        assertNull(Controls.assistGestureSide(null))
    }
}
