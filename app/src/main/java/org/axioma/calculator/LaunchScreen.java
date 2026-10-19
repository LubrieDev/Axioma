package org.axioma.calculator;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

/** A brief branded launch using the platform starting window, with no extra activity. */
final class LaunchScreen {
    private static final long DISPLAY_MILLIS = 500;
    private LaunchScreen() {}

    static void install(Activity activity, Bundle state) {
        if (state != null) return;
        long deadline = SystemClock.uptimeMillis() + DISPLAY_MILLIS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.getSplashScreen().setOnExitAnimationListener(splash -> {
                long remaining = Math.max(0, deadline - SystemClock.uptimeMillis());
                splash.postDelayed(splash::remove, remaining);
            });
        } else {
            View decor = activity.getWindow().getDecorView();
            decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    if (SystemClock.uptimeMillis() < deadline) {
                        decor.postInvalidateOnAnimation();
                        return false;
                    }
                    decor.getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            });
        }
    }
}
