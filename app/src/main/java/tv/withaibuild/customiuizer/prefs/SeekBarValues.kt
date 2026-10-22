package tv.withaibuild.customiuizer.prefs

import java.math.BigDecimal
import java.math.RoundingMode

/** The existing slider lattice is multiples of step, before the display shift/divider. */
internal class SeekBarValues(min: Int, max: Int, val step: Int, val shift: Int, val divider: Int) {
    private val first = (min.toLong() + step - 1) / step * step
    val isValid = first <= max / step * step
    val minimum = first.coerceAtMost(max.toLong()).toInt()
    val maximum = (max / step * step).coerceAtLeast(minimum)
    val progressMax = (maximum - minimum) / step

    fun storage(progress: Int): Int = minimum + progress.coerceIn(0, progressMax) * step
    fun progress(value: Int): Int =
        ((value.toLong().coerceIn(minimum.toLong(), maximum.toLong()) - minimum + step / 2) / step).toInt()
            .coerceIn(0, progressMax)
    fun bounded(value: Int): Int = storage(progress(value))
    fun contains(value: Int): Boolean = isValid && value in minimum..maximum && (value - minimum) % step == 0

    fun number(value: Int): String = BigDecimal.valueOf(value.toLong() - shift)
        .divide(BigDecimal.valueOf(divider.toLong()), 12, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()

    fun stepNumber(): String = BigDecimal.valueOf(step.toLong())
        .divide(BigDecimal.valueOf(divider.toLong()), 12, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()

    fun parse(input: String): Int? {
        val text = input.trim().replace(',', '.')
        // Bound work as well as values. Exponents, suffixes and nonfinite numbers are not input.
        if (text.length !in 1..64 || !DECIMAL.matches(text)) return null
        return try {
            val raw = BigDecimal(text).multiply(BigDecimal.valueOf(divider.toLong()))
                .add(BigDecimal.valueOf(shift.toLong())).intValueExact()
            raw.takeIf(::contains)
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    companion object {
        private val DECIMAL = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
    }
}
