package miui.systemui.controlcenter.panel.main.recyclerview

class MainPanelAdapter {
    @JvmField val layoutManager = Layout()

    class Layout {
        @JvmField val spanSizeLookup = Lookup()
        @JvmField var columns = 2
        fun getSpanSizeLookup() = spanSizeLookup
        fun setSpanCount(count: Int) { columns = count }
    }

    class Lookup {
        fun getSpanSize(@Suppress("UNUSED_PARAMETER") position: Int) = 1
    }
}
