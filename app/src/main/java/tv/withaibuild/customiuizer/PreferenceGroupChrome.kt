package tv.withaibuild.customiuizer

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx

/**
 * Settings-app chrome only. Maps the visible Preference adapter order onto
 * iOS-style grouped inset cards: top-level category titles sit outside the
 * card, nested categories stay inside as inner headers.
 */
internal object PreferenceGroupChrome {

    enum class Kind {
        SECTION_HEADER,
        HIDDEN_HEADER,
        INNER_HEADER,
        CARD,
    }

    data class InputRow(
        val isCategory: Boolean,
        val isTopLevel: Boolean,
        val titleVisible: Boolean,
    )

    data class Chrome(
        val kind: Kind,
        val groupId: Int,
        val inCard: Boolean,
        val cardStart: Boolean,
        val cardEnd: Boolean,
        val dividerBelow: Boolean,
    )

    fun layout(rows: List<InputRow>): List<Chrome> {
        if (rows.isEmpty()) return emptyList()
        val result = ArrayList<Chrome>(rows.size)
        var index = 0
        var groupId = 0
        while (index < rows.size) {
            val row = rows[index]
            if (isTopLevelCategory(row)) {
                val bodyStart = index + 1
                var bodyEnd = bodyStart
                while (bodyEnd < rows.size && !isTopLevelCategory(rows[bodyEnd])) {
                    bodyEnd++
                }
                result.add(
                    Chrome(
                        kind = if (row.titleVisible) Kind.SECTION_HEADER else Kind.HIDDEN_HEADER,
                        groupId = groupId,
                        inCard = false,
                        cardStart = false,
                        cardEnd = false,
                        dividerBelow = false,
                    )
                )
                appendCard(result, rows, bodyStart, bodyEnd, groupId)
                index = bodyEnd
            } else {
                var bodyEnd = index
                while (bodyEnd < rows.size && !isTopLevelCategory(rows[bodyEnd])) {
                    bodyEnd++
                }
                appendCard(result, rows, index, bodyEnd, groupId)
                index = bodyEnd
            }
            groupId++
        }
        return result
    }

    private fun isTopLevelCategory(row: InputRow): Boolean = row.isCategory && row.isTopLevel

    private fun appendCard(
        result: MutableList<Chrome>,
        rows: List<InputRow>,
        start: Int,
        end: Int,
        groupId: Int,
    ) {
        if (start >= end) return
        val last = end - 1
        for (i in start until end) {
            result.add(
                Chrome(
                    kind = if (rows[i].isCategory) Kind.INNER_HEADER else Kind.CARD,
                    groupId = groupId,
                    inCard = true,
                    cardStart = i == start,
                    cardEnd = i == last,
                    dividerBelow = i != last,
                )
            )
        }
    }
}

internal fun flattenVisiblePreferences(screen: PreferenceScreen): List<Preference> {
    val flattened = ArrayList<Preference>()
    flattenPreferenceGroup(screen, flattened)
    val visible = ArrayList<Preference>(flattened.size)
    for (pref in flattened) {
        if (pref.isVisible) visible.add(pref)
    }
    return visible
}

private fun flattenPreferenceGroup(group: PreferenceGroup, out: MutableList<Preference>) {
    val count = group.preferenceCount
    for (i in 0 until count) {
        val pref = group.getPreference(i)
        out.add(pref)
        if (pref is PreferenceGroup && pref !is PreferenceScreen) {
            flattenPreferenceGroup(pref, out)
        }
    }
}

internal fun preferenceGroupInputRow(pref: Preference): PreferenceGroupChrome.InputRow {
    return PreferenceGroupChrome.InputRow(
        isCategory = pref is PreferenceCategory,
        isTopLevel = pref.parent is PreferenceScreen,
        titleVisible = (pref as? PreferenceCategoryEx)?.isTitleVisible() ?: true,
    )
}

