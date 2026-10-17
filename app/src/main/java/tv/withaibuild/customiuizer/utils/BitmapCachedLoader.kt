package tv.withaibuild.customiuizer.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BitmapCachedLoader(
    target: ImageView,
    info: AppData,
    context: Context
) {
    private val targetRef = WeakReference(target)
    private val appInfo = WeakReference(info)
    private val ctx = context.applicationContext
    private val theTag = (target.tag as? Int) ?: -1
    private var iconKey: String = ""

    /**
     * Queues this icon, or joins the load already running for the same key.
     *
     * Every exit from here has to end with the key's `inFlight` entry gone, or that key is
     * permanently marked as loading and no later loader for it will ever start: each one
     * finds a non-empty list, decides it is not the leader, and returns. That is what the
     * queue's old DiscardOldestPolicy did - it dropped a queued task without telling anyone,
     * leaving the entry behind - which is why rejection is now explicit and handled here.
     */
    fun execute() {
        val ad = appInfo.get() ?: return
        iconKey = ad.iconKey
        if (iconKey.isEmpty()) return

        val isLeader = synchronized(inFlightLock) {
            val list = inFlight.getOrPut(iconKey) { mutableListOf() }
            list.add(this)
            list.size == 1
        }
        if (!isLeader) return

        try {
            executor.execute(LoadTask(this))
        } catch (rejected: RejectedExecutionException) {
            // The queue is full. Release the key so the next bind of this row can try again
            // rather than inheriting a load that will never happen.
            Log.w(TAG, "Icon load rejected, queue is full: $iconKey")
            releaseWaiters()
        }
    }

    /**
     * The background half of a load.
     *
     * A plain Runnable rather than a coroutine: this needs a rejection to be an exception it
     * can catch at submission time, and an executor-backed dispatcher instead reroutes a
     * rejected task onto kotlinx's fallback executor, which is neither this pool nor
     * observable from here.
     */
    private class LoadTask(private val loader: BitmapCachedLoader) : Runnable {
        override fun run() {
            val bitmap = try {
                loader.loadBitmap()
            } catch (t: Throwable) {
                // Includes OutOfMemoryError from the icon allocation. Nothing here may reach
                // the thread's default handler; the pool is shared by every icon on screen.
                Log.w(TAG, "Icon load failed", t)
                null
            }
            if (bitmap == null) {
                loader.releaseWaiters()
                return
            }
            mainHandler.post { loader.publish(bitmap) }
        }
    }

    private fun loadBitmap(): Bitmap? {
        var icon: android.graphics.drawable.Drawable? = null

        val ad = appInfo.get() ?: return null
        if (iconKey.isEmpty()) return null

        // If another loader finished while we were queued, return the cached bitmap
        val existing = Helpers.memoryCache.get(iconKey)
        if (existing != null) return existing

        try {
            if (ad.pkgName.isEmpty() && ad.actName.isEmpty()) return null

            val pkgMgr = ctx.packageManager
            if (ad.actName.isNotEmpty() && ad.actName != "-") {
                val component = ComponentName(ad.pkgName, ad.actName)
                try {
                    if (pkgMgr.getActivityInfo(component, PackageManager.MATCH_ALL).icon != 0) {
                        icon = pkgMgr.getActivityIcon(component)
                    }
                } catch (ignored: PackageManager.NameNotFoundException) {
                }
            }
            if (icon == null && pkgMgr.getApplicationInfo(ad.pkgName, PackageManager.MATCH_DISABLED_COMPONENTS).icon != 0) {
                icon = pkgMgr.getApplicationIcon(ad.pkgName)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to load app icon", t)
        }
        if (icon == null) return null

        val newIconSize = ctx.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        val bmp = Bitmap.createBitmap(newIconSize, newIconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        icon.setBounds(0, 0, newIconSize, newIconSize)
        icon.draw(canvas)

        Helpers.memoryCache.put(iconKey, bmp)

        return bmp
    }

    private fun applyToTarget(bmp: Bitmap) {
        val itemIcon = targetRef.get() ?: return
        val tag = itemIcon.tag
        if (tag is Int && theTag == tag && itemIcon.drawable is TransitionDrawable) {
            val crossfader = itemIcon.drawable as TransitionDrawable
            crossfader.addLayer(BitmapDrawable(ctx.resources, bmp))
            crossfader.startTransition(200)
        }
    }

    /**
     * Hands the bitmap to every view waiting on this key. Main thread.
     *
     * The key is released before any view is touched, so a throw from one target cannot
     * leave the key marked as loading; and each target is applied independently, so one
     * detached or recycled view cannot deprive the rest of their icon. The leader is in the
     * list too, which is why it is not applied separately.
     */
    private fun publish(bmp: Bitmap) {
        val waiters: List<BitmapCachedLoader>
        synchronized(inFlightLock) {
            waiters = inFlight.remove(iconKey)?.toList() ?: return
        }
        for (loader in waiters) {
            try {
                loader.applyToTarget(bmp)
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to apply app icon", t)
            }
        }
    }

    private fun releaseWaiters() {
        synchronized(inFlightLock) {
            inFlight.remove(iconKey)
        }
    }

    companion object {
        private const val TAG = "Pengeek.IconLoader"
        private const val MAX_PENDING_TASKS = 128

        internal fun clampLoaderThreadCount(availableProcessors: Int): Int =
            (availableProcessors / 2).coerceIn(2, 4)

        private val threadCount =
            clampLoaderThreadCount(Runtime.getRuntime().availableProcessors())
        private val threadNumber = AtomicInteger()

        private val executor = ThreadPoolExecutor(
            threadCount, threadCount, 15L, TimeUnit.SECONDS,
            LinkedBlockingQueue(MAX_PENDING_TASKS),
            { runnable ->
                Thread({
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    runnable.run()
                }, "Pengeek-IconLoader-${threadNumber.incrementAndGet()}")
            },
            // AbortPolicy, not DiscardOldest: a dropped task has to be visible to the caller
            // so it can release the key. CallerRuns is not an option either - the caller is
            // the UI thread binding a list row, and decoding an icon there is the stall this
            // pool exists to avoid.
            ThreadPoolExecutor.AbortPolicy()
        ).apply { allowCoreThreadTimeOut(true) }

        private val mainHandler = Handler(Looper.getMainLooper())

        private val inFlightLock = Any()
        private val inFlight = mutableMapOf<String, MutableList<BitmapCachedLoader>>()
    }
}
