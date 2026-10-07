package cl.retrolink.app;

/** Frontend JNI libretro común para todos los cores integrados. */
public final class NativeLibretro {
    static { System.loadLibrary("retrolink_native"); }
    private NativeLibretro() {}

    public static String corePreloadError() { return ""; }

    public static native void nativeClearFrontendOptions();
    public static native void nativeSetFrontendOption(String key, String value);
    public static native String nativeInit(String corePath, String romPath, String systemDir, String saveDir);
    public static native void nativeSetInput(int player, int mask, int axisX, int axisY);
    public static native void nativeSetOutputSize(int width, int height);
    public static native int nativeGetVideoWidth();
    public static native int nativeGetVideoHeight();
    public static native long nativeCopyVideoFrame(int[] outArgb);
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
