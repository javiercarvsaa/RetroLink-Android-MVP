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

    /**
     * v0.6.6: viewport remoto centrado y con el mismo overscan que RACE P1.
     * También elimina 2 px de la unión entre jugadores para evitar que la línea
     * divisoria o un borde del viewport vecino se cuele en el stream.
     */
    public static Rect focusedSourceRect(int width, int height, int playerCount, int player, float overscan) {
        Rect base = sourceRect(width, height, playerCount, player);
        if (playerCount <= 1) return base;

        int seam = 2;
        if (playerCount == 2) {
            if (player == 1) base.bottom = Math.max(base.top + 1, base.bottom - seam);
            else base.top = Math.min(base.bottom - 1, base.top + seam);
        } else {
            int midX = width / 2;
            int midY = height / 2;
            if (base.right == midX) base.right = Math.max(base.left + 1, base.right - seam);
            if (base.left == midX) base.left = Math.min(base.right - 1, base.left + seam);
            if (base.bottom == midY) base.bottom = Math.max(base.top + 1, base.bottom - seam);
            if (base.top == midY) base.top = Math.min(base.bottom - 1, base.top + seam);
        }

        float safeOverscan = Math.max(1.0f, Math.min(1.05f, overscan));
        if (safeOverscan <= 1.0001f) return base;

        float visibleScale = 1f / safeOverscan;
        int targetW = Math.max(1, Math.round(base.width() * visibleScale));
        int targetH = Math.max(1, Math.round(base.height() * visibleScale));
        int cx = base.centerX();
        int cy = base.centerY();
        int left = Math.max(base.left, cx - targetW / 2);
        int top = Math.max(base.top, cy - targetH / 2);
        int right = Math.min(base.right, left + targetW);
        int bottom = Math.min(base.bottom, top + targetH);
        left = Math.max(base.left, right - targetW);
        top = Math.max(base.top, bottom - targetH);
        return new Rect(left, top, right, bottom);
    }
}
