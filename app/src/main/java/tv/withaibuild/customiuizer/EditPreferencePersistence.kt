package tv.withaibuild.customiuizer

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import tv.withaibuild.customiuizer.prefs.SpinnerEx
import tv.withaibuild.customiuizer.prefs.SpinnerExFake

/**
 * Persistence kinds for Edit-page view trees.
 *
 * [SpinnerEx] / [SpinnerExFake] extend AdapterView and are therefore ViewGroups.
 * A generic "skip containers" walk never visits them; a generic
 * "include containers" walk can visit the same parent once per child.
 * Spinner kinds are treated as leaves so each control is saved exactly once
 * and AdapterView chrome (the selected-item TextView) is not persisted.
 */
internal enum class EditPersistenceKind {
    SPINNER_INT,
    SPINNER_FAKE,
    TEXT,
    GROUP,
    OTHER,
}

internal fun editPersistenceKind(view: View): EditPersistenceKind = when (view) {
    is SpinnerExFake -> EditPersistenceKind.SPINNER_FAKE
    is SpinnerEx -> EditPersistenceKind.SPINNER_INT
    is TextView -> EditPersistenceKind.TEXT
    is ViewGroup -> EditPersistenceKind.GROUP
    else -> EditPersistenceKind.OTHER
}

internal fun isEditPersistenceLeaf(kind: EditPersistenceKind): Boolean =
    kind == EditPersistenceKind.SPINNER_INT ||
        kind == EditPersistenceKind.SPINNER_FAKE ||
        kind == EditPersistenceKind.TEXT

internal fun <T> collectEditPersistenceViews(
    root: T?,
    kindOf: (T) -> EditPersistenceKind,
    childrenOf: (T) -> List<T>,
): List<T> {
    val out = ArrayList<T>()
    fun walk(node: T?) {
        node ?: return
        val kind = kindOf(node)
        if (isEditPersistenceLeaf(kind)) {
            out.add(node)
            return
        }
        if (kind == EditPersistenceKind.GROUP) {
            for (child in childrenOf(node)) {
                walk(child)
            }
        }
    }
    walk(root)
    return out
}

internal fun collectEditPersistenceViews(root: View?): List<View> {
    return collectEditPersistenceViews(
        root,
        kindOf = ::editPersistenceKind,
        childrenOf = { view ->
            val group = view as? ViewGroup
            if (group == null) {
                emptyList()
            } else {
                val count = group.childCount
                if (count <= 0) {
                    emptyList()
                } else {
                    (0 until count).map { group.getChildAt(it) }
                }
            }
        },
    )
}

internal fun applyTaggedEditPersistence(
    kind: EditPersistenceKind,
    tag: String?,
    onSpinnerInt: () -> Unit,
    onSpinnerFake: () -> Unit,
    onText: () -> Unit,
): Boolean {
    if (tag.isNullOrEmpty()) return false
    return when (kind) {
        EditPersistenceKind.SPINNER_FAKE -> {
            onSpinnerFake()
            true
        }
        EditPersistenceKind.SPINNER_INT -> {
            onSpinnerInt()
            true
        }
        EditPersistenceKind.TEXT -> {
            onText()
            true
        }
        else -> false
    }
}
