package cl.retrolink.app;

public final class InputHub {
    public static final class State {
        public final int mask, x, y;
        State(int mask, int x, int y) { this.mask = mask; this.x = x; this.y = y; }
    }

    private static final int[] MASK = new int[5];
    private static final int[] X = new int[5];
    private static final int[] Y = new int[5];

    private InputHub() {}

    public static synchronized void set(int player, int mask, int x, int y) {
        if (player < 1 || player > 4) return;
        MASK[player] = mask;
        X[player] = Math.max(-127, Math.min(127, x));
        Y[player] = Math.max(-127, Math.min(127, y));
    }

    public static synchronized State get(int player) {
        if (player < 1 || player > 4) return new State(0, 0, 0);
        return new State(MASK[player], X[player], Y[player]);
    }

    public static synchronized void resetPlayer(int player) { set(player, 0, 0, 0); }

    public static synchronized void resetAll() {
        for (int p = 1; p <= 4; p++) { MASK[p] = 0; X[p] = 0; Y[p] = 0; }
    }
}
