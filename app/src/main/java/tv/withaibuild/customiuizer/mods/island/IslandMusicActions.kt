package tv.withaibuild.customiuizer.mods.island

import android.media.session.PlaybackState
import tv.withaibuild.customiuizer.utils.PrefMap

/** Persisted action IDs extend the existing matrix; they are not KeyEvent codes. */
internal enum class IslandMusicGesture(val suffix: String, val defaultAction: Int) {
    LEFT("left", 87), RIGHT("right", 88), DOWN("down", 1), DOUBLE_TAP("double", 1), LONG_PRESS("long", 1);

    val key: String get() = "system_island_music_$suffix"
    val horizontal: Boolean get() = this == LEFT || this == RIGHT
}

internal data class IslandMusicActions(
    val left: Int = 87, val right: Int = 88, val down: Int = 1,
    val doubleTap: Int = 1, val longPress: Int = 1,
) {
    fun resolve(gesture: IslandMusicGesture, trackSwipes: Boolean): Int {
        val action = when (gesture) {
            IslandMusicGesture.LEFT -> left
            IslandMusicGesture.RIGHT -> right
            IslandMusicGesture.DOWN -> down
            IslandMusicGesture.DOUBLE_TAP -> doubleTap
            IslandMusicGesture.LONG_PRESS -> longPress
        }
        return if (supported(action) && (trackSwipes || !gesture.horizontal || action != 87 && action != 88)) action else 1
    }

    companion object {
        const val PLAY = 30
        const val PAUSE = 31
        const val OPEN_PLAYER = 32
        const val PIP_HIDE = 33
        const val PIP_STASH = 34
        fun supported(action: Int): Boolean = action in 1..3 || action in 8..9 || action in 17..20 ||
            action in PLAY..PIP_STASH || action == 85 || action in 87..88

        fun read(prefs: PrefMap): IslandMusicActions {
            fun action(gesture: IslandMusicGesture) = prefs.getInt(gesture.key + "_action", gesture.defaultAction)
                .takeIf(::supported) ?: 1
            return IslandMusicActions(action(IslandMusicGesture.LEFT), action(IslandMusicGesture.RIGHT),
                action(IslandMusicGesture.DOWN), action(IslandMusicGesture.DOUBLE_TAP), action(IslandMusicGesture.LONG_PRESS))
        }
    }
}

/** Resolve play/pause from the current snapshot, and require that exact session's capability. */
internal object IslandTransportAction {
    fun resolve(action: Int, state: Int, capabilities: Long): Long? {
        val request = when (action) {
            IslandMusicActions.PLAY -> PlaybackState.ACTION_PLAY
            IslandMusicActions.PAUSE -> PlaybackState.ACTION_PAUSE
            85 -> when (state) {
                PlaybackState.STATE_PLAYING -> PlaybackState.ACTION_PAUSE
                PlaybackState.STATE_PAUSED -> PlaybackState.ACTION_PLAY
                else -> return null
            }
            87 -> PlaybackState.ACTION_SKIP_TO_NEXT
            88 -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
            else -> return null
        }
        val supported = capabilities and request != 0L ||
            action == 85 && capabilities and PlaybackState.ACTION_PLAY_PAUSE != 0L
        return request.takeIf { supported }
    }
}

internal object IslandVolumePolicy {
    fun adjustable(type: Int, control: Int, usage: Int, deviceFixed: Boolean): Boolean =
        (control == android.media.VolumeProvider.VOLUME_CONTROL_RELATIVE ||
            control == android.media.VolumeProvider.VOLUME_CONTROL_ABSOLUTE) && when (type) {
            android.media.session.MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL ->
                !deviceFixed && usage == android.media.AudioAttributes.USAGE_MEDIA
            android.media.session.MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE -> true
            else -> false
        }
}
