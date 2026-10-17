package tv.withaibuild.customiuizer.mods.battery.testfixtures;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Base test double for the battery meter view. It declares the three exact child-view field
 * names expected by the production code.
 */
public class BaseBatteryView extends LinearLayout {

    public TextView mBatteryTextDigitView;
    public TextView mBatteryPercentView;
    public TextView mBatteryPercentMarkView;

    private final List<View> backingChildren = new ArrayList<>();
    public int mutationCount = 0;

    public BaseBatteryView() {
        super((Context) null);
    }

    private final Resources fakeResources = new FakeResources();

    @Override
    public Resources getResources() {
        return fakeResources;
    }

    @Override
    public int getChildCount() {
        return backingChildren.size();
    }

    @Override
    public View getChildAt(int index) {
        return backingChildren.get(index);
    }

    @Override
    public int indexOfChild(View child) {
        return backingChildren.indexOf(child);
    }

    @Override
    public void addView(View child) {
        addView(child, -1);
    }

    @Override
    public void addView(View child, int index) {
        if (child == null) return;
        mutationCount++;
        if (index < 0 || index > backingChildren.size()) {
            backingChildren.add(child);
        } else {
            backingChildren.add(index, child);
        }
    }

    @Override
    public void removeView(View child) {
        if (child == null) return;
        mutationCount++;
        backingChildren.remove(child);
    }

    public void resetMutationCount() {
        mutationCount = 0;
    }

    public void setupChildrenInOemOrder() {
        mBatteryTextDigitView = new FakeTextView();
        mBatteryPercentView = new FakeTextView();
        mBatteryPercentMarkView = new FakeTextView();

        mBatteryTextDigitView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 7.5f);
        mBatteryPercentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 7.5f);
        mBatteryPercentMarkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 7.5f);

        mBatteryPercentView.setPaddingRelative(4, 0, 0, 0);
        mBatteryPercentMarkView.setPaddingRelative(0, 2, 0, 0);

        addView(mBatteryTextDigitView);
        addView(mBatteryPercentView);
        addView(mBatteryPercentMarkView);
    }
}
