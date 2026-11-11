package tv.withaibuild.customiuizer.mods.media

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaCardAppearanceTest {
    private class Fixture {
        val root=FrameLayout(RuntimeEnvironment.getApplication())
        val art=ImageView(root.context).apply { id=1;scaleType=ImageView.ScaleType.FIT_CENTER;setPadding(4,5,6,7);foreground=ColorDrawable(Color.RED) }
        val title=TextView(root.context).apply { id=2;text="A long title";textSize=20f;gravity=Gravity.CENTER;typeface=Typeface.MONOSPACE }
        val artist=TextView(root.context).apply { id=3;text="Artist";textSize=18f }
        val elapsed=TextView(root.context).apply { id=4;text="1:02:30";textSize=15f }
        val total=TextView(root.context).apply { id=5;text="2:45:00";textSize=15f }
        val seek=SeekBar(root.context).apply { id=6;max=300;progress=80;isEnabled=true }
        val button=ImageButton(root.context).apply { id=7;setImageDrawable(ColorDrawable(Color.BLUE));contentDescription="Next chapter" }
        val ids=mapOf("album_art" to 1,"header_title" to 2,"header_artist" to 3,"media_elapsed_time" to 4,
            "media_total_time" to 5,"media_progress_bar" to 6,"actionNext" to 7)
        init { for(view in listOf(art,title,artist,elapsed,total,seek,button)) root.addView(view) }
    }
    @Test fun artworkUsesItsFullBoundsAndTypographyKeepsNativeContentAndThemeColors() {
        val f=Fixture();val originalArt=f.art.foreground
        val originalFace=f.title.typeface;val originalSize=f.title.textSize;val originalGravity=f.title.gravity
        val themeColors=ColorStateList.valueOf(Color.rgb(30,40,50));f.artist.setTextColor(themeColors)
        val appearance=MediaCardAppearance(f.root,f.ids)
        appearance.apply()
        assertEquals(ImageView.ScaleType.CENTER_CROP,f.art.scaleType)
        assertEquals(0,f.art.paddingLeft);assertEquals(0,f.art.paddingBottom);assertNull(f.art.foreground)
        assertTrue(f.title.textSize>f.artist.textSize);assertTrue(f.artist.textSize>f.elapsed.textSize)
        assertFalse(f.title.includeFontPadding);assertEquals("tnum",f.elapsed.fontFeatureSettings)
        assertSame(themeColors,f.artist.textColors)
        assertEquals("A long title",f.title.text.toString());assertEquals("1:02:30",f.elapsed.text.toString())
        appearance.restore()
        assertSame(originalArt,f.art.foreground);assertEquals(ImageView.ScaleType.FIT_CENTER,f.art.scaleType)
        assertEquals(4,f.art.paddingLeft);assertEquals(7,f.art.paddingBottom)
        assertSame(originalFace,f.title.typeface);assertEquals(originalSize,f.title.textSize,0f);assertEquals(originalGravity,f.title.gravity)
    }
    @Test fun nativeSeekStateListenersAndTintRemainAuthoritativeAndNoStableRebuildOccurs() {
        val f=Fixture();val nativeTrack=f.seek.progressDrawable;val nativeThumb=f.seek.thumb
        var changed=0
        f.seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar,progress: Int,fromUser: Boolean) { changed++ }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
        f.seek.thumb.alpha=0 // Native unavailable/hidden thumb state must survive styling.
        val appearance=MediaCardAppearance(f.root,f.ids);appearance.apply()
        val track=f.seek.progressDrawable;val thumb=f.seek.thumb
        assertEquals(0,thumb.alpha);assertEquals(80,f.seek.progress);assertEquals(300,f.seek.max);assertTrue(f.seek.isEnabled)
        val tint=ColorStateList.valueOf(Color.BLACK)
        f.seek.progressTintList=tint;f.seek.thumbTintList=tint
        repeat(100) { appearance.apply();assertSame(track,f.seek.progressDrawable);assertSame(thumb,f.seek.thumb) }
        f.seek.progress=160;assertEquals(1,changed)
        thumb.alpha=255 // A later native observer made seeking available.
        appearance.restore();assertSame(nativeTrack,f.seek.progressDrawable);assertSame(nativeThumb,f.seek.thumb)
        assertEquals(255,f.seek.thumb.alpha)
        assertEquals(160,f.seek.progress);assertSame(tint,f.seek.progressTintList)
    }
    @Test fun onlyKnownStandardActionsReceiveGlyphsAndNativeCallbacksAndNewBindingsSurvive() {
        val f=Fixture();val original=f.button.drawable;val appearance=MediaCardAppearance(f.root,f.ids)
        var clicks=0;f.button.setOnClickListener { clicks++ }
        appearance.bind(f.button,32L)
        val glyph=f.button.drawable;assertNotSame(original,glyph)
        assertEquals("Next chapter",f.button.contentDescription);f.button.performClick();assertEquals(1,clicks)
        appearance.bind(f.button,16L);assertSame(glyph,f.button.drawable)
        appearance.bind(f.button,4L);assertSame(glyph,f.button.drawable)
        appearance.bind(f.button,2L);assertSame(glyph,f.button.drawable)
        appearance.bind(f.button,0L);assertSame(original,f.button.drawable)
        val custom=ColorDrawable(Color.GREEN);f.button.setImageDrawable(custom);f.button.isEnabled=false
        appearance.bind(f.button,0L);assertSame(custom,f.button.drawable);assertFalse(f.button.isEnabled)
        appearance.bind(f.button,32L);assertSame(glyph,f.button.drawable)
        val replacement=ColorDrawable(Color.YELLOW);f.button.setImageDrawable(replacement)
        appearance.restore();assertSame(replacement,f.button.drawable)
    }
    @Test fun repeatedTranslucentGlyphDrawsKeepTheSameTintAndAlpha() {
        val f=Fixture();val appearance=MediaCardAppearance(f.root,f.ids)
        appearance.bind(f.button,2L)
        val glyph=f.button.drawable
        glyph.setTintList(ColorStateList.valueOf(0x80ff0000.toInt()));glyph.alpha=128;glyph.setBounds(0,0,48,48)
        val bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        glyph.draw(canvas);val first=IntArray(48*48);bitmap.getPixels(first,0,48,0,0,48,48)
        repeat(4) { bitmap.eraseColor(Color.TRANSPARENT);glyph.draw(canvas) }
        val after=IntArray(48*48);bitmap.getPixels(after,0,48,0,0,48,48)
        assertArrayEquals(first,after);assertTrue(first.any { Color.alpha(it)>0 })
        appearance.restore();bitmap.recycle()
    }
    @Test fun nativeReplacementProgressAndTypographyAreKeptOnRelease() {
        val f=Fixture();val appearance=MediaCardAppearance(f.root,f.ids);appearance.apply()
        val replacementTrack=ColorDrawable(Color.CYAN);val replacementThumb=ColorDrawable(Color.BLUE)
        f.seek.progressDrawable=replacementTrack;f.seek.thumb=replacementThumb
        f.title.typeface=Typeface.SERIF;f.title.textSize=19f
        val nativeSize=f.title.textSize
        appearance.restore()
        assertSame(replacementTrack,f.seek.progressDrawable);assertSame(replacementThumb,f.seek.thumb)
        assertSame(Typeface.SERIF,f.title.typeface);assertEquals(nativeSize,f.title.textSize,0f)
    }
}
