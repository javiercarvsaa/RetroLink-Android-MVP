package cl.retrolink.app;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.opengl.GLES20;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Choreographer;
import android.view.Surface;
import android.view.WindowManager;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmulatorSurfaceView extends GLSurfaceView {
    public interface Listener {
        void onCoreReady(String coreInfo, double fps, int sampleRate);
        void onCoreError(String error);
        void onStats(String stats);
    }

    private final CoreRenderer coreRenderer;
    private int renderWidth = 640;
    private int renderHeight = 480;
    private int performanceProfile = 1;
    private volatile N64GameProfile gameProfile = N64GameProfile.detect("");
    private volatile CoreRegistry.Core activeCore = CoreRegistry.N64;
    private volatile AdaptiveOptimizationEngine.Plan adaptivePlan;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);
    private final CountDownLatch shutdownDone = new CountDownLatch(1);

    // v0.5.0 PresentSync: GLSurfaceView no longer swaps at the display refresh
    // when the N64 core has not produced a new frame. This prevents stale back
    // buffers from being presented on 120 Hz panels between 50/60 Hz N64 VIs.
    private volatile boolean framePacerRunning;
    private volatile long pacerIntervalNs = 16_666_667L;
    private long pacerLastVsyncNs;
    private long pacerAccumulatorNs;
    private final Choreographer.FrameCallback presentSyncCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!framePacerRunning) return;

            if (pacerLastVsyncNs == 0L) {
                pacerLastVsyncNs = frameTimeNanos;
                requestRender();
            } else {
                long delta = frameTimeNanos - pacerLastVsyncNs;
                pacerLastVsyncNs = frameTimeNanos;
                if (delta <= 0L || delta > 100_000_000L) {
                    pacerAccumulatorNs = 0L;
                } else {
                    pacerAccumulatorNs += delta;
                }

                long interval = Math.max(8_000_000L, pacerIntervalNs);
                // Small tolerance keeps 59.94/60 and 49.7/50 content locked to
                // the nearest display VSYNC without issuing empty swaps.
                if (pacerAccumulatorNs + 750_000L >= interval) {
                    pacerAccumulatorNs -= interval;
                    if (pacerAccumulatorNs < 0L) pacerAccumulatorNs = 0L;
                    if (pacerAccumulatorNs > interval * 2L) pacerAccumulatorNs = 0L;
                    requestRender();
                }
            }

            if (framePacerRunning) Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public EmulatorSurfaceView(Context context) { this(context, null); }
    public EmulatorSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(3);
        getHolder().setFixedSize(renderWidth, renderHeight);
        setEGLConfigChooser(8, 8, 8, 8, 24, 8);
        setPreserveEGLContextOnPause(true);
        coreRenderer = new CoreRenderer();
        setRenderer(coreRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        requestRender();
    }

    public void setAdaptivePlan(AdaptiveOptimizationEngine.Plan plan) {
        adaptivePlan = plan;
        coreRenderer.setAdaptivePlan(plan);
    }

    public void setPerformanceProfile(int profile) {
        performanceProfile = Math.max(0, Math.min(2, profile));
        if (performanceProfile == 0) { renderWidth = 320; renderHeight = 240; }
        else if (performanceProfile == 2) { renderWidth = 960; renderHeight = 720; }
        else { renderWidth = 640; renderHeight = 480; }
        try { getHolder().setFixedSize(renderWidth, renderHeight); } catch (Throwable ignored) {}
        coreRenderer.setRenderSize(renderWidth, renderHeight, performanceProfile);
    }

    public void configure(String corePath, String romPath, String systemDir, String saveDir, Listener listener) {
        configure(CoreRegistry.N64, corePath, romPath, systemDir, saveDir, listener);
    }

    public void configure(CoreRegistry.Core core, String corePath, String romPath, String systemDir, String saveDir, Listener listener) {
        activeCore = core == null ? CoreRegistry.N64 : core;
        if (activeCore == CoreRegistry.N64) {
            gameProfile = N64GameProfile.detect(romPath);
            SessionState.setGameProfile(gameProfile);
        } else {
            gameProfile = null;
            SessionState.setGameProfile(null);
        }
        coreRenderer.configure(corePath, romPath, systemDir, saveDir, listener);
        requestRender();
    }

    public String getGameProfileLabel() {
        if (activeCore != CoreRegistry.N64) return activeCore.shortSystem;
        return gameProfile == null ? "N64 AUTO" : gameProfile.shortLabel();
    }

    public boolean isCoreReady() { return coreRenderer.ready; }
    public void resetCore() {
        if (!shutdownStarted.get()) queueEvent(NativeLibretro::nativeReset);
    }

    /**
     * Cierre idempotente del core. Se ejecuta en el hilo GL mientras el contexto
     * sigue vivo y permite a la Activity esperar brevemente antes de pausar GLSurfaceView.
     */
    public boolean shutdownCoreAndWait(long timeoutMs) {
        final boolean first = shutdownStarted.compareAndSet(false, true);
        if (first) {
            stopFramePacer();
            coreRenderer.audio.stop();
            try {
                queueEvent(() -> {
                    try {
                        coreRenderer.closeAdaptiveSession();
                        NativeLibretro.nativeShutdown();
                        coreRenderer.ready = false;
                    } finally {
                        shutdownDone.countDown();
                    }
                });
                requestRender();
            } catch (Throwable t) {
                shutdownDone.countDown();
            }
        }
        try {
            return shutdownDone.await(Math.max(100L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void shutdownCore() {
        shutdownCoreAndWait(1200L);
    }

    private void startFramePacer(double targetFps) {
        final double safeFps = Math.max(24.0, Math.min(120.0, targetFps));
        pacerIntervalNs = (long)(1_000_000_000.0 / safeFps);
        post(() -> {
            pacerLastVsyncNs = 0L;
            pacerAccumulatorNs = 0L;
            if (!framePacerRunning) {
                framePacerRunning = true;
                Choreographer.getInstance().postFrameCallback(presentSyncCallback);
            }
        });
    }

    private void stopFramePacer() {
        post(() -> {
            framePacerRunning = false;
            pacerLastVsyncNs = 0L;
            pacerAccumulatorNs = 0L;
            try { Choreographer.getInstance().removeFrameCallback(presentSyncCallback); }
            catch (Throwable ignored) {}
        });
    }

    @Override public void onPause() {
        stopFramePacer();
        super.onPause();
    }

    @Override public void onResume() {
        super.onResume();
        if (coreRenderer.ready) startFramePacer(coreRenderer.fps);
        else requestRender();
    }

    private final class CoreRenderer implements GLSurfaceView.Renderer {
        private String corePath, romPath, systemDir, saveDir;
        private Listener listener;
        private boolean configured;
        private boolean surfaceReady;
        private volatile boolean ready;
        private boolean initializedOnce;
        private double fps = 60.0;
        private long frameIntervalNs = 16_666_667L;
        private long statStartNs;
        private int statFrames;
        private long lastVideoFrameCount;
        private int rw = 640, rh = 480, profile = 1;
        private final short[] audioBuffer = new short[8192];
        private final NativeAudioSink audio = new NativeAudioSink();
        private AdaptiveOptimizationEngine.Plan rendererPlan;
        private AdaptiveRuntimeSession adaptiveSession;

        synchronized void setAdaptivePlan(AdaptiveOptimizationEngine.Plan plan) {
            rendererPlan = plan;
        }

        synchronized void setRenderSize(int width, int height, int p) {
            rw = width; rh = height; profile = p;
        }

        synchronized void configure(String corePath, String romPath, String systemDir, String saveDir, Listener listener) {
            this.corePath = corePath;
            this.romPath = romPath;
            this.systemDir = systemDir;
            this.saveDir = saveDir;
            this.listener = listener;
            configured = true;
        }

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
                                               javax.microedition.khronos.egl.EGLConfig config) {
            try { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY); } catch (Throwable ignored) {}
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            try { EGL14.eglSwapInterval(EGL14.eglGetCurrentDisplay(), 1); } catch (Throwable ignored) {}
            if (initializedOnce && ready) NativeLibretro.nativeContextReset();
        }

        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            NativeLibretro.nativeSetOutputSize(width, height);
            surfaceReady = true;
            requestRender();
        }

        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            if (!ready) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                if (configured && surfaceReady && !initializedOnce) initializeCore();
                return;
            }

            long now = System.nanoTime();
            long adaptiveWorkStart = now;

            for (int p = 1; p <= 4; p++) {
                InputHub.State s = InputHub.get(p);
                NativeLibretro.nativeSetInput(p, s.mask, s.x, s.y);
            }

            if (!NativeLibretro.nativeRunFrame()) {
                ready = false;
                stopFramePacer();
                postError("El núcleo " + activeCore.shortSystem + " dejó de ejecutar frames");
                return;
            }

            int n = NativeLibretro.nativeDrainAudio(audioBuffer);
            if (n > 0) audio.write(audioBuffer, n);
            AdaptiveRuntimeSession session = adaptiveSession;
            if (session != null) session.recordFrame(System.nanoTime() - adaptiveWorkStart);

            statFrames++;
            if (now - statStartNs >= 1_000_000_000L) {
                long elapsed = Math.max(1L, now - statStartNs);
                double realFps = statFrames * 1_000_000_000.0 / elapsed;
                long videoCount = NativeLibretro.nativeGetVideoFrameCount();
                double gameFps = (videoCount - lastVideoFrameCount) * 1_000_000_000.0 / elapsed;
                lastVideoFrameCount = videoCount;
                float displayHz = 0f;
                try {
                    Display d = getDisplay();
                    if (d != null) displayHz = d.getRefreshRate();
                } catch (Throwable ignored) {}
                final String msg = String.format(Locale.US,
                        "VI/PRESENT %.1f/%.2f · video %.1f FPS · pantalla %.0f Hz · %dx%d · %s · PSYNC",
                        realFps, fps, gameFps, displayHz, rw, rh,
                        profile == 0 ? "PERF" : profile == 2 ? "QUALITY" : "BALANCED")
                        + (adaptiveSession == null ? "" : " · " + adaptiveSession.compactStatus());
                if (listener != null) post(() -> listener.onStats(msg));
                statFrames = 0;
                statStartNs = now;
            }
        }

        private void initializeCore() {
            initializedOnce = true;
            if (listener != null) post(() -> listener.onStats("Etapa 2/5 · cargando " + activeCore.shortSystem + " · " + rw + "×" + rh));

            NativeLibretro.nativeClearFrontendOptions();
            if (activeCore == CoreRegistry.N64) {
            NativeLibretro.nativeSetFrontendOption("mupen64plus-rdp-plugin", "gliden64");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-rsp-plugin", "hle");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-cpucore", "dynamic_recompiler");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-43screensize", rw + "x" + rh);
            NativeLibretro.nativeSetFrontendOption("mupen64plus-aspect", "4:3");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-ThreadedRenderer", "True");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-EnableNativeResFactor", "0");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-MultiSampling", "0");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-FXAA", profile == 1 ? "1" : "0");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-HybridFilter", "False");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-FrameDuping", "False");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-Framerate", "Original");
            NativeLibretro.nativeSetFrontendOption("mupen64plus-virefresh", "Auto");

            // v0.5.0: ruta VI/framebuffer específica para Donkey Kong 64.
            // El ROM US 1.0 se identifica por CRC EC58EABF-AD7C7169.
            // En Android/GLES3 damos prioridad real a los Core Options sobre el INI,
            // mantenemos Framebuffer Emulation activo, pero deshabilitamos la copia
            // de profundidad a RDRAM que puede provocar resets/retención de cámara
            // en DK64. La copia de color se hace en modo síncrono para evitar leer
            // buffers atrasados. No se aplica ningún parche temporal ni de memoria.
            if (gameProfile != null && gameProfile.isDk64()) {
                NativeLibretro.nativeSetFrontendOption("mupen64plus-GLideN64IniBehaviour", "early");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-EnableFBEmulation", "True");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-EnableCopyDepthToRDRAM", "Off");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-EnableCopyColorToRDRAM", "Sync");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-EnableCopyColorFromRDRAM", "False");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-EnableCopyAuxToRDRAM", "False");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-EnableLegacyBlending", "False");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-BilinearMode", "3point");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-ThreadedRenderer", "True");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-CorrectTexrectCoords", "Auto");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-EnableOverscan", "Disabled");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-FXAA", "0");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-HybridFilter", "False");
                NativeLibretro.nativeSetFrontendOption("mupen64plus-FrameDuping", "False");
            }
            }

            String error;
            try { error = NativeLibretro.nativeInit(corePath, romPath, systemDir, saveDir); }
            catch (Throwable t) { error = t.getClass().getSimpleName() + ": " + t.getMessage(); }
            if (error != null && !error.isEmpty()) {
                ready = false;
                String preload = NativeLibretro.corePreloadError();
                String detail = error + (preload.isEmpty() ? "" : " · preload=" + preload);
                postError(detail);
                return;
            }

            // v0.5.0: DK64 se ejecuta sin deblur/cheats runtime.
            // El objetivo es aislar la salida VI/framebuffer real del core.
            if (gameProfile != null && gameProfile.isDk64()) {
                NativeLibretro.nativeResetCheats();
            }

            if (listener != null) {
                final String gp = activeCore == CoreRegistry.N64 ? (gameProfile == null ? "N64 AUTO" : gameProfile.shortLabel()) : activeCore.system;
                post(() -> listener.onStats("Etapa 4/5 · ROM cargada · " + gp + " · iniciando ejecución"));
            }
            fps = NativeLibretro.nativeGetFps();
            if (fps < 20 || fps > 120) fps = 60.0;
            frameIntervalNs = (long)(1_000_000_000.0 / fps);
            if (rendererPlan == null) {
                rendererPlan = AdaptiveOptimizationEngine.resolve(
                        getContext(), activeCore, romPath, false);
            }
            adaptiveSession = new AdaptiveRuntimeSession(getContext(), rendererPlan,
                    new AdaptiveRuntimeSession.Listener() {
                        @Override public void onAdaptiveStatus(String value) {
                            if (listener != null) post(() -> listener.onStats("N64 · " + value));
                        }
                        @Override public void onOptionalEffectsAllowed(boolean allowed) {
                            // Core options are not mutated mid-session; safe profile is persisted for next launch.
                        }
                    });
            adaptiveSession.startForCurrentThread(frameIntervalNs);
            requestContentFrameRate(fps);
            int sampleRate = NativeLibretro.nativeGetSampleRate();
            audio.start(sampleRate);
            ready = true;
            statStartNs = System.nanoTime();
            statFrames = 0;
            lastVideoFrameCount = NativeLibretro.nativeGetVideoFrameCount();
            String info = NativeLibretro.nativeGetCoreInfo();
            startFramePacer(fps);
            if (listener != null) post(() -> listener.onCoreReady(info, fps, sampleRate));
        }

        private void requestContentFrameRate(double contentFps) {
            final float requested = (float)Math.max(24.0, Math.min(120.0, contentFps));
            post(() -> {
                try {
                    Surface s = getHolder().getSurface();
                    if (s != null && s.isValid()) {
                        if (Build.VERSION.SDK_INT >= 31) {
                            s.setFrameRate(requested,
                                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                                    Surface.CHANGE_FRAME_RATE_ALWAYS);
                        } else if (Build.VERSION.SDK_INT >= 30) {
                            s.setFrameRate(requested, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
                        }
                    }
                } catch (Throwable ignored) {}

                try {
                    Context c = getContext();
                    if (!(c instanceof Activity) || Build.VERSION.SDK_INT < 23) return;
                    Activity a = (Activity)c;
                    Display d = a.getWindowManager().getDefaultDisplay();
                    if (d == null) return;
                    Display.Mode best = null;
                    float bestRate = -1f;
                    for (Display.Mode m : d.getSupportedModes()) {
                        float rr = m.getRefreshRate();
                        float ratio = rr / requested;
                        float nearest = Math.round(ratio);
                        if (nearest < 1f || Math.abs(ratio - nearest) > 0.015f) continue;
                        if (rr > bestRate) { best = m; bestRate = rr; }
                    }
                    if (best != null && best.getModeId() != d.getMode().getModeId()) {
                        WindowManager.LayoutParams lp = a.getWindow().getAttributes();
                        lp.preferredDisplayModeId = best.getModeId();
                        a.getWindow().setAttributes(lp);
                    }
                } catch (Throwable ignored) {}
            });
        }

        void closeAdaptiveSession() {
            if (adaptiveSession != null) {
                adaptiveSession.close();
                adaptiveSession = null;
            }
        }

        private void postError(String error) {
            if (listener != null) post(() -> listener.onCoreError(error));
        }
    }
}
