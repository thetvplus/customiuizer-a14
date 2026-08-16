package tv.withaibuild.customiuizer.mods.battery.testfixtures;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Minimal test double for {@link TextView} that records the values written to it.
 */
public class FakeTextView extends TextView {

    private final TextPaint fakePaint = new TextPaint();
    private Typeface storedTypeface;
    private float storedTextSize;
    private int storedPaddingStart;
    private int storedPaddingTop;
    private int storedPaddingEnd;
    private int storedPaddingBottom;
    private FakeResources fakeResources;

    public FakeTextView() {
        super((Context) null);
        fakeResources = new FakeResources();
    }

    public FakeTextView(Context context) {
        super(context);
    }

    public FakeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public Typeface getTypeface() {
        return storedTypeface;
    }

    @Override
    public void setTypeface(Typeface tf) {
        storedTypeface = tf;
    }

    @Override
    public float getTextSize() {
        return storedTextSize;
    }

    @Override
    public void setTextSize(float size) {
        storedTextSize = size;
    }

    @Override
    public void setTextSize(int unit, float size) {
        if (unit == TypedValue.COMPLEX_UNIT_DIP) {
            // Fake a density of 2.0f, matching BatteryViewStateTest expectations.
            storedTextSize = size * 2.0f;
        } else {
            storedTextSize = size;
        }
    }

    @Override
    public int getPaddingStart() {
        return storedPaddingStart;
    }

    @Override
    public int getPaddingTop() {
        return storedPaddingTop;
    }

    @Override
    public int getPaddingEnd() {
        return storedPaddingEnd;
    }

    @Override
    public int getPaddingBottom() {
        return storedPaddingBottom;
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        storedPaddingStart = start;
        storedPaddingTop = top;
        storedPaddingEnd = end;
        storedPaddingBottom = bottom;
    }

    @Override
    public TextPaint getPaint() {
        return fakePaint;
    }

    @Override
    public Resources getResources() {
        return fakeResources;
    }
}
