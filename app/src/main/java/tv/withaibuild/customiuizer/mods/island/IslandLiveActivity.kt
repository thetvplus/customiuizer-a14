package tv.withaibuild.customiuizer.mods.island

import android.app.PendingIntent
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.utils.PrefMap

/** One type selector for native activity sources. This metadata is safe in settings. */
internal enum class IslandLiveKind(val value: String, val priority: Int, val label: Int) {
    CALL("call", 100, R.string.system_island_live_call),
    ALARM("alarm", 90, R.string.system_island_live_alarm),
    TIMER("timer", 80, R.string.system_island_live_timer),
    RECORDER("recorder", 70, R.string.system_island_live_recorder),
    STOPWATCH("stopwatch", 60, R.string.system_island_live_stopwatch),
    PRIVACY("privacy", 20, R.string.system_island_live_privacy);

    val signalPackage: String? get() = when {
        this in clockKinds -> CLOCK_PACKAGE
        this == RECORDER -> RECORDER_PACKAGE
        else -> null
    }
    val windowPackage: String? get() = if (this == CALL) CALL_PACKAGE else signalPackage

    companion object {
        const val PREFERENCE = "system_island_live_kinds"
        const val CLOCK_PACKAGE = "com.android.deskclock"
        const val RECORDER_PACKAGE = "com.android.soundrecorder"
        const val CALL_PACKAGE = "com.android.incallui"
        val clockKinds = setOf(TIMER, STOPWATCH, ALARM)
        fun read(prefs: PrefMap): Set<IslandLiveKind> {
            val values = prefs.getStringSet(PREFERENCE)
            return entries.filterTo(linkedSetOf()) { it.value in values }
        }
        fun sourcePackages(values: Collection<String>): Set<String> = buildSet {
            for (kind in entries) if (kind.value in values) kind.signalPackage?.let { add(it) }
        }
        fun windowPackages(kinds: Set<IslandLiveKind>): Set<String> =
            kinds.mapNotNullTo(linkedSetOf()) { it.windowPackage }
        fun canReturn(kind: IslandLiveKind, pkg: String) = kind.windowPackage == pkg
    }
}

/** All time is monotonic after capture. A paused clock has no ticking task. */
internal data class IslandLiveClock(val base: Long, val countdown: Boolean, val pausedMillis: Long? = null) {
    fun millis(now: Long): Long = pausedMillis ?: (if (countdown) base - now else now - base).coerceAtLeast(0)
}

internal data class IslandLiveKey(val kind: IslandLiveKind, val uid: Int, val session: Long = 0)

/** Minimal current state; no recording path, phone number, media sample or message history. */
internal data class IslandLiveActivity(val kind: IslandLiveKind, val packageName: String, val uid: Int,
    val clock: IslandLiveClock? = null, val open: PendingIntent? = null, val privacy: Int = 0,
    val session: Long = 0, val alert: Boolean = false, val meter: android.os.IBinder? = null) {
    val key = IslandLiveKey(kind, uid, session)
}

/** Fixed at one current snapshot per type. An explicit dismissal lasts until its source ends. */
internal class IslandLiveRegistry(private val dismissal: Dismissal = Dismissal()) {
    class Dismissal { internal val keys = HashSet<IslandLiveKey>() }
    private val current = java.util.EnumMap<IslandLiveKind, IslandLiveActivity>(IslandLiveKind::class.java)
    private var selected: IslandLiveKey? = null

    fun update(kind: IslandLiveKind, value: IslandLiveActivity?) {
        require(value == null || value.kind == kind)
        dismissal.keys.removeAll { it.kind == kind && it != value?.key }
        if (value == null) current.remove(kind) else current[kind] = value
    }

    fun select(kinds: Set<IslandLiveKind>, excluded: Set<String>, user: Int,
        privacyIsSecondary: Boolean = false, elapsed: Long = 0, foregroundPackage: String? = null): IslandLiveActivity? =
        choose(kinds, excluded, user, privacyIsSecondary, elapsed, foregroundPackage, null).also { selected = it?.key }

    /** The native return target remains available while its app owns the foreground UI.
     * Looking it up must not replace the selected background activity or undo dismissal. */
    fun forPackage(packageName: String?, kinds: Set<IslandLiveKind>, excluded: Set<String>, user: Int,
        elapsed: Long): IslandLiveActivity? = if (packageName == null) null
        else choose(kinds, excluded, user, false, elapsed, null, packageName)

    private fun choose(kinds: Set<IslandLiveKind>, excluded: Set<String>, user: Int,
        privacyIsSecondary: Boolean, elapsed: Long, foregroundPackage: String?, requiredPackage: String?): IslandLiveActivity? {
        var best: IslandLiveActivity? = null
        for (candidate in current.values) {
            if (candidate.kind !in kinds || candidate.packageName in excluded || candidate.uid / 100000 != user ||
                candidate.packageName == foregroundPackage || requiredPackage != null && candidate.packageName != requiredPackage ||
                candidate.key in dismissal.keys || privacyIsSecondary && candidate.kind == IslandLiveKind.PRIVACY ||
                candidate.clock?.let { it.countdown && it.pausedMillis == null && elapsed - it.base >= 3000 } == true) continue
            val old = best
            if (old == null || candidate.kind.priority > old.kind.priority ||
                candidate.kind.priority == old.kind.priority && candidate.key == selected) best = candidate
        }
        return best
    }

    fun dismiss(key: IslandLiveKey) { if (current[key.kind]?.key == key) dismissal.keys.add(key) }
    fun privacy(user: Int) = current[IslandLiveKind.PRIVACY]?.takeIf { it.uid / 100000 == user }?.privacy ?: 0
    fun clear(preserveDismissal: Boolean = false) {
        current.clear(); selected = null
        if (!preserveDismissal) dismissal.keys.clear()
    }
}

internal object IslandLiveLabels {
    fun time(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0) / 1000)
        return if (seconds >= 3600) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }
}

/** Read-only adapters translate native wall-clock models once, at their event boundary. */
internal object IslandLiveTime {
    const val MAX_SPAN = 366L * 24 * 60 * 60 * 1000
    fun deadline(wall: Long, nowWall: Long, nowElapsed: Long): IslandLiveClock? {
        val remaining = difference(wall, nowWall) ?: return null
        if (remaining !in 0..MAX_SPAN || nowElapsed < 0 || nowElapsed > Long.MAX_VALUE - remaining) return null
        return IslandLiveClock(nowElapsed + remaining, true)
    }
    fun elapsed(duration: Long, nowElapsed: Long, paused: Boolean = false): IslandLiveClock? {
        if (duration !in 0..MAX_SPAN || nowElapsed < 0) return null
        return IslandLiveClock(nowElapsed - duration, false, if (paused) duration else null)
    }
    fun difference(value: Long, base: Long): Long? = try { Math.subtractExact(value, base) }
        catch (_: ArithmeticException) { null }
}