internal fun groupedListRowBackground(position: Int, count: Int): Int {
    if (count <= 1) return R.drawable.pref_search_row_single
    return when (position) {
        0 -> R.drawable.pref_search_row_top
        count - 1 -> R.drawable.pref_search_row_bottom
        else -> R.drawable.pref_search_row_middle
    }
}

internal fun applyGroupedListRow(view: View, position: Int, count: Int) {
    view.setBackgroundResource(groupedListRowBackground(position, count))
    if (!view.clipToOutline) view.clipToOutline = true
}

internal class PreferenceGroupDecoration(
    private val screenProvider: () -> PreferenceScreen?,
) : RecyclerView.ItemDecoration() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rectF = RectF()
    private val path = Path()
    private val radii = FloatArray(8)
    private var paintsReady = false
    private var inset = 0
    private var radius = 0f
    private var groupGap = 0
    private var headerBottom = 0
    private var listTop = 0
    private var listBottom = 0
    private var dividerHeight = 0
    private var dividerInset = 0

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            outRect.setEmpty()
            applyRowClip(view, inCard = false, roundTop = false, roundBottom = false)
            return
        }
        ensureMetrics(parent)
        val chrome = chrome(parent)
        if (position >= chrome.size) {
            outRect.setEmpty()
            applyRowClip(view, inCard = false, roundTop = false, roundBottom = false)
            return
        }
        val row = chrome[position]
        when (row.kind) {
            PreferenceGroupChrome.Kind.SECTION_HEADER -> {
                val top = if (position == 0) listTop else groupGap
                outRect.set(0, top, 0, headerBottom)
            }
            PreferenceGroupChrome.Kind.HIDDEN_HEADER -> outRect.setEmpty()
            PreferenceGroupChrome.Kind.INNER_HEADER,
            PreferenceGroupChrome.Kind.CARD -> {
                val top = when {
                    !row.cardStart -> 0
                    position == 0 -> listTop
                    chrome[position - 1].kind == PreferenceGroupChrome.Kind.SECTION_HEADER -> 0
                    else -> groupGap
                }
                val bottom = if (row.dividerBelow) dividerHeight else 0
                outRect.set(inset, top, inset, bottom)
            }
        }
        if (position == chrome.lastIndex) {
            outRect.bottom += listBottom
        }
        applyRowClip(view, row.inCard, row.cardStart, row.cardEnd)
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val chrome = chrome(parent)
        if (chrome.isEmpty()) return
        ensureMetrics(parent)
        val childCount = parent.childCount
        var index = 0
        while (index < childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position >= chrome.size) {
                index++
                continue
            }
            val row = chrome[position]
            if (!row.inCard) {
                index++
                continue
            }
            var lastChild = child
            var roundTop = row.cardStart
            var roundBottom = row.cardEnd
            var look = index + 1
            while (look < childCount) {
                val next = parent.getChildAt(look)
                val nextPos = parent.getChildAdapterPosition(next)
                if (nextPos == RecyclerView.NO_POSITION || nextPos >= chrome.size) break
                val nextRow = chrome[nextPos]
                if (!nextRow.inCard || nextRow.groupId != row.groupId) break
                lastChild = next
                roundBottom = nextRow.cardEnd
                look++
            }
            rectF.set(
                child.left.toFloat(),
                child.top + child.translationY,
                child.right.toFloat(),
                lastChild.bottom + lastChild.translationY,
            )
            setCornerRadii(radius, roundTop, roundBottom)
            path.reset()
            path.addRoundRect(rectF, radii, Path.Direction.CW)
            c.drawPath(path, fillPaint)
            index = look
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val chrome = chrome(parent)
        if (chrome.isEmpty()) return
        ensureMetrics(parent)
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position >= chrome.size) continue
            val row = chrome[position]
            if (!row.dividerBelow) continue
            val y = child.bottom + child.translationY
            val left = (child.left + dividerInset).toFloat()
            val right = (child.right - dividerInset).toFloat()
            c.drawRect(left, y, right, y + dividerHeight, dividerPaint)
        }
    }

    private fun chrome(parent: RecyclerView): List<PreferenceGroupChrome.Chrome> {
        val screen = screenProvider() ?: return emptyList()
        val count = parent.adapter?.itemCount ?: 0
        val rows = flattenVisiblePreferences(screen)
        if (rows.size != count) return emptyList()
        val inputs = ArrayList<PreferenceGroupChrome.InputRow>(rows.size)
        for (pref in rows) {
            inputs.add(preferenceGroupInputRow(pref))
        }
        return PreferenceGroupChrome.layout(inputs)
    }

    private fun ensureMetrics(parent: RecyclerView) {
        if (paintsReady) return
        val res = parent.resources
        inset = res.getDimensionPixelSize(R.dimen.preference_group_inset)
        radius = res.getDimension(R.dimen.preference_group_radius)
        groupGap = res.getDimensionPixelSize(R.dimen.preference_group_gap)
        headerBottom = res.getDimensionPixelSize(R.dimen.preference_group_header_bottom)
        listTop = res.getDimensionPixelSize(R.dimen.preference_screen_padding_top)
        listBottom = res.getDimensionPixelSize(R.dimen.preference_screen_padding_bottom)
        dividerHeight = res.getDimensionPixelSize(R.dimen.preference_group_divider_height)
        dividerInset = res.getDimensionPixelSize(R.dimen.preference_item_child_padding)
        fillPaint.color = parent.context.getColor(R.color.color_surface_container)
        dividerPaint.color = parent.context.getColor(R.color.color_outline_variant)
        dividerPaint.alpha = 102
        paintsReady = true
    }

    private fun setCornerRadii(r: Float, roundTop: Boolean, roundBottom: Boolean) {
        val top = if (roundTop) r else 0f
        val bottom = if (roundBottom) r else 0f
        radii[0] = top
        radii[1] = top
        radii[2] = top
        radii[3] = top
        radii[4] = bottom
        radii[5] = bottom
        radii[6] = bottom
        radii[7] = bottom
    }

    private fun applyRowClip(view: View, inCard: Boolean, roundTop: Boolean, roundBottom: Boolean) {
        if (!inCard) {
            if (view.clipToOutline) {
                view.clipToOutline = false
                view.outlineProvider = ViewOutlineProvider.BACKGROUND
            }
            return
        }
        val provider = view.outlineProvider as? GroupedRowOutline ?: GroupedRowOutline()
        provider.apply(view, radius, roundTop, roundBottom)
    }
}

