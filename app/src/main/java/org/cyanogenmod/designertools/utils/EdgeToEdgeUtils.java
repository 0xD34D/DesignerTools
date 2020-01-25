package org.cyanogenmod.designertools.utils;

import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;

public class EdgeToEdgeUtils {

    public static void addEdgeToEdgeFlags(@NonNull View view) {
        final int currentViewFlags = view.getSystemUiVisibility();
        int newFlags = currentViewFlags | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            newFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        view.setSystemUiVisibility(newFlags);
    }

    public static void requestAndApplyInsets(@NonNull View view, @NonNull OnApplyWindowInsetsListener listener) {
        if (view.isAttachedToWindow()) {
            view.requestApplyInsets();
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    v.requestApplyInsets();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    // No op
                }
            });
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, listener);
    }
}
