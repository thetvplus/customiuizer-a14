package tv.withaibuild.customiuizer.mods.recents;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import java.util.function.Consumer;

/** The verified native spring invokes this framework callback with null. Java
 * preserves that ROM contract without Kotlin's non-null parameter assertion. */
final class DeckSpringEndListener extends AnimatorListenerAdapter {
    private final Consumer<Boolean> end;
    private final Runnable start;

    DeckSpringEndListener(Runnable start, Consumer<Boolean> end) { this.start = start; this.end = end; }

    @Override public void onAnimationStart(Animator animation) { start.run(); }
    @Override public void onAnimationCancel(Animator animation) { end.accept(true); }
    @Override public void onAnimationEnd(Animator animation) { end.accept(false); }
}
