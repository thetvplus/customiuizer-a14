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
        fun blocked(content: IslandMediaContent? = music,
            packages: Set<String>? = emptySet(), pip: IslandPipSnapshot = video, overlay: Boolean = false,
            known: Boolean = true) = IslandFloatingPolicy.blocksMedia(true, content, user,
                known, packages?.isNotEmpty() == true, packages, pip, overlay)
        try {
            assertFalse(blocked())
            assertTrue(blocked(music.copy(packageName = "video.app")))
            assertFalse(blocked(packages = setOf("another.video")))
            assertTrue(blocked(packages = setOf("music.app")))
            assertTrue(blocked(overlay = true))
            assertFalse(blocked(content = null, overlay = true))
            assertFalse(blocked(known = false, packages = null, pip = video.copy(component = null)))
            assertFalse(blocked(music.copy(packageName = "video.app"), pip = video.copy(displayId = 1)))
        } finally { session.release() }
    }

    @Test fun pinnedOwnershipAndNativeWindowHandoffShareIdentityButKeepTheirDifferentVisibilityRules() {
        val session = MediaSession(RuntimeEnvironment.getApplication(), "pip-owner")
        val user = Process.myUserHandle()
        val media = IslandMediaContent(session.sessionToken, "video.app", "Video", null, null,
            true, 0, PlaybackState.STATE_PLAYING, user, 10001)
        try {
            val pip = IslandPipSnapshot(1, 4, ComponentName("video.app", "Player"), user, stashed = true)
            assertTrue(pip.belongsTo(media.packageName, user))
            assertFalse(pip.blocks(media))
            assertTrue(IslandFloatingPolicy.blocksMedia(true, media, user, true, false, emptySet(), pip, false))
            for (unrelated in listOf(pip.copy(displayId = 1), pip.copy(user = null), pip.copy(component = null),
                pip.copy(component = ComponentName("other.video", "Player")))) {
                assertFalse(unrelated.belongsTo(media.packageName, user))
                assertFalse(unrelated.blocks(media))
                assertFalse(IslandFloatingPolicy.blocksMedia(true, media, user, false, false, null, unrelated, false))
            }
        } finally { session.release() }
    }
}
