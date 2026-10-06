package cl.retrolink.app;

public final class NativeLibretro {
    private static final String CORE_PRELOAD_ERROR;
    static {
        String error = "";
        System.loadLibrary("retrolink_native");
        try {
            System.loadLibrary("retro_n64");
        } catch (Throwable t) {
            error = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
        }
        CORE_PRELOAD_ERROR = error;
    }

    private NativeLibretro() {}

    public static String corePreloadError() { return CORE_PRELOAD_ERROR; }

    public static native void nativeClearFrontendOptions();
    public static native void nativeSetFrontendOption(String key, String value);
    public static native String nativeInit(String corePath, String romPath, String systemDir, String saveDir);
    public static native void nativeSetInput(int player, int mask, int axisX, int axisY);
    public static native boolean nativeRunFrame();
    public static native void nativeReset();
    public static native void nativeSetCheat(int index, boolean enabled, String code);
    public static native void nativeResetCheats();
    public static native void nativeContextReset();
    public static native int nativeDrainAudio(short[] out);
    public static native double nativeGetFps();
    public static native long nativeGetVideoFrameCount();
    public static native int nativeGetSampleRate();
    public static native String nativeGetCoreInfo();
    public static native String nativeGetStage();
    public static native void nativeShutdown();
}
