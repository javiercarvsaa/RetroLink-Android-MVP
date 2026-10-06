package cl.retrolink.app.ble;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BleProtocol {
    private BleProtocol() {}

    public static final UUID SERVICE_UUID = UUID.fromString("e3a10000-8b2a-4bf1-a87c-4e8f9d4c0001");
    public static final UUID INPUT_UUID = UUID.fromString("e3a10001-8b2a-4bf1-a87c-4e8f9d4c0001");
    public static final UUID PLAYER_UUID = UUID.fromString("e3a10002-8b2a-4bf1-a87c-4e8f9d4c0001");
    public static final UUID HOST_INFO_UUID = UUID.fromString("e3a10003-8b2a-4bf1-a87c-4e8f9d4c0001");

    public static final int UP      = 1 << 0;
    public static final int DOWN    = 1 << 1;
    public static final int LEFT    = 1 << 2;
    public static final int RIGHT   = 1 << 3;
    public static final int A       = 1 << 4;
    public static final int B       = 1 << 5;
    public static final int Z       = 1 << 6;
    public static final int C_UP    = 1 << 7;
    public static final int L       = 1 << 8;
    public static final int R       = 1 << 9;
    public static final int START   = 1 << 10;
    public static final int C_DOWN  = 1 << 11;
    public static final int C_LEFT  = 1 << 12;
    public static final int C_RIGHT = 1 << 13;

    public static byte[] packet(int sequence, int mask, int axisX, int axisY) {
        return new byte[] {
                (byte) (sequence & 0xFF),
                (byte) (mask & 0xFF),
                (byte) ((mask >> 8) & 0xFF),
                (byte) clamp(axisX),
                (byte) clamp(axisY)
        };
    }

    public static int maskFromPacket(byte[] value) {
        if (value == null || value.length < 3) return 0;
        return (value[1] & 0xFF) | ((value[2] & 0xFF) << 8);
    }

    public static int axisXFromPacket(byte[] value) {
        return value != null && value.length >= 5 ? value[3] : 0;
    }

    public static int axisYFromPacket(byte[] value) {
        return value != null && value.length >= 5 ? value[4] : 0;
    }

    private static int clamp(int v) { return Math.max(-127, Math.min(127, v)); }

    public static String buttonsToText(int mask) {
        if (mask == 0) return "—";
        List<String> out = new ArrayList<>();
        if ((mask & UP) != 0) out.add("D↑");
        if ((mask & DOWN) != 0) out.add("D↓");
        if ((mask & LEFT) != 0) out.add("D←");
        if ((mask & RIGHT) != 0) out.add("D→");
        if ((mask & A) != 0) out.add("A");
        if ((mask & B) != 0) out.add("B");
        if ((mask & Z) != 0) out.add("Z");
        if ((mask & C_UP) != 0) out.add("C↑");
        if ((mask & C_DOWN) != 0) out.add("C↓");
        if ((mask & C_LEFT) != 0) out.add("C←");
        if ((mask & C_RIGHT) != 0) out.add("C→");
        if ((mask & L) != 0) out.add("L");
        if ((mask & R) != 0) out.add("R");
        if ((mask & START) != 0) out.add("START");
        return String.join(" + ", out);
    }
}
