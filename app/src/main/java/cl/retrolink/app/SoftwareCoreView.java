package cl.retrolink.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import cl.retrolink.app.net.FrameStreamServer;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runner seguro para cores libretro con framebuffer por software.
 * El core corre en su hilo; video se copia desde libretro y Android lo presenta por Canvas.
 * Esta ruta sirve a GB/GBC y queda como base para SNES/Atari.
 */
public final class SoftwareCoreView extends SurfaceView implements SurfaceHolder.Callback {
    private CoreRegistry.Core core = CoreRegistry.GAME_BOY;
    private String corePath, romPath, systemDir, saveDir;
    private EmulatorSurfaceView.Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean configured = new AtomicBoolean(false);
    private volatile boolean surfaceReady;
    private volatile boolean paused;
    private volatile boolean ready;
    private volatile boolean resetRequested;
    private volatile String gameBoyLinkMode = "Not Connected";
    private volatile String gameBoyLinkHost = "";
    private volatile int gameBoyLinkPort = 56400;
    private volatile boolean ps1DefaultsEnabled;
    private Thread thread;
    private CountDownLatch stopped = new CountDownLatch(1);

    private int[] pixels;
    private Bitmap bitmap;
    private long lastFrameSerial;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final short[] audioBuffer = new short[8192];
    private final NativeAudioSink audio = new NativeAudioSink();

    public SoftwareCoreView(Context c) { this(c, null); }
    public SoftwareCoreView(Context c, AttributeSet a) {
        super(c, a);
        getHolder().addCallback(this);
        paint.setFilterBitmap(false);
        setKeepScreenOn(true);
    }

    public synchronized void configure(CoreRegistry.Core core, String corePath, String romPath,
                                       String systemDir, String saveDir,
                                       EmulatorSurfaceView.Listener listener) {
        this.core = core == null ? CoreRegistry.GAME_BOY : core;
        paint.setFilterBitmap(this.core == CoreRegistry.PS1);
        this.corePath = corePath;
        this.romPath = romPath;
        this.systemDir = systemDir;
        this.saveDir = saveDir;
        this.listener = listener;
        configured.set(true);
        maybeStart();
    }

    public synchronized void configureGameBoyLink(String mode, String host, int port) {
        gameBoyLinkMode = mode == null ? "Not Connected" : mode;
        gameBoyLinkHost = host == null ? "" : host.trim();
        gameBoyLinkPort = Math.max(56400, Math.min(56420, port));
    }

    public synchronized void configurePs1Defaults() {
        ps1DefaultsEnabled = true;
    }

