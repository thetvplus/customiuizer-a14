package tv.withaibuild.customiuizer.mods.island

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

internal data class IslandPipCandidate(val packageName: String, val user: UserHandle, val taskId: Int,
    val alreadyPinned: Boolean = false)

internal fun interface IslandPipCandidatePort {
    suspend fun candidate(packageName: String, user: UserHandle): IslandPipCandidate?
}

/** One cold, privileged query on an actual Home edge, never from a draw callback. */
@SuppressLint("MissingPermission") // Runs only in privileged SystemUI; the settings process never calls it.
internal class IslandPipCandidateAccess(private val tasks: () -> List<ActivityManager.RunningTaskInfo>) : IslandPipCandidatePort {
    constructor(context: Context) : this(tasks = context.applicationContext.getSystemService(ActivityManager::class.java)
        ?.let { manager -> { manager.getRunningTasks(16) } } ?: { emptyList() })
    private val userId = XposedHelpers.findField(ActivityManager.RunningTaskInfo::class.java, "userId")
    private val displayId = XposedHelpers.findField(ActivityManager.RunningTaskInfo::class.java, "displayId")
    private val parameters = XposedHelpers.findField(ActivityManager.RunningTaskInfo::class.java, "pictureInPictureParams")
    private val mode = ActivityManager.RunningTaskInfo::class.java.getMethod("getWindowingMode")

    override suspend fun candidate(packageName: String, user: UserHandle): IslandPipCandidate? {
        for (task in tasks().take(16)) {
            if (task.topActivity?.packageName != packageName || displayId.getInt(task) != 0) continue
            val id = userId.getInt(task)
            if (id !in 0..10000 || UserHandle.getUserHandleForUid(id * 100000) != user) continue
            val windowingMode = mode.invoke(task) as Int
            if (task.taskId < 0 || windowingMode !in 1..2) return null
            val pip = parameters.get(task) as? PictureInPictureParams ?: return null
            return if (pip.isAutoEnterEnabled) IslandPipCandidate(packageName, user, task.taskId, windowingMode == 2) else null
        }
        return null
    }
}

/** Capacity one Home intent, one serial query and one expiry, all owned by the island controller. */
internal class IslandPipHomeGate(private val scope: CoroutineScope, private val dispatcher: CoroutineDispatcher,
    private val probe: IslandPipCandidatePort, private val changed: (Update) -> Unit) : AutoCloseable {
    companion object { const val TIMEOUT = 2500L }
    data class Source(val packageName: String, val user: UserHandle)
    data class Update(val source: Source, val confirmed: Boolean, val refreshMedia: Boolean, val renderNow: Boolean)
    private val handler = Handler(Looper.getMainLooper())
    private val mutex = Mutex()
    private var generation = 0L
    private var job: Job? = null
    private var deadline = 0L
    private var closed = false
    private var reported = false
    private var candidate: IslandPipCandidate? = null
    private var nativeSeen = false
    private var startedGeneration = 0L
    private var latest = IslandPipSnapshot(0, 0)
    var source: Source? = null
        private set
    val confirmed get() = candidate != null
    private val expiry = Runnable {
        ModuleHelper.guarded {
            if (source != null && SystemClock.uptimeMillis() >= deadline) finish(refresh = true, render = true)
        }
    }

    fun matches(media: IslandMediaContent?) = source?.let { it.packageName == media?.packageName && it.user == media?.user } == true

    fun begin(packageName: String, user: UserHandle) {
        if (closed) return
        cancel()
        val current = Source(packageName, user)
        val epoch = generation
        source = current
        startedGeneration = latest.generation
        deadline = SystemClock.uptimeMillis() + TIMEOUT
        handler.postDelayed(expiry, TIMEOUT)
        job = scope.launch {
            val result = try {
                withContext(dispatcher) { mutex.withLock { ensureActive(); probe.candidate(packageName, user) } }
            } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                if (error is CancellationException) throw error
                if (!reported) { reported = true; XposedHelpers.log("Dynamic Island auto-PiP readiness", error) }
                null
            }
            if (closed || epoch != generation || source != current) return@launch
            job = null
            if (result == null || result.packageName != packageName || result.user != user || result.taskId < 0) {
                finish(refresh = false, render = true)
            } else {
                candidate = result
                if (ready(latest)) finish(refresh = true, render = true)
                else changed(Update(current, confirmed = true, refreshMedia = false, renderNow = true))
            }
        }
    }

    private fun ready(snapshot: IslandPipSnapshot): Boolean {
        val task = candidate ?: return false
        return snapshot.state == 4 && snapshot.displayId == 0 && snapshot.taskId == task.taskId &&
            snapshot.component?.packageName == task.packageName && snapshot.user == task.user &&
            (task.alreadyPinned || snapshot.generation > startedGeneration)
    }

    fun nativeChanged(snapshot: IslandPipSnapshot, renderNow: Boolean = true) {
        if (snapshot.generation < latest.generation) return
        latest = snapshot
        if (source == null || !confirmed) return
        if (ready(snapshot)) { finish(refresh = true, render = renderNow); return }
        if (snapshot.generation > startedGeneration && snapshot.displayId == 0) {
            val task = candidate ?: return
            val sameTask = (snapshot.component == null || snapshot.component.packageName == task.packageName) &&
                (snapshot.user == null || snapshot.user == task.user) && (snapshot.taskId == null || snapshot.taskId == task.taskId)
            if ((snapshot.pendingEntry || snapshot.state in 1..5) && sameTask) nativeSeen = true
            else if (snapshot.state == 0 && !snapshot.pendingEntry && nativeSeen)
                finish(refresh = true, render = renderNow)
        }
    }

    private fun finish(refresh: Boolean, render: Boolean) {
        val old = source ?: return
        val wasConfirmed = confirmed
        cancel()
        changed(Update(old, wasConfirmed, refresh, render))
    }

    fun cancel() {
        generation++
        job?.cancel(); job = null
        handler.removeCallbacks(expiry)
        source = null; candidate = null; nativeSeen = false; deadline = 0L
    }

    override fun close() { closed = true; cancel() }
}
