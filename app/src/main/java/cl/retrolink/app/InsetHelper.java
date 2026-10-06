package cl.retrolink.app;

import android.view.View;
import android.view.WindowInsets;

public final class InsetHelper {
    private InsetHelper() {}
    public static void apply(View root) {
        if (root == null) return;
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l = insets.getSystemWindowInsetLeft();
            int t = insets.getSystemWindowInsetTop();
            int r = insets.getSystemWindowInsetRight();
            int b = insets.getSystemWindowInsetBottom();
            v.setPadding(l, t, r, b);
            return insets;
        });
        root.requestApplyInsets();
    }
}
