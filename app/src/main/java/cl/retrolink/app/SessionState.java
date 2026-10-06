package cl.retrolink.app;

public final class SessionState {
    private static final boolean[] CONNECTED = new boolean[5];
    private static int configuredPlayers = 1;
    private static double contentFps = 60.0;
    private static boolean dk64Profile;
    private static String activeGameLabel = "N64 AUTO";

    private SessionState() {}

    public static synchronized void reset() {
        configuredPlayers = 1;
        contentFps = 60.0;
        dk64Profile = false;
        activeGameLabel = "N64 AUTO";
        for (int i = 0; i < CONNECTED.length; i++) CONNECTED[i] = false;
        CONNECTED[1] = true;
    }

    public static synchronized void setConfiguredPlayers(int count) {
        configuredPlayers = Math.max(1, Math.min(4, count));
        CONNECTED[1] = true;
    }

    public static synchronized void playerConnected(int player) {
        if (player >= 1 && player <= 4) CONNECTED[player] = true;
    }

    public static synchronized void playerDisconnected(int player) {
        if (player >= 2 && player <= 4) CONNECTED[player] = false;
    }

    public static synchronized boolean isConnected(int player) {
        return player >= 1 && player <= 4 && CONNECTED[player];
    }

    public static synchronized int getPlayerCount() {
        int highest = 1;
        for (int p = 2; p <= 4; p++) if (CONNECTED[p]) highest = p;
        return Math.max(configuredPlayers, highest);
    }

    public static synchronized void setContentFps(double fps) {
        if (fps >= 20.0 && fps <= 120.0) contentFps = fps;
    }

    public static synchronized double getContentFps() { return contentFps; }

    public static synchronized void setGameProfile(N64GameProfile p) {
        dk64Profile = p != null && p.isDk64();
        activeGameLabel = p == null ? "N64 AUTO" : p.shortLabel();
    }

    public static synchronized boolean isDk64Profile() { return dk64Profile; }
    public static synchronized String getActiveGameLabel() { return activeGameLabel; }
}
