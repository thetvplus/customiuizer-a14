package tv.withaibuild.customiuizer.mods.island

import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.os.Process
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [NativePlaybackInfoBoundaryTest.NullPlaybackInfo::class])
class NativePlaybackInfoBoundaryTest {
    @Implements(MediaController::class)
    class NullPlaybackInfo {
        @Implementation fun getPlaybackInfo(): MediaController.PlaybackInfo? = null
    }

    @Test fun binderNullIsAnUnavailableSessionInsteadOfAnException() {
        val context = RuntimeEnvironment.getApplication()
        val session = MediaSession(context, "synthetic.playback")
        try {
            ReflectionHelpers.setField(session.sessionToken, "mUid", Process.myUid())
            val player = MediaController(context, session.sessionToken)
            assertEquals(Process.myUid(), IslandMediaIdentity.uid(player))
            assertNull(IslandAudioSpectrum.NativeBackend(context, Handler(Looper.getMainLooper()))
                .session(player, Process.myUserHandle()))
        } finally { session.release() }
    }
}
