package tv.withaibuild.customiuizer.mods.battery.testfixtures;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

/**
 * Test double for {@link Resources} that exposes a fixed density.
 */
public class FakeResources extends Resources {

    private final DisplayMetrics metrics;

    public FakeResources() {
        this(2.0f);
    }

    @SuppressWarnings("deprecation")
    public FakeResources(float density) {
        super(null, new DisplayMetrics(), new Configuration());
        metrics = new DisplayMetrics();
        metrics.density = density;
    }

    @Override
    public DisplayMetrics getDisplayMetrics() {
        return metrics;
    }
}
