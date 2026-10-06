package cl.retrolink.app;

import android.app.Activity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;

public final class HardwareButtonMapper {
    private HardwareButtonMapper() {}

    public static boolean handle(Activity activity, N64ControlBinder controls, KeyEvent event) {
        if (activity == null || controls == null || event == null) return false;
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if (!RetroPreferences.volumeButtonsEnabled(activity)) return false;

        String mapping = code == KeyEvent.KEYCODE_VOLUME_UP
                ? RetroPreferences.volumeUpMapping(activity)
                : RetroPreferences.volumeDownMapping(activity);
        int bit = RetroPreferences.mappingToMask(mapping);
        if (bit == 0) return RetroPreferences.blockSystemVolume(activity);

        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
            controls.setHardwareButton(bit, down);
            if (down && event.getRepeatCount() == 0 && RetroPreferences.hardwareHaptic(activity)) {
                View root = activity.getWindow().getDecorView();
                if (root != null) root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
        return RetroPreferences.blockSystemVolume(activity);
    }
}
