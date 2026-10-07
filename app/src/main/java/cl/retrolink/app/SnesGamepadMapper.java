package cl.retrolink.app;

import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import cl.retrolink.app.ble.BleProtocol;

/** Android HID/Gamepad -> mando SNES. */
public final class SnesGamepadMapper {
    private SnesGamepadMapper() {}

    public static boolean handleKeyEvent(Context context, SnesControlBinder controls, KeyEvent event) {
        if (controls == null || event == null || !isController(event.getSource(), event.getDevice()))
            return false;
        int bit = keyToBit(event.getKeyCode());
        if (bit == 0) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
            controls.setHardwareButton(bit, event.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        return false;
    }

    public static boolean handleMotionEvent(Context context, SnesControlBinder controls, MotionEvent event) {
        if (controls == null || event == null || event.getActionMasked() != MotionEvent.ACTION_MOVE)
            return false;
        InputDevice d = event.getDevice();
        if (!isController(event.getSource(), d)) return false;

        float x = axis(event, d, MotionEvent.AXIS_X);
        float y = axis(event, d, MotionEvent.AXIS_Y);
        x = applyDeadzone(context, x);
        y = applyDeadzone(context, y);
        if (RetroPreferences.stickInvertY(context)) y = -y;
        controls.setHardwareStick(Math.round(x * 127f), Math.round(y * 127f));

        float hx = axis(event, d, MotionEvent.AXIS_HAT_X);
        float hy = axis(event, d, MotionEvent.AXIS_HAT_Y);
        controls.setHardwareButton(BleProtocol.LEFT, hx < -0.5f);
        controls.setHardwareButton(BleProtocol.RIGHT, hx > 0.5f);
        controls.setHardwareButton(BleProtocol.UP, hy < -0.5f);
        controls.setHardwareButton(BleProtocol.DOWN, hy > 0.5f);
        return true;
    }

    private static int keyToBit(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: return BleProtocol.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return BleProtocol.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return BleProtocol.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return BleProtocol.RIGHT;
            case KeyEvent.KEYCODE_BUTTON_A: return BleProtocol.A;
            case KeyEvent.KEYCODE_BUTTON_B: return BleProtocol.B;
            case KeyEvent.KEYCODE_BUTTON_X: return BleProtocol.C_LEFT;
            case KeyEvent.KEYCODE_BUTTON_Y: return BleProtocol.C_UP;
            case KeyEvent.KEYCODE_BUTTON_L1: return BleProtocol.L;
            case KeyEvent.KEYCODE_BUTTON_R1: return BleProtocol.R;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return BleProtocol.Z;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_MODE: return BleProtocol.START;
            default: return 0;
        }
    }

    private static boolean isController(int source, InputDevice d) {
        int s = d == null ? source : d.getSources();
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
                || (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private static float axis(MotionEvent e, InputDevice d, int axis) {
        if (d == null) return 0f;
        InputDevice.MotionRange range = d.getMotionRange(axis, e.getSource());
        if (range == null) range = d.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
        if (range == null) return 0f;
        float v = e.getAxisValue(axis);
        float flat = Math.max(0.02f, range.getFlat());
        return Math.abs(v) > flat ? Math.max(-1f, Math.min(1f, v)) : 0f;
    }

    private static float applyDeadzone(Context c, float v) {
        float dz = RetroPreferences.stickDeadzone(c);
        float a = Math.abs(v);
        if (a <= dz) return 0f;
        float n = (a - dz) / Math.max(0.001f, 1f - dz);
        return Math.copySign(Math.min(1f, n * RetroPreferences.stickSensitivity(c)), v);
    }
}
