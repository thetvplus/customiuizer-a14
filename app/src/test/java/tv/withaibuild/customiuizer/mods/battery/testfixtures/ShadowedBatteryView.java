package tv.withaibuild.customiuizer.mods.battery.testfixtures;

import android.widget.TextView;

/**
 * Subclass that shadows the {@code mBatteryPercentView} field to prove runtime-owner precedence.
 */
public class ShadowedBatteryView extends BaseBatteryView {

    public TextView mBatteryPercentView;
}
