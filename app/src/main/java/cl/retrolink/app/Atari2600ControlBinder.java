package cl.retrolink.app;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import cl.retrolink.app.ble.BleProtocol;

/**
 * Joystick Atari 2600.
 * Fire -> libretro B; SELECT -> Z/SELECT; RESET -> START.
 */
public final class Atari2600ControlBinder {
    public interface Listener { void onInput(int mask, int axisX, int axisY); }

    private final Listener listener;
    private int touchMask, hardwareMask;
    private int touchAxisX, touchAxisY;
    private int hardwareAxisX, hardwareAxisY;

    public Atari2600ControlBinder(android.app.Activity activity, Listener listener) {
        this.listener = listener;
        bind(activity, R.id.btnFire, BleProtocol.B);
        bind(activity, R.id.btnSelect, BleProtocol.Z);
        bind(activity, R.id.btnReset, BleProtocol.START);

        View stick = activity.findViewById(R.id.virtualStick);
        if (stick instanceof VirtualStickView) {
            ((VirtualStickView) stick).refreshPreferences();
            ((VirtualStickView) stick).setListener((x, y) -> {
                touchAxisX = x;
                touchAxisY = y;
                fire();
            });
        }
    }

    private void bind(android.app.Activity a, int id, int bit) {
        View v = a.findViewById(id);
        if (v == null) return;
        v.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                touchMask |= bit;
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                fire();
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchMask &= ~bit;
                fire();
                view.performClick();
                return true;
            }
            return true;
        });
    }

    public void setHardwareButton(int bit, boolean down) {
        if (bit == 0) return;
        if (down) hardwareMask |= bit;
        else hardwareMask &= ~bit;
        fire();
    }

    public void setHardwareStick(int x, int y) {
        hardwareAxisX = clamp127(x);
        hardwareAxisY = clamp127(y);
        fire();
    }

    public void release() {
        touchMask = hardwareMask = 0;
        touchAxisX = touchAxisY = hardwareAxisX = hardwareAxisY = 0;
        fire();
    }

    private int[] axis() {
        int touchMag = touchAxisX * touchAxisX + touchAxisY * touchAxisY;
        int hardwareMag = hardwareAxisX * hardwareAxisX + hardwareAxisY * hardwareAxisY;
        return hardwareMag > touchMag
                ? new int[]{hardwareAxisX, hardwareAxisY}
                : new int[]{touchAxisX, touchAxisY};
    }

    private void fire() {
        if (listener == null) return;
        int[] a = axis();
        listener.onInput(touchMask | hardwareMask, a[0], a[1]);
    }

    private static int clamp127(int v) {
        return Math.max(-127, Math.min(127, v));
    }
}
