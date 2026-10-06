package cl.retrolink.app;

import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

import cl.retrolink.app.ble.BleProtocol;

/**
 * Adaptador HID/Gamepad Android -> estado de entrada RetroLink.
 * No requiere emparejamiento propio: funciona con mandos que Android expone como
 * SOURCE_GAMEPAD/SOURCE_JOYSTICK (Bluetooth o USB).
 */
public final class AndroidGamepadMapper {
    private static final float C_THRESHOLD = 0.55f;

    private AndroidGamepadMapper() {}

    public static boolean handleKeyEvent(Context context, N64ControlBinder controls, KeyEvent event) {
        if (controls == null || event == null || !isControllerEvent(event.getSource(), event.getDevice())) return false;
        int bit = keyToN64Bit(event.getKeyCode());
        if (bit == 0) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
            controls.setHardwareButton(bit, event.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        return false;
    }

    public static boolean handleMotionEvent(Context context, N64ControlBinder controls, MotionEvent event) {
        if (controls == null || event == null) return false;
        InputDevice device = event.getDevice();
        if (!isControllerEvent(event.getSource(), device)) return false;
        if (event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;

        float lx = centeredAxis(event, device, MotionEvent.AXIS_X);
        float ly = centeredAxis(event, device, MotionEvent.AXIS_Y);
        lx = applyDeadzoneAndSensitivity(context, lx);
        ly = applyDeadzoneAndSensitivity(context, ly);
        if (RetroPreferences.stickInvertY(context)) ly = -ly;
        controls.setHardwareStick(Math.round(lx * 127f), Math.round(ly * 127f));

        // D-pad analógico/HAT.
        float hx = centeredAxis(event, device, MotionEvent.AXIS_HAT_X);
        float hy = centeredAxis(event, device, MotionEvent.AXIS_HAT_Y);
        controls.setHardwareButton(BleProtocol.LEFT, hx < -0.5f);
        controls.setHardwareButton(BleProtocol.RIGHT, hx > 0.5f);
        controls.setHardwareButton(BleProtocol.UP, hy < -0.5f);
        controls.setHardwareButton(BleProtocol.DOWN, hy > 0.5f);

        // Stick derecho -> C-buttons N64. Se prueban Z/RZ primero y luego RX/RY.
        float rx = axisWithFallback(event, device, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX);
        float ry = axisWithFallback(event, device, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY);
        controls.setHardwareButton(BleProtocol.C_LEFT, rx < -C_THRESHOLD);
        controls.setHardwareButton(BleProtocol.C_RIGHT, rx > C_THRESHOLD);
        controls.setHardwareButton(BleProtocol.C_UP, ry < -C_THRESHOLD);
        controls.setHardwareButton(BleProtocol.C_DOWN, ry > C_THRESHOLD);
        return true;
    }

    public static String connectedGamepadsSummary() {
        List<String> names = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (!isPhysicalGamepad(d)) continue;
            String name = d.getName();
            if (name == null || name.trim().isEmpty()) name = "Mando Android";
            if (!names.contains(name)) names.add(name);
        }
        if (names.isEmpty()) return "Sin mando físico · conecta Bluetooth o USB";
        if (names.size() == 1) return "● " + names.get(0) + " · LISTO";
        return "● " + names.size() + " mandos detectados · " + names.get(0);
    }


    private static boolean isPhysicalGamepad(InputDevice d) {
        if (d == null || d.isVirtual()) return false;
        int s = d.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    public static boolean isController(InputDevice d) {
        if (d == null) return false;
        int s = d.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (s & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    private static boolean isControllerEvent(int source, InputDevice d) {
        return ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                || ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
                || ((source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD)
                || isController(d);
    }

    private static int keyToN64Bit(int key) {
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
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R2: return BleProtocol.Z;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_MODE: return BleProtocol.START;
            default: return 0;
        }
    }

    private static float axisWithFallback(MotionEvent e, InputDevice d, int primary, int fallback) {
        if (hasAxis(d, primary)) return centeredAxis(e, d, primary);
        return centeredAxis(e, d, fallback);
    }

    private static boolean hasAxis(InputDevice d, int axis) {
        return d != null && d.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null;
    }

    private static float centeredAxis(MotionEvent event, InputDevice device, int axis) {
        if (device == null) return 0f;
        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range == null) range = device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
        if (range == null) return 0f;
        float value = event.getAxisValue(axis);
        float flat = Math.max(0.02f, range.getFlat());
        return Math.abs(value) > flat ? clamp(value, -1f, 1f) : 0f;
    }

    private static float applyDeadzoneAndSensitivity(Context c, float v) {
        float dz = RetroPreferences.stickDeadzone(c);
        float a = Math.abs(v);
        if (a <= dz) return 0f;
        float normalized = (a - dz) / Math.max(0.001f, 1f - dz);
        float out = Math.min(1f, normalized * RetroPreferences.stickSensitivity(c));
        return Math.copySign(out, v);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
