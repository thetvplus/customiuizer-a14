package tv.withaibuild.customiuizer.mods.island

import android.content.ComponentName
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Process
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandFloatingPolicyTest {
    @Test fun exclusionTargetsTheFloatingSourceAndLeavesOtherMediaAndNotificationsAlone() {
        val session = MediaSession(RuntimeEnvironment.getApplication(), "floating-policy")
        val user = Process.myUserHandle()
        val music = IslandMediaContent(session.sessionToken, "music.app", "Track", null, null,
            true, 0, PlaybackState.STATE_PLAYING, user, 10001)
        val video = IslandPipSnapshot(1, 4, ComponentName("video.app", "Player"), user)
        fun blocked(content: IslandMediaContent? = music, notice: Boolean = false,
            packages: Set<String>? = emptySet(), pip: IslandPipSnapshot = video, overlay: Boolean = false,
            known: Boolean = true) = IslandFloatingPolicy.blocked(true, notice, content, user,
                known, packages?.isNotEmpty() == true, packages, pip, overlay)
        try {
            assertFalse(blocked())
            assertTrue(blocked(music.copy(packageName = "video.app")))
            assertFalse(blocked(packages = setOf("another.video")))
            assertTrue(blocked(packages = setOf("music.app")))
            assertTrue(blocked(overlay = true))
            assertFalse(blocked(notice = true, overlay = true))
            assertFalse(blocked(content = null, overlay = true))
            assertFalse(blocked(known = false, packages = null, pip = video.copy(component = null)))
            assertFalse(blocked(music.copy(packageName = "video.app"), pip = video.copy(displayId = 1)))
        } finally { session.release() }
    }
}
