package miui.systemui.controlcenter.panel.main.recyclerview

class MainPanelAdapter {
    @JvmField val layoutManager = Layout(this)
    @JvmField var contentMap = linkedMapOf<Int, List<MainPanelListItem>>()

    class Layout(owner: MainPanelAdapter) {
        @JvmField val spanSizeLookup = `layoutManager$1$1`(owner)
        @JvmField var columns = 2
        fun getSpanSizeLookup() = spanSizeLookup
        fun setSpanCount(count: Int) { columns = count }
    }

    class `layoutManager$1$1`(private val owner: MainPanelAdapter) {
        fun getSpanSize(position: Int): Int = (getItem(owner.contentMap, position)?.getSpanSize() ?: 4).coerceIn(1, 4)
    }

    companion object {
        private fun getItem(map: Map<Int, List<MainPanelListItem>>, position: Int): MainPanelListItem? {
            var remaining = position
            for (items in map.values) {
                if (remaining < items.size) return items.getOrNull(remaining)
                remaining -= items.size
            }
            return null
        }
    }
}

fun interface MainPanelListItem {
    fun getSpanSize(): Int
}
