package cl.retrolink.app;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import cl.retrolink.app.ble.BleProtocol;

/** Une entrada táctil + mando físico en un único estado N64. */
public class N64ControlBinder {
    public interface Listener { void onInput(int mask, int axisX, int axisY); }

    private final Listener listener;
    private int touchMask, hardwareMask;
    private int touchAxisX, touchAxisY;
    private int hardwareAxisX, hardwareAxisY;

    public N64ControlBinder(android.app.Activity a, Listener l) {
        listener = l;
        bind(a, R.id.btnA, BleProtocol.A);
        bind(a, R.id.btnB, BleProtocol.B);
        bind(a, R.id.btnZ, BleProtocol.Z);
        bind(a, R.id.btnL, BleProtocol.L);
        bind(a, R.id.btnR, BleProtocol.R);
        bind(a, R.id.btnStart, BleProtocol.START);
        bind(a, R.id.btnCUp, BleProtocol.C_UP);
        bind(a, R.id.btnCDown, BleProtocol.C_DOWN);
        bind(a, R.id.btnCLeft, BleProtocol.C_LEFT);
        bind(a, R.id.btnCRight, BleProtocol.C_RIGHT);

        View stick = a.findViewById(R.id.virtualStick);
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
        v.setOnTouchListener((view, e) -> {
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                touchMask |= bit;
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                fire();
                return true;
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
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
        int before = hardwareMask;
        if (down) hardwareMask |= bit;
        else hardwareMask &= ~bit;
        if (before != hardwareMask) fire();
    }

    public void setHardwareStick(int x, int y) {
        int nx = clamp127(x);
        int ny = clamp127(y);
        if (hardwareAxisX == nx && hardwareAxisY == ny) return;
        hardwareAxisX = nx;
        hardwareAxisY = ny;
        fire();
    }

    public int currentMask() { return touchMask | hardwareMask; }

    public int currentAxisX() { return resolvedAxis()[0]; }
    public int currentAxisY() { return resolvedAxis()[1]; }

    public void release() {
        touchMask = 0;
        hardwareMask = 0;
        touchAxisX = touchAxisY = 0;
        hardwareAxisX = hardwareAxisY = 0;
        fire();
    }

    private int[] resolvedAxis() {
        int touchMag = touchAxisX * touchAxisX + touchAxisY * touchAxisY;
        int hardwareMag = hardwareAxisX * hardwareAxisX + hardwareAxisY * hardwareAxisY;
        if (hardwareMag > touchMag) return new int[]{hardwareAxisX, hardwareAxisY};
        return new int[]{touchAxisX, touchAxisY};
    }

    private void fire() {
        if (listener == null) return;
        int[] axis = resolvedAxis();
        listener.onInput(touchMask | hardwareMask, axis[0], axis[1]);
    }

    private static int clamp127(int v) { return Math.max(-127, Math.min(127, v)); }
}