/**
 * One OutlineProvider per visible row. Reused across binds; getOutline allocates nothing.
 * minSdk 34, so [Outline.setPath] can round only the card-start / card-end corners.
 */
private class GroupedRowOutline : ViewOutlineProvider() {
    private var radius = 0f
    private var roundTop = false
    private var roundBottom = false
    private val path = Path()
    private val rect = RectF()
    private val radii = FloatArray(8)

    fun apply(view: View, radius: Float, roundTop: Boolean, roundBottom: Boolean) {
        val unchanged = this.radius == radius &&
            this.roundTop == roundTop &&
            this.roundBottom == roundBottom &&
            view.outlineProvider === this &&
            view.clipToOutline
        this.radius = radius
        this.roundTop = roundTop
        this.roundBottom = roundBottom
        if (view.outlineProvider !== this) view.outlineProvider = this
        if (!view.clipToOutline) view.clipToOutline = true
        if (!unchanged) view.invalidateOutline()
    }

    override fun getOutline(view: View, outline: Outline) {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) {
            outline.setEmpty()
            return
        }
        if (!roundTop && !roundBottom) {
            outline.setRect(0, 0, width, height)
            return
        }
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val top = if (roundTop) radius else 0f
        val bottom = if (roundBottom) radius else 0f
        radii[0] = top
        radii[1] = top
        radii[2] = top
        radii[3] = top
        radii[4] = bottom
        radii[5] = bottom
        radii[6] = bottom
        radii[7] = bottom
        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)
        outline.setPath(path)
    }
}
