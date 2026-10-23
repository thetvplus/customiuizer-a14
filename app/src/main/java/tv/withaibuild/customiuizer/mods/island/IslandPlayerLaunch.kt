package tv.withaibuild.customiuizer.mods.island

import android.app.PendingIntent
import android.content.IntentSender
import android.media.session.MediaController
import android.os.UserHandle
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** One selected-session transaction. A failed send may fall back; a successful send never does. */
internal object IslandPlayerLaunch {
    fun interface Entry { fun send() }
    enum class Result { STARTED, STALE, UNAVAILABLE, BLOCKED }

    fun open(current: () -> Boolean, primary: () -> Entry?, fallback: () -> Entry?): Result {
        if (!current()) return Result.STALE
        try {
            val entry = primary()
            if (!current()) return Result.STALE
            if (entry != null) {
                try { entry.send(); return Result.STARTED }
                catch (_: PendingIntent.CanceledException) { /* Only a definitely failed send may fall back. */ }
            }
            if (!current()) return Result.STALE
            val alternative = fallback() ?: return Result.UNAVAILABLE
            if (!current()) return Result.STALE
            alternative.send()
            return Result.STARTED
        } catch (_: SecurityException) {
            return Result.BLOCKED
        } catch (_: IntentSender.SendIntentException) {
            return Result.UNAVAILABLE
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            if (error is kotlinx.coroutines.CancellationException) throw error
            return Result.UNAVAILABLE
        }
    }
}

/** A14 token UID is authoritative; package lookups alone cannot distinguish work profiles. */
internal object IslandMediaIdentity {
    fun uid(controller: MediaController): Int? = try {
        (XposedHelpers.callMethod(controller.sessionToken, "getUid") as? Int)?.takeIf { it >= 0 }
    } catch (error: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        null
    }

    fun sameUser(uid: Int, user: UserHandle?) = user != null && UserHandle.getUserHandleForUid(uid) == user
}

/** No identifiers, audio samples or exception payloads. At most one line per reason per owner. */
internal class IslandDiagnostics {
    private val emitted = HashSet<String>()
    @Synchronized fun report(reason: String) {
        if (emitted.size < 8 && emitted.add(reason)) XposedHelpers.log("Dynamic Island: $reason")
    }
}
