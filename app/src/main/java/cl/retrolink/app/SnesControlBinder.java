package cl.retrolink.app;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import cl.retrolink.app.ble.BleProtocol;

/**
 * Controles SNES sobre el protocolo de bits RetroLink existente.
 * C_LEFT se reutiliza como X y C_UP como Y; Z se reutiliza como SELECT.
 */
public final class SnesControlBinder {
    public interface Listener { void onInput(int mask, int axisX, int axisY); }

    private final Listener listener;
    private int touchMask, hardwareMask;
    private int touchAxisX, touchAxisY;
    private int hardwareAxisX, hardwareAxisY;

    public SnesControlBinder(android.app.Activity activity, Listener listener) {
        this.listener = listener;
        bind(activity, R.id.btnA, BleProtocol.A);
        bind(activity, R.id.btnB, BleProtocol.B);
        bind(activity, R.id.btnX, BleProtocol.C_LEFT);
        bind(activity, R.id.btnY, BleProtocol.C_UP);
        bind(activity, R.id.btnSelect, BleProtocol.Z);
        bind(activity, R.id.btnL, BleProtocol.L);
        bind(activity, R.id.btnR, BleProtocol.R);
        bind(activity, R.id.btnStart, BleProtocol.START);

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
        int tm = touchAxisX * touchAxisX + touchAxisY * touchAxisY;
        int hm = hardwareAxisX * hardwareAxisX + hardwareAxisY * hardwareAxisY;
        return hm > tm
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
