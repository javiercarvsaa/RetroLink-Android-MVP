package cl.retrolink.app;

import android.graphics.Rect;

public final class SplitScreenProfile {
    public static final String MARIO_KART_64 = "Mario Kart 64";
    private SplitScreenProfile() {}

    public static Rect sourceRect(int width, int height, int playerCount, int player) {
        player = Math.max(1, Math.min(4, player));
        if (playerCount <= 1) return new Rect(0, 0, width, height);
        if (playerCount == 2) {
            int mid = height / 2;
            return player == 1 ? new Rect(0, 0, width, mid) : new Rect(0, mid, width, height);
        }
        int midX = width / 2;
        int midY = height / 2;
        switch (player) {
            case 1: return new Rect(0, 0, midX, midY);
            case 2: return new Rect(midX, 0, width, midY);
            case 3: return new Rect(0, midY, midX, height);
            default: return new Rect(midX, midY, width, height);
        }
    }
}