    private void applyPs1Options() {
        if (core != CoreRegistry.PS1 || !ps1DefaultsEnabled) return;
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_region", "auto");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_bios", "auto");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_memcard1", "serial");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_memcard2", "shared");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_drc", "enabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_neon_enhancement_enable", "enabled");
NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_neon_enhancement_no_main", "disabled");
NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_neon_enhancement_tex_adj_v2", "enabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_frameskip_type", "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_show_bios_bootlogo", "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_multitap", "disabled");
    }

    private void applyGameBoyLinkOptions() {
        if (core != CoreRegistry.GAME_BOY || "Not Connected".equals(gameBoyLinkMode)) return;
        NativeLibretro.nativeSetFrontendOption("gambatte_show_gb_link_settings", "enabled");
        NativeLibretro.nativeSetFrontendOption("gambatte_gb_link_mode", gameBoyLinkMode);
        NativeLibretro.nativeSetFrontendOption("gambatte_gb_link_network_port", String.valueOf(gameBoyLinkPort));
        if (!"Network Client".equals(gameBoyLinkMode) || gameBoyLinkHost.isEmpty()) return;
        String[] octets = gameBoyLinkHost.split("\\.");
        if (octets.length != 4) return;
        int index = 1;
        for (String octet : octets) {
            int value;
            try { value = Integer.parseInt(octet); }
            catch (Exception e) { return; }
            if (value < 0 || value > 255) return;
            String padded = String.format(java.util.Locale.US, "%03d", value);
            for (int i = 0; i < 3; i++) {
                NativeLibretro.nativeSetFrontendOption("gambatte_gb_link_network_server_ip_" + index, String.valueOf(padded.charAt(i)));
                index++;
            }
        }
    }

    public boolean isCoreReady() { return ready; }

    public void resetCore() {
        if (ready) resetRequested = true;
    }

    public void onResumeCore() {
        paused = false;
        maybeStart();
    }

    public void onPauseCore() {
        paused = true;
        audio.pause();
    }

    public boolean shutdownCoreAndWait(long timeoutMs) {
        running.set(false);
        paused = false;
        Thread t = thread;
        if (t != null) t.interrupt();
        try {
            return stopped.await(Math.max(100L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        maybeStart();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceReady = true;
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
    }

    private synchronized void maybeStart() {
        if (!configured.get() || !surfaceReady || running.get()) return;
        stopped = new CountDownLatch(1);
        running.set(true);
        thread = new Thread(this::runCore, "RetroLinkSoftwareCore");
        try { thread.setPriority(Thread.NORM_PRIORITY + 1); } catch (Throwable ignored) {}
        thread.start();
    }

    private void runCore() {
        try {
            NativeLibretro.nativeClearFrontendOptions();
            applyGameBoyLinkOptions();
            applyPs1Options();
            String linkLabel = "Not Connected".equals(gameBoyLinkMode) ? "" :
                    (" · " + ("Network Server".equals(gameBoyLinkMode) ? "LINK SERVER" : "LINK CLIENT"));
            postStats("Etapa 2/5 · cargando " + core.shortSystem + " · software framebuffer" + linkLabel);
            String error = NativeLibretro.nativeInit(corePath, romPath, systemDir, saveDir);
            if (error != null && !error.isEmpty()) {
                postError(error + " · etapa=" + NativeLibretro.nativeGetStage());
                return;
            }

            double fps = NativeLibretro.nativeGetFps();
            if (fps < 20 || fps > 120) fps = 60.0;
            int sampleRate = NativeLibretro.nativeGetSampleRate();
            audio.start(sampleRate);
            ready = true;
            final double coreFps = fps;
            final int coreRate = sampleRate;
            postReady(NativeLibretro.nativeGetCoreInfo(), coreFps, coreRate);

            long interval = (long)(1_000_000_000.0 / fps);
            long next = System.nanoTime();
            long statStart = next;
            int statFrames = 0;

            while (running.get()) {
                if (paused || !surfaceReady) {
                    audio.pause();
                    sleepMs(20);
                    next = System.nanoTime();
                    continue;
                }
                audio.resume();

                for (int p = 1; p <= 4; p++) {
                    InputHub.State in = InputHub.get(p);
                    NativeLibretro.nativeSetInput(p, in.mask, in.x, in.y);
                }
                if (resetRequested) {
                    NativeLibretro.nativeReset();
                    resetRequested = false;
                }

                if (!NativeLibretro.nativeRunFrame()) {
                    postError("El núcleo " + core.shortSystem + " dejó de ejecutar frames · etapa=" + NativeLibretro.nativeGetStage());
                    break;
                }

                int n = NativeLibretro.nativeDrainAudio(audioBuffer);
                if (n > 0) audio.write(audioBuffer, n);
                drawLatestFrame();

                statFrames++;
                long now = System.nanoTime();
                if (now - statStart >= 1_000_000_000L) {
                    double real = statFrames * 1_000_000_000.0 / Math.max(1L, now - statStart);
                    postStats(String.format(Locale.US,
                            "%s · %.1f/%.2f FPS · %dx%d · SOFTWARE · PSYNC",
                            core.shortSystem, real, fps,
                            NativeLibretro.nativeGetVideoWidth(), NativeLibretro.nativeGetVideoHeight()));
                    statFrames = 0;
                    statStart = now;
                }

                next += interval;
                long wait = next - System.nanoTime();
                if (wait > 0) sleepNs(wait);
                else if (wait < -interval * 3) next = System.nanoTime();
            }
        } catch (Throwable t) {
            postError(t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())
                    + " · etapa=" + safeStage());
        } finally {
            ready = false;
            audio.stop();
            try { NativeLibretro.nativeShutdown(); } catch (Throwable ignored) {}
            running.set(false);
            stopped.countDown();
        }
    }

    private void drawLatestFrame() {
        int w = NativeLibretro.nativeGetVideoWidth();
        int h = NativeLibretro.nativeGetVideoHeight();
        if (w <= 0 || h <= 0 || w > 2048 || h > 2048) return;

        int count = w * h;
        if (pixels == null || pixels.length != count) pixels = new int[count];
        long serial = NativeLibretro.nativeCopyVideoFrame(pixels);
        if (serial <= 0 || serial == lastFrameSerial) return;
        lastFrameSerial = serial;

        if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);

        // SNES comparte una consola/pantalla; P2 recibe el framebuffer del Host.
        if ((core == CoreRegistry.SNES || core == CoreRegistry.ATARI_2600
                || core == CoreRegistry.PS1)
                && FrameStreamServer.hasRemoteClients()) {
            try {
                Bitmap remote = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                if (remote != null) EmulatorFrameHub.publishOwned(remote);
            } catch (Throwable ignored) {}
        }

        Canvas canvas = null;
        try {
            canvas = getHolder().lockCanvas();
            if (canvas == null) return;
            canvas.drawColor(Color.BLACK);
            int dw = canvas.getWidth(), dh = canvas.getHeight();
            float scale = Math.min(dw / (float)w, dh / (float)h);
            int rw = Math.max(1, Math.round(w * scale));
            int rh = Math.max(1, Math.round(h * scale));
            int left = (dw - rw) / 2;
            int top = (dh - rh) / 2;
            canvas.drawBitmap(bitmap, null, new Rect(left, top, left + rw, top + rh), paint);
        } finally {
            if (canvas != null) {
                try { getHolder().unlockCanvasAndPost(canvas); } catch (Throwable ignored) {}
            }
        }
    }

    private String safeStage() {
        try { return NativeLibretro.nativeGetStage(); } catch (Throwable ignored) { return "unknown"; }
    }

    private void postReady(String info, double fps, int sampleRate) {
        EmulatorSurfaceView.Listener l = listener;
        if (l != null) post(() -> l.onCoreReady(info, fps, sampleRate));
    }

    private void postError(String error) {
        EmulatorSurfaceView.Listener l = listener;
        if (l != null) post(() -> l.onCoreError(error));
    }

    private void postStats(String stats) {
        EmulatorSurfaceView.Listener l = listener;
        if (l != null) post(() -> l.onStats(stats));
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static void sleepNs(long ns) {
        if (ns <= 0) return;
        long ms = ns / 1_000_000L;
        int extra = (int)(ns % 1_000_000L);
        try { Thread.sleep(ms, extra); } catch (InterruptedException ignored) {}
    }
}
