package tv.withaibuild.customiuizer.utils

/**
 * Stable selected-first ordering. Selection may involve a system-service call, so read it
 * exactly once per row instead of twice per sort comparison. Nothing is retained between
 * publications, and the source stays intact if a selection read fails.
 */
internal inline fun selectedAppsFirst(
    source: ArrayList<AppData>,
    isSelected: (AppData) -> Boolean,
): ArrayList<AppData> {
    if (source.isEmpty()) return source
    val selected = BooleanArray(source.size)
    var selectedCount = 0
    for (index in source.indices) {
        if (isSelected(source[index])) {
            selected[index] = true
            selectedCount++
        }
    }
    if (selectedCount == 0 || selectedCount == source.size) return source

    val result = ArrayList<AppData>(source.size)
    for (index in source.indices) if (selected[index]) result.add(source[index])
    for (index in source.indices) if (!selected[index]) result.add(source[index])
    return result
}
