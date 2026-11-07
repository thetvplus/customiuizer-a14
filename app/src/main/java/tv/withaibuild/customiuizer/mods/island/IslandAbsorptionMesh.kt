package tv.withaibuild.customiuizer.mods.island

import android.graphics.RectF
import kotlin.math.pow

/** Native Canvas mesh, driven by the accepted ROM spring. No independent animation clock. */
internal object IslandAbsorptionMesh {
    const val ROWS = 12
    const val FLOATS = (ROWS + 1) * 4
    private const val BEND_STEPS = 32
    private const val BEND_STRENGTH = 2.6f

    /** Immutable geometry only, initialized on the capture worker. Default returns
     * never touch this holder. Interpolation avoids a power operation per mesh row. */
    private object Tables {
        val fractions = FloatArray(ROWS + 1) { it.toFloat() / ROWS }
        val widths = FloatArray(ROWS + 1) { fractions[it].pow(.85f) }
        val bends = FloatArray((ROWS + 1) * (BEND_STEPS + 1)) { index ->
            val row = index % (ROWS + 1)
            when (row) {
                0 -> 0f
                ROWS -> 1f
                else -> fractions[row].pow(1f + BEND_STRENGTH * .25f * (index / (ROWS + 1)) / BEND_STEPS)
            }
        }
    }

    fun prepare() { Tables.bends }

    /** Absorb at the shell's vertical middle, with the full footprint and an edge guard. */
    fun intakeY(shell: RectF, destination: RectF): Float {
        val half = maxOf(1f, destination.height() * .08f) * .5f
        val guard = maxOf(1f, half * 2f)
        val low = maxOf(shell.top, destination.top) + half + guard
        val high = minOf(shell.bottom, destination.bottom) - half - guard
        return if (low <= high) shell.centerY().coerceIn(low, high) else shell.centerY()
    }

    /** A late or faded frame stays native: overlapping translucent layers during
     * commit would briefly increase opacity. Opaque mid-screen releases are admitted. */
    fun mayReplaceNative(progress: Float, alpha: Float) = progress >= 0f && progress < .55f && alpha >= .98f && alpha <= 1f

    /** Only merge once every row fits inside the destination; outside it the page
     * stays at release opacity. The actual island supplies its own live colour/alpha. */
    fun mergeOpacity(bounds: RectF, destination: RectF): Float {
        if (!destination.contains(bounds)) return 1f
        val remaining = ((bounds.height() / destination.height() - .08f) / .92f).coerceIn(0f, 1f)
        return remaining * remaining * (3f - 2f * remaining)
    }

    /** Native progress includes the drag before release. Admission and mesh travel
     * belong to the accepted return, including a release from an already small page. */
    fun returnProgress(progress: Float, atCommit: Float): Float =
        ((progress - atCommit) / (1f - atCommit).coerceAtLeast(.01f)).coerceIn(0f, 1f)

    fun fill(output: FloatArray, rect: RectF, destination: RectF, progress: Float, bounds: RectF? = null,
        intakeY: Float = destination.centerY(), occlusion: RectF? = null,
        contactLeft: Float = destination.left, contactRight: Float = destination.right): Float {
        require(output.size == FLOATS)
        val t = progress.coerceIn(0f, 1f)
        // Keep the captured page's aspect while the leading edge enters first. The
        // native spring supplies the only clock; the trailing edge compresses later.
        val leading = leadingProgress(t)
        val trailing = t.pow(2.25f)
        val holeHeight = maxOf(1f, destination.height() * .08f)
        val holeTop = intakeY - holeHeight * .5f
        val holeBottom = intakeY + holeHeight * .5f
        // Widen only the leading footprint. The body keeps the native rectangle
        // and its existing trailing curve; static island dimensions do not change.
        val leadingNeck = minOf(destination.width() * .78f, (contactRight - contactLeft) * .8f)
        val trailingNeck = minOf(destination.width() * .36f, destination.height())
        val top = rect.top + (holeTop - rect.top) * leading
        val bottom = rect.bottom + (holeBottom - rect.bottom) * trailing
        // Finish narrowing before the top edge touches the visible island. An
        // emerging island supplies its seed aperture; an existing island can be wider.
        val contactFraction = ((rect.top - (occlusion?.bottom ?: destination.bottom)) /
            (rect.top - holeTop).coerceAtLeast(1f)).coerceIn(.01f, 1f)
        val horizontalGrip = (leading / contactFraction).coerceIn(0f, 1f)
        val topWidth = rect.width() + (leadingNeck - rect.width()) * horizontalGrip
        val bottomWidth = rect.width() + (trailingNeck - rect.width()) * t.pow(2.4f)
        val topCenter = rect.centerX() + ((contactLeft + contactRight) * .5f - rect.centerX()) * horizontalGrip
        val bottomCenter = rect.centerX() + (destination.centerX() - rect.centerX()) * trailing
        if (occlusion != null && bounds != null && t >= .96f) {
            // This envelope contains every interpolated row, even for off-centre
            // releases. Skip vertex work only when the existing merge rule would
            // make all of it invisible. Visible/reversed frames rebuild normally.
            val halfWidth = maxOf(topWidth, bottomWidth) * .5f
            bounds.set(minOf(topCenter, bottomCenter) - halfWidth, top,
                maxOf(topCenter, bottomCenter) + halfWidth, bottom)
            if (mergeOpacity(bounds, occlusion) == 0f) return trailing
        }
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        val table = Tables
        val position = (4f * t * (1f - t) * BEND_STEPS).coerceIn(0f, BEND_STEPS.toFloat())
        val lower = position.toInt().coerceAtMost(BEND_STEPS - 1)
        val blend = position - lower
        val offset = lower * (ROWS + 1)
        for (row in 0..ROWS) {
            val v = table.fractions[row]
            val width = topWidth + (bottomWidth - topWidth) * table.widths[row]
            val center = topCenter + (bottomCenter - topCenter) * v
            val curve = table.bends[offset + row] +
                (table.bends[offset + ROWS + 1 + row] - table.bends[offset + row]) * blend
            val y = top + (bottom - top) * curve
            val index = row * 4
            output[index] = center - width * .5f; output[index + 1] = y
            output[index + 2] = center + width * .5f; output[index + 3] = y
            left = minOf(left, output[index]); right = maxOf(right, output[index + 2])
        }
        // Off-centre releases can have their widest edge on an interior row.
        // Test the whole drawn mesh before it is allowed to merge with the island.
        bounds?.set(left, top, right, bottom)
        return trailing
    }

    /** Remaining trailing-body travel, measured in the same geometry used to draw. */
    fun tailProgress(progress: Float): Float = progress.coerceIn(0f, 1f).pow(2.25f)

    fun leadingProgress(progress: Float): Float {
        val grip = (progress / .16f).coerceIn(0f, 1f)
        return grip * grip * (3f - 2f * grip)
    }
}
