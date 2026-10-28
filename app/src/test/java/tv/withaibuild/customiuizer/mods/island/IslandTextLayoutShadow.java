package tv.withaibuild.customiuizer.mods.island;

import android.text.StaticLayout;
import android.text.TextPaint;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Records prepared paint identities while retaining Android 14's actual line layout. */
@Implements(value = StaticLayout.Builder.class, minSdk = 34)
public class IslandTextLayoutShadow {
    public static final Set<TextPaint> paints = Collections.newSetFromMap(new IdentityHashMap<>());
    public static int probes;

    @Resetter
    public static void reset() { paints.clear(); probes = 0; }

    @Implementation
    protected static StaticLayout.Builder obtain(CharSequence text, int start, int end,
            TextPaint paint, int width) {
        probes++;
        paints.add(paint);
        return Shadow.directlyOn(StaticLayout.Builder.class, "obtain",
                ClassParameter.from(CharSequence.class, text), ClassParameter.from(int.class, start),
                ClassParameter.from(int.class, end), ClassParameter.from(TextPaint.class, paint),
                ClassParameter.from(int.class, width));
    }
}
