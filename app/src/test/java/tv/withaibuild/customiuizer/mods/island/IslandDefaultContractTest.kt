package tv.withaibuild.customiuizer.mods.island

import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class IslandDefaultContractTest {
    @Test fun layoutXmlAndRuntimeDefaultsDescribeTheSameAutomaticConfiguration() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            Path.of("src/main/res/xml/prefs_system_island_layout.xml").toFile())
        val nodes = document.getElementsByTagName("*")
        val prefs = PrefMap()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val key = node.attributes?.getNamedItem("android:key")?.nodeValue ?: continue
            val raw = node.attributes.getNamedItem("android:defaultValue")?.nodeValue ?: continue
            val value: Any = when {
                raw == "true" -> true
                raw == "false" -> false
                node.nodeName.endsWith("SeekBarPreference") -> raw.toInt()
                else -> raw
            }
            prefs.put(key, value)
        }
        assertEquals(IslandOptions.read(PrefMap()), IslandOptions.read(prefs))
        prefs.put("system_island_height", 64)
        assertTrue(IslandOptions.read(prefs).automaticHeight)
        prefs.put("system_island_height_auto", false)
        assertEquals(64, IslandOptions.read(prefs).height)
        assertFalse(IslandOptions.read(prefs).automaticHeight)
        prefs.put("system_island_height", 40)
        assertFalse("Explicit manual 40 dp must not be inferred as automatic", IslandOptions.read(prefs).automaticHeight)
    }
}
