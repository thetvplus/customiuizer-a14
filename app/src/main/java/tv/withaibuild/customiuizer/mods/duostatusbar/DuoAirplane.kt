package tv.withaibuild.customiuizer.mods.duostatusbar

import android.graphics.Path

/** A14 silhouette, not an extracted SF Symbol. Prepare the ink centroid once per view. */
internal object DuoAirplane {
    fun create(): Path {
        val points = floatArrayOf(
            0f, -26f, 3.5f, -10f, 23f, 2f, 23f, 8f,
            4f, 3f, 4f, 17f, 11f, 23f, 11f, 27f,
            0f, 24f, -11f, 27f, -11f, 23f, -4f, 17f,
            -4f, 3f, -23f, 8f, -23f, 2f, -3.5f, -10f,
        )
        var area = 0f
        var momentX = 0f
        var momentY = 0f
        for (i in points.indices step 2) {
            val j = (i + 2) % points.size
            val cross = points[i] * points[j + 1] - points[j] * points[i + 1]
            area += cross
            momentX += (points[i] + points[j]) * cross
            momentY += (points[i + 1] + points[j + 1]) * cross
        }
        val x = 59.5f - momentX / (3f * area)
        val y = 61.5f - momentY / (3f * area)
        return Path().apply {
            moveTo(points[0] + x, points[1] + y)
            for (i in 2 until points.size step 2) lineTo(points[i] + x, points[i + 1] + y)
            close()
        }
    }
}
