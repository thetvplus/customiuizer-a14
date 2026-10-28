package tv.withaibuild.customiuizer.mods.island

import android.media.session.PlaybackState
import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class IslandMusicActionsTest {
    @Test fun defaultsAndTrackSwitchHaveOneSharedResolutionRule() {
        val prefs = PrefMap()
        val defaults = IslandMusicActions.read(prefs)
        assertTrue(IslandOptions.read(prefs).mediaSwipe)
        assertEquals(listOf(87, 88, 1, 1, 1), IslandMusicGesture.entries.map { defaults.resolve(it, true) })
        assertEquals(1, defaults.resolve(IslandMusicGesture.LEFT, false))
        assertEquals(1, defaults.resolve(IslandMusicGesture.RIGHT, false))
        val remapped = defaults.copy(left = 18, right = 85, down = 87)
        assertEquals(18, remapped.resolve(IslandMusicGesture.LEFT, false))
        assertEquals(85, remapped.resolve(IslandMusicGesture.RIGHT, false))
        assertEquals(87, remapped.resolve(IslandMusicGesture.DOWN, false))
        for (gesture in IslandMusicGesture.entries) prefs.put(gesture.key + "_action", 1)
        assertEquals(List(5) { 1 }, IslandMusicGesture.entries.map { IslandMusicActions.read(prefs).resolve(it, true) })
    }

    @Test fun importsRetainPayloadsAndRejectActionsOutsideTheMusicMatrix() {
        val source = PrefMap().apply {
            put("system_island_music_left_action", 8)
            put("system_island_music_left_app", "music.app|Music")
            put("system_island_music_left_app_user", 0)
        }
        val restored = PrefMap().apply { replaceSnapshot(source.getAll()) }
        assertEquals(8, IslandMusicActions.read(restored).left)
        assertEquals("music.app|Music", restored.getString("system_island_music_left_app", ""))
        for (unsafe in listOf(4, 5, 12, 13, 24, 126, 127, Int.MAX_VALUE)) {
            restored.put("system_island_music_left_action", unsafe)
            assertEquals(1, IslandMusicActions.read(restored).left)
        }
    }

    @Test fun transportUsesRealStateAndTheSelectedSessionsActionBits() {
        assertEquals(PlaybackState.ACTION_PAUSE, IslandTransportAction.resolve(85, PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE))
        assertEquals(PlaybackState.ACTION_PLAY, IslandTransportAction.resolve(85, PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY_PAUSE))
        assertNull(IslandTransportAction.resolve(85, PlaybackState.STATE_BUFFERING, PlaybackState.ACTION_PLAY_PAUSE))
        assertNull(IslandTransportAction.resolve(87, PlaybackState.STATE_PLAYING, PlaybackState.ACTION_SKIP_TO_PREVIOUS))
        assertNull(IslandTransportAction.resolve(30, PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PAUSE))
        assertEquals(PlaybackState.ACTION_PLAY, IslandTransportAction.resolve(30, PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY))
        assertEquals(PlaybackState.ACTION_PAUSE, IslandTransportAction.resolve(31, PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE))
    }

    @Test fun positioningNeverPlacesTextOverTheCameraOrCrossesInsets() {
        assertEquals(12, IslandPosition.left(184, 12, 388, 0, 0, 40))
        assertEquals(108, IslandPosition.left(184, 12, 388, 1, 0, 40))
        assertEquals(204, IslandPosition.left(184, 12, 388, 2, 0, 40))
        assertEquals(204, IslandPosition.left(184, 12, 388, 1, Int.MAX_VALUE, 40))
        assertNull(IslandPosition.left(184, 12, 388, 0, 0, 40, 192, 208, true))
        assertNull(IslandPosition.left(500, 12, 388, 0, 0, 40))
        for (density in listOf(.75f, 1f, 2.75f, 4f)) for (height in listOf(38, 40, 44)) {
            val auto = IslandSizing.collapsed(density, (400*density).toInt(), (300*density).toInt(), (40*density).toInt(), heightDp = height)
            assertTrue("Camera plus two 24dp contents and their 16dp margins must fit", auto >= (120*density).toInt())
            assertTrue(auto <= (280*density).toInt())
            assertEquals((184*density).toInt(), IslandSizing.collapsed(density, 2000, 2000, 40, manual=true, heightDp=height))
        }
    }

    @Test fun localBluetoothAndRemoteVolumeRespectTheirOwnCapabilities() {
        val local=android.media.session.MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL
        val remote=android.media.session.MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
        val media=android.media.AudioAttributes.USAGE_MEDIA
        val relative=android.media.VolumeProvider.VOLUME_CONTROL_RELATIVE
        val absolute=android.media.VolumeProvider.VOLUME_CONTROL_ABSOLUTE
        val fixed=android.media.VolumeProvider.VOLUME_CONTROL_FIXED
        // Bluetooth media remains a local MediaSession route; the controller chooses its actual device.
        assertTrue(IslandVolumePolicy.adjustable(local,absolute,media,false))
        assertFalse(IslandVolumePolicy.adjustable(local,absolute,media,true))
        assertFalse(IslandVolumePolicy.adjustable(local,absolute,android.media.AudioAttributes.USAGE_ALARM,false))
        assertTrue(IslandVolumePolicy.adjustable(remote,relative,media,true))
        assertTrue(IslandVolumePolicy.adjustable(remote,absolute,media,false))
        assertFalse(IslandVolumePolicy.adjustable(remote,fixed,media,false))
        assertFalse(IslandVolumePolicy.adjustable(-1,absolute,media,false))
        assertFalse(IslandVolumePolicy.adjustable(remote,99,media,false))
    }

    @Test fun importedHorizontalOffsetsCannotOverflowBeforeClamping() {
        val prefs=PrefMap()
        assertEquals(0,IslandOptions.read(prefs).horizontalOffset)
        prefs.put("system_island_horizontal_offset",Int.MIN_VALUE)
        assertEquals(-80,IslandOptions.read(prefs).horizontalOffset)
        prefs.put("system_island_horizontal_offset",Int.MAX_VALUE)
        assertEquals(80,IslandOptions.read(prefs).horizontalOffset)
    }
}
