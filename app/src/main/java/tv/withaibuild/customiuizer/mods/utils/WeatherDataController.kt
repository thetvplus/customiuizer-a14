package tv.withaibuild.customiuizer.mods.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Weather data cache for the status bar clock.
 *
 * - Only the application context is retained. The clock controller is weakly
 *   referenced so rebuilding SystemUI clock controllers cannot leak old views.
 * - The ContentProvider query is protected by a Mutex so ticks do not pile up
 *   while a query is already in flight.
 * - TIME_TICK is only registered while the screen is on; screen off stops both
 *   the receiver and any delayed refresh job.
 * - A missed force refresh is remembered and executed when the screen turns
 *   back on.
 *
 * context is set to [Context.applicationContext] in [initContext]; a previous
 * context is unregistered and its coroutine scope is cancelled before the
 * replacement. updateTarget is a WeakReference. Lint cannot see this explicit
 * ownership/receiver lifecycle, so the static-Context warning is suppressed at
 * the object level.
 */
@SuppressLint("StaticFieldLeak")
object WeatherDataController : ScreenStateController.ScreenStateListener {

    @JvmField
    @Volatile
    var weatherInfo: String = ""

    private var updateTarget: WeakReference<Any>? = null
    private var timeTickReceiver: BroadcastReceiver? = null
    private var context: Context? = null

    private var controllerScope = newScope()
    private val queryMutex = Mutex()
    private var timeTickRegistered = false
    private var pendingForceRefresh = false
    private var queryFailureLogged = false

    private fun newScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + ModuleHelper.coroutineFailureHandler)

    override fun onScreenStateChanged(isOn: Boolean) {
        val ctx = context ?: return
        if (isOn) {
            ensureTickRegistered(ctx)
            if (pendingForceRefresh) {
                pendingForceRefresh = false
                refreshWeatherData(true)
            }
        } else {
            controllerScope.cancel()
            controllerScope = newScope()
            unregisterTick(ctx)
        }
    }

    private fun queryWeather() {
        val ctx = context ?: return

        var cursor: Cursor? = null
        try {
            cursor = ctx.contentResolver.query(
                Uri.parse("content://weather/actualWeatherData/1"),
                null, null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                var newWeather = ""
                var columnIndex = cursor.getColumnIndex("description")
                if (columnIndex >= 0) {
                    newWeather = cursor.getString(columnIndex)
                }
                columnIndex = cursor.getColumnIndex("temperature")
                if (columnIndex >= 0) {
                    newWeather += (" " + cursor.getString(columnIndex))
                }
                weatherInfo = newWeather
            }
            queryFailureLogged = false
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            if (!queryFailureLogged) {
                queryFailureLogged = true
                XposedHelpers.log(t)
            }
        } finally {
            cursor?.close()
        }
    }

    @JvmStatic
    fun refreshWeatherData(forceRefresh: Boolean) {
        if (!ScreenStateController.isScreenOn()) {
            if (forceRefresh) pendingForceRefresh = true
            return
        }

        if (forceRefresh) pendingForceRefresh = false

        controllerScope.launch {
            withContext(Dispatchers.IO) {
                queryMutex.withLock { queryWeather() }
            }
            if (forceRefresh) {
                val target = updateTarget?.get()
                if (target != null) {
                    ModuleHelper.guarded { XposedHelpers.callMethod(target, "updateTime") }
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @JvmStatic
    fun initContext(context: Context, clockController: Any) {
        // Cancel any pending work from a previous context and start fresh.
        controllerScope.cancel()
        controllerScope = newScope()
        pendingForceRefresh = false

        val appContext = context.applicationContext
        val oldContext = this.context
        oldContext?.let { unregisterTick(it) }

        updateTarget = WeakReference(clockController)
        this.context = appContext

        ScreenStateController.addListener(appContext, this)

        timeTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (!ScreenStateController.isScreenOn()) return@guarded
                refreshWeatherData(false)
            }
        }

        if (ScreenStateController.isScreenOn()) {
            ensureTickRegistered(appContext)
            controllerScope.launch {
                delay(1800)
                if (ScreenStateController.isScreenOn()) refreshWeatherData(true)
            }
        } else {
            // No need to query while the screen is off.
            pendingForceRefresh = true
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun ensureTickRegistered(ctx: Context) {
        if (timeTickRegistered || timeTickReceiver == null) return
        try {
            ctx.registerReceiver(
                timeTickReceiver,
                IntentFilter("android.intent.action.TIME_TICK"),
                Context.RECEIVER_NOT_EXPORTED
            )
            timeTickRegistered = true
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    private fun unregisterTick(ctx: Context) {
        if (!timeTickRegistered) return
        timeTickRegistered = false
        val receiver = timeTickReceiver ?: return
        try {
            ctx.unregisterReceiver(receiver)
        } catch (ignored: Throwable) {
        }
    }
}
