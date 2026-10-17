package tv.withaibuild.customiuizer.utils

import android.app.AlarmManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.PowerManager
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import miui.util.HapticFeedbackUtil
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Leaf utilities the hooks need, kept out of [Helpers].
 *
 * Helpers is the settings app's toolbox: 93 members, and its object initialiser builds an
 * LruCache sized from Runtime.maxMemory(), two comparators, a mod list and a resource-id map.
 * Hook code only ever wanted a dozen leaf functions out of it, but touching any of them ran that
 * initialiser inside system_server, SystemUI and the launcher.
 *
 * Nothing here holds settings-app state or reaches back into Helpers, so the object initialiser
 * is empty apart from the resource-id cache that getResId needs.
 */
object HookUtils {

    @JvmField
    var mWakeLock: PowerManager.WakeLock? = null

    @JvmStatic
    fun getNextStockAlarmTime(context: Context): Long {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return 0
        val aci = alarmMgr.nextAlarmClock
        return aci?.triggerTime ?: 0
    }

    @JvmStatic
    fun dp2px(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            Resources.getSystem().displayMetrics
        )
    }

    @JvmStatic
    fun performLightVibration(context: Context?) {
        performLightVibration(context, false)
    }

    @JvmStatic
    fun performLightVibration(context: Context?, ignoreOff: Boolean) {
        performVibration(context, false, ignoreOff)
    }

    @JvmStatic
    fun performStrongVibration(context: Context?) {
        performVibration(context, true, false)
    }

    @JvmStatic
    fun performStrongVibration(context: Context?, ignoreOff: Boolean) {
        performVibration(context, true, ignoreOff)
    }

    @JvmStatic
    fun performVibration(context: Context?, isStrong: Boolean, ignoreOff: Boolean) {
        context ?: return
        val mHapticFeedbackUtil = HapticFeedbackUtil(context, false)
        mHapticFeedbackUtil.performHapticFeedback(
            if (isStrong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY,
            ignoreOff
        )
    }

    @JvmStatic
    fun copyFile(from: String, to: String): Boolean {
        return try {
            Files.copy(Paths.get(from), Paths.get(to), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap? {
        if (radius < 1) return null
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)

        val w = bitmap.width
        val h = bitmap.height

        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)

        val vmin = IntArray(Math.max(w, h))

        val divsum = ((div + 1) shr 1) * ((div + 1) shr 1)
        val dv = IntArray(256 * divsum) { i -> i / divsum }

        var yw = 0
        var yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (y in 0 until h) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0
            for (i in -radius..radius) {
                var p = pix[yi + Math.min(wm, Math.max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (x in 0 until w) {
                if (rsum < dv.size) r[yi] = dv[rsum]
                if (gsum < dv.size) g[yi] = dv[gsum]
                if (bsum < dv.size) b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                var p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }
        for (x in 0 until w) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0
            var yp = -radius * w
            for (i in -radius..radius) {
                yi = Math.max(0, yp) + x

                sir = stack[i + radius]

                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                rbs = r1 - Math.abs(i)

                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (i < hm) {
                    yp += w
                }
            }
            yi = x
            stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = ((0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum])

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w
                }
                var p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)

        return bitmap
    }

    @JvmStatic
    fun constrain(amount: Int, low: Int, high: Int): Int {
        return if (amount < low) low else if (amount > high) high else amount
    }

    @JvmStatic
    fun constrain(amount: Float, low: Float, high: Float): Float {
        return if (amount < low) low else if (amount > high) high else amount
    }

    @JvmStatic
    fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }

    @JvmStatic
    fun lerp(start: Int, stop: Int, amount: Float): Float {
        return lerp(start.toFloat(), stop.toFloat(), amount)
    }

    /**
     * Returns the interpolation scalar (s) that satisfies the equation: value = lerp(a, b, s)
     *
     * If a == b, then this function will return 0.
     */
    @JvmStatic
    fun lerpInv(a: Float, b: Float, value: Float): Float {
        return if (a != b) (value - a) / (b - a) else 0.0f
    }

    /** Returns the single argument constrained between [0.0, 1.0].  */
    @JvmStatic
    fun saturate(value: Float): Float {
        return constrain(value, 0.0f, 1.0f)
    }

    /** Returns the saturated (constrained between [0, 1]) result of lerpInv.  */
    @JvmStatic
    fun lerpInvSat(a: Float, b: Float, value: Float): Float {
        return saturate(lerpInv(a, b, value))
    }

    @JvmStatic
    fun norm(start: Float, stop: Float, value: Float): Float {
        return (value - start) / (stop - start)
    }

    private fun sq(f: Float): Float {
        return f * f
    }

    @JvmStatic
    fun exp(f: Float): Float {
        return kotlin.math.exp(f.toDouble()).toFloat()
    }

    @JvmStatic
    fun convertGammaToLinearFloat(i: Float, max: Int, f: Float, f2: Float): Float {
        val norm = norm(0.0f, max.toFloat(), i)
        val R = 0.4f
        val A = 0.2146f
        val B = 0.2847f
        val C = 0.4719f
        val value = if (norm <= R) sq(norm / R) else exp((norm - C) / A) + B
        return lerp(f, f2, constrain(value, 0.0f, 12.0f) / 12.0f)
    }

    private val resIdCache = ConcurrentHashMap<String, Int>()

    @JvmStatic
    fun getResId(res: Resources?, name: String?, defType: String?, defPackage: String?): Int {
        if (res == null || name == null || defType == null || defPackage == null) return 0
        val key = "$defPackage:$defType/$name"
        val cached = resIdCache[key]
        if (cached != null) return cached
        val id = res.getIdentifier(name, defType, defPackage)
        resIdCache[key] = id
        return id
    }

}
