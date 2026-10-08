package cl.retrolink.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.HandlerThread;
import android.os.Process;
import android.content.Intent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import cl.retrolink.app.net.FrameStreamServer;

public class IntegratedN64Activity extends Activity implements EmulatorSurfaceView.Listener {
    public static final String EXTRA_ROM_PATH = "rom_path";
    public static final String EXTRA_PLAYERS = "players";

    private EmulatorSurfaceView surface;
    private FrameLayout viewport, crop, controlRoot;
    private TextView status, stats;
    private View topBar, viewportPanel, settingsPanel;
    private TextView viewportLabel, settingsSummary;
    private Button viewBtn, hudBtn, settingsBtn;
    private N64ControlBinder controls;
    private int playerCount = 1;
    private boolean playerView;
    private boolean capturePending;
    private boolean coreShutdown;
    private AdaptiveOptimizationEngine.Plan adaptivePlan;
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread captureThread;
    private Handler captureHandler;

    private final Runnable captureTick = new Runnable() {
        @Override public void run() {
            syncSessionPlayers();
            captureForRemotePlayers();
            main.postDelayed(this, Math.max(16, 1000 / Math.max(15, RetroPreferences.resolvedStreamFps(IntegratedN64Activity.this))));
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_integrated_n64);
        hideSystemUi();
        captureThread = new HandlerThread("RetroLinkPixelCopy", Process.THREAD_PRIORITY_DISPLAY);
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        surface = findViewById(R.id.emulatorSurface);
        viewport = findViewById(R.id.emulatorViewport);
        crop = findViewById(R.id.emulatorCrop);
        controlRoot = findViewById(R.id.emulatorRoot);
        status = findViewById(R.id.txtEmuStatus);
        stats = findViewById(R.id.txtEmuStats);
        topBar = findViewById(R.id.emuTopBar);
        viewportPanel = findViewById(R.id.emuViewportPanel);
        viewportLabel = findViewById(R.id.txtEmuViewport);
        settingsPanel = findViewById(R.id.emuSettingsPanel);
        settingsSummary = findViewById(R.id.txtEmuSettingsSummary);
        viewBtn = findViewById(R.id.btnEmuView);
        hudBtn = findViewById(R.id.btnEmuHud);
        settingsBtn = findViewById(R.id.btnEmuSettings);
        playerCount = Math.max(1, Math.min(4, getIntent().getIntExtra(EXTRA_PLAYERS, SessionState.getPlayerCount())));
        SessionState.setConfiguredPlayers(playerCount);

        String romPath = getIntent().getStringExtra(EXTRA_ROM_PATH);
        if (romPath == null || !new File(romPath).isFile()) {
            Toast.makeText(this, "ROM N64 no disponible", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        File systemDir = new File(getFilesDir(), "cores/n64/system");
        File saveDir = new File(getFilesDir(), "cores/n64/saves");
        systemDir.mkdirs();
        saveDir.mkdirs();
        String corePath = getApplicationInfo().nativeLibraryDir + "/" + CoreRegistry.N64.libraryFile;
        adaptivePlan = AdaptiveOptimizationEngine.resolve(
                this, CoreRegistry.N64, romPath, playerCount > 1);
        surface.setAdaptivePlan(adaptivePlan);

        controls = new N64ControlBinder(this, (mask, x, y) -> InputHub.set(1, mask, x, y));
        if (controlRoot != null) ControlLayoutStore.applyAll(this, controlRoot, ControlLayoutStore.SCOPE_N64_LANDSCAPE);
        findViewById(R.id.btnEmuExit).setOnClickListener(v -> finish());
        findViewById(R.id.btnEmuReset).setOnClickListener(v -> surface.resetCore());
        viewBtn.setOnClickListener(v -> togglePlayerView());
        hudBtn.setOnClickListener(v -> setHudVisible(topBar == null || topBar.getVisibility() != View.VISIBLE));
        settingsBtn.setOnClickListener(v -> toggleSettingsPanel());
        findViewById(R.id.btnEmuViewportAdjust).setOnClickListener(v -> toggleViewportPanel());
        findViewById(R.id.btnEmuViewLeft).setOnClickListener(v -> adjustViewport(-0.20f, 0f, 0f));
        findViewById(R.id.btnEmuViewRight).setOnClickListener(v -> adjustViewport(0.20f, 0f, 0f));
        findViewById(R.id.btnEmuViewUp).setOnClickListener(v -> adjustViewport(0f, -0.20f, 0f));
        findViewById(R.id.btnEmuViewDown).setOnClickListener(v -> adjustViewport(0f, 0.20f, 0f));
        findViewById(R.id.btnEmuViewZoomOut).setOnClickListener(v -> adjustViewport(0f, 0f, -0.02f));
        findViewById(R.id.btnEmuViewZoomIn).setOnClickListener(v -> adjustViewport(0f, 0f, 0.02f));
        findViewById(R.id.btnEmuViewReset).setOnClickListener(v -> { PlayerViewportPreferences.reset(this, false, 1); applyPlayerTransform(); updateViewportLabel(); });
        findViewById(R.id.btnEmuViewDone).setOnClickListener(v -> { if (viewportPanel != null) viewportPanel.setVisibility(View.GONE); });
        findViewById(R.id.btnEmuGraphics).setOnClickListener(v -> {
            if (OptimizationProfileStore.mode(this) == OptimizationProfileStore.MODE_OFF) {
                RetroPreferences.setGraphicsProfile(this, (RetroPreferences.graphicsProfile(this) + 1) % 3);
                int manualProfile = RetroPreferences.graphicsProfile(this);
                surface.setPerformanceProfile(manualProfile);
            } else {
                Toast.makeText(this, "Adaptive Core aplicará el perfil al próximo inicio", Toast.LENGTH_SHORT).show();
            }
            refreshGameSettingsPanel();
        });
        findViewById(R.id.btnEmuRetroSr).setOnClickListener(v -> {
            RetroPreferences.nextRetroSrMode(this);
            refreshGameSettingsPanel();
        });
        findViewById(R.id.btnEmuSplitProfile).setOnClickListener(v -> {
            RetroPreferences.nextSplitCropMode(this);
            applyPlayerTransform();
            refreshGameSettingsPanel();
        });
        findViewById(R.id.btnEmuAdvancedSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnEmuSettingsClose).setOnClickListener(v -> { if (settingsPanel != null) settingsPanel.setVisibility(View.GONE); });
        findViewById(R.id.btnEmuEditControls).setOnClickListener(v -> startActivity(new Intent(this, ControlLayoutActivity.class)));
        setHudVisible(RetroPreferences.gameHudVisible(this), false);
        updateViewButton();
        refreshGameSettingsPanel();

        surface.setPerformanceProfile(adaptivePlan == null ? RetroPreferences.graphicsProfile(this) : adaptivePlan.n64RenderProfile);
        viewport.post(this::fitSurfaceFourByThree);
        String preload = NativeLibretro.corePreloadError();
        status.setText(preload.isEmpty() ? "N64 · preparando núcleo integrado…" : "N64 · pre-carga pendiente · " + preload);
        stats.setText("Etapa 1/5 · OpenGL ES 3 · " + (adaptivePlan == null ? RetroPreferences.graphicsProfileLabel(this) : adaptivePlan.detailedLabel()));
        surface.configure(corePath, romPath, systemDir.getAbsolutePath(), saveDir.getAbsolutePath(), this);
        main.post(captureTick);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (AndroidGamepadMapper.handleKeyEvent(this, controls, event)) return true;
        if (HardwareButtonMapper.handle(this, controls, event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (AndroidGamepadMapper.handleMotionEvent(this, controls, event)) return true;
        return super.dispatchGenericMotionEvent(event);
    }

    private void fitSurfaceFourByThree() {
        int pw = viewport.getWidth();
        int ph = viewport.getHeight();
        if (pw <= 0 || ph <= 0 || crop == null || surface == null) return;
        int w = Math.min(pw, Math.round(ph * 4f / 3f));
        int h = Math.min(ph, Math.round(w * 3f / 4f));

        // v0.6.0: el recorte 4:3 es un contenedor real. Al ampliar P1 en split-screen,
        // el resto de jugadores queda físicamente fuera del clip y no puede sangrar.
        FrameLayout.LayoutParams cropLp = new FrameLayout.LayoutParams(w, h, Gravity.CENTER);
        crop.setLayoutParams(cropLp);
        crop.setClipChildren(true);
        crop.setClipToPadding(true);

        FrameLayout.LayoutParams surfaceLp = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.LEFT);
        surface.setLayoutParams(surfaceLp);
        crop.post(this::applyPlayerTransform);
    }

    private void syncSessionPlayers() {
        int count = SessionState.getPlayerCount();
        count = Math.max(1, Math.min(4, count));
        if (count == playerCount) return;
        playerCount = count;
        if (playerCount <= 1) playerView = false;
        updateViewButton();
        applyPlayerTransform();
        status.setText("N64 INTEGRADO · sesión P1–P" + playerCount);
    }

    private void togglePlayerView() {
        syncSessionPlayers();
        if (playerCount <= 1) {
            playerView = false;
            updateViewButton();
            applyPlayerTransform();
            Toast.makeText(this, "Conecta P2 o configura 2P–4P para activar RACE P1.", Toast.LENGTH_SHORT).show();
            return;
        }
        playerView = !playerView;
        updateViewButton();
        applyPlayerTransform();
    }


    private void toggleViewportPanel() {
        syncSessionPlayers();
        if (settingsPanel != null) settingsPanel.setVisibility(View.GONE);
        if (playerCount <= 1) {
            Toast.makeText(this, "El ajuste de pantalla se usa en vista PLAYER con 2P–4P.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!playerView) {
            playerView = true;
            updateViewButton();
            applyPlayerTransform();
        }
        boolean show = viewportPanel != null && viewportPanel.getVisibility() != View.VISIBLE;
        if (viewportPanel != null) viewportPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) updateViewportLabel();
    }

    private void adjustViewport(float dx, float dy, float dz) {
        float x = PlayerViewportPreferences.offsetX(this, false, 1) + dx;
        float y = PlayerViewportPreferences.offsetY(this, false, 1) + dy;
        float z = PlayerViewportPreferences.zoom(this, false, 1) + dz;
        if ((Math.abs(x) > 0.001f || Math.abs(y) > 0.001f) && z < 1.04f) z = 1.04f;
        PlayerViewportPreferences.set(this, false, 1, x, y, z);
        applyPlayerTransform();
        updateViewportLabel();
    }

    private void updateViewportLabel() {
        if (viewportLabel != null) viewportLabel.setText(PlayerViewportPreferences.label(this, false, 1));
    }

    private void toggleSettingsPanel() {
        if (settingsPanel == null) return;
        if (viewportPanel != null) viewportPanel.setVisibility(View.GONE);
        boolean show = settingsPanel.getVisibility() != View.VISIBLE;
        settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            refreshGameSettingsPanel();
            settingsPanel.bringToFront();
            if (hudBtn != null) hudBtn.bringToFront();
        }
    }

    private void refreshGameSettingsPanel() {
        Button gfx = findViewById(R.id.btnEmuGraphics);
        Button sr = findViewById(R.id.btnEmuRetroSr);
        Button split = findViewById(R.id.btnEmuSplitProfile);
        if (gfx != null) gfx.setText("GRÁFICOS  ·  " + RetroPreferences.graphicsProfileLabel(this));
        if (sr != null) sr.setText("RETROSR  ·  " + RetroPreferences.retroSrModeLabel(this));
        if (split != null) split.setText("SPLIT-SCREEN  ·  " + RetroPreferences.splitCropModeLabel(this));
        if (settingsSummary != null) {
            settingsSummary.setText("P1–P" + Math.max(1, playerCount) + " · "
                    + RetroPreferences.streamProfileLabel(this));
        }
    }

    private void setHudVisible(boolean visible) {
        setHudVisible(visible, true);
    }

    private void setHudVisible(boolean visible, boolean persist) {
        if (topBar != null) {
            topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) topBar.bringToFront();
        }
        if (!visible) {
            if (viewportPanel != null) viewportPanel.setVisibility(View.GONE);
            if (settingsPanel != null) settingsPanel.setVisibility(View.GONE);
        }
        if (hudBtn != null) {
            hudBtn.setText(visible ? "×" : "⚙");
            hudBtn.setContentDescription(visible ? "Ocultar menú de juego" : "Mostrar menú de juego");
            hudBtn.setAlpha(visible ? 0.98f : 0.82f);
            hudBtn.bringToFront();
        }
        if (persist) RetroPreferences.setGameHudVisible(this, visible);
    }

    private void updateViewButton() {
        if (viewBtn == null) return;
        if (playerCount <= 1) {
            viewBtn.setText("FULL 1P");
            viewBtn.setAlpha(0.72f);
        } else {
            viewBtn.setText(playerView ? "RACE P1" : "FULL " + playerCount + "P");
            viewBtn.setAlpha(1f);
        }
    }

    private void applyPlayerTransform() {
        if (surface == null || crop == null) return;
        if (playerCount <= 1) playerView = false;

        surface.setPivotX(0f);
        surface.setPivotY(0f);
        if (!playerView) {
            surface.setScaleX(1f);
            surface.setScaleY(1f);
            surface.setTranslationX(0f);
            surface.setTranslationY(0f);
            return;
        }

        final float overscan = RetroPreferences.splitOverscan(this);
        final float manualZoom = PlayerViewportPreferences.zoom(this, false, 1);
        final float manualX = PlayerViewportPreferences.offsetX(this, false, 1);
        final float manualY = PlayerViewportPreferences.offsetY(this, false, 1);
        final float baseScaleX = playerCount <= 2 ? overscan : 2f * overscan;
        final float baseScaleY = 2f * overscan;
        final float sx = baseScaleX * manualZoom;
        final float sy = baseScaleY * manualZoom;
        surface.setScaleX(sx);
        surface.setScaleY(sy);

        // Base automática + ajuste manual persistente. Los offsets representan
        // desplazamiento visual del cuadro; el zoom aporta margen para no mostrar bordes.
        float extraX = Math.max(0f, crop.getWidth() * (overscan - 1f) * 0.5f);
        float extraY = Math.max(0f, crop.getHeight() * (overscan - 1f) * 0.5f);
        float zoomExtraX = crop.getWidth() * Math.max(0f, manualZoom - 1f) * 0.5f;
        float zoomExtraY = crop.getHeight() * Math.max(0f, manualZoom - 1f) * 0.5f;
        float panX = manualX * crop.getWidth() * 0.08f;
        float panY = manualY * crop.getHeight() * 0.08f;
        surface.setTranslationX(-extraX - zoomExtraX + panX);
        surface.setTranslationY(-extraY - zoomExtraY + panY);
    }

    private void captureForRemotePlayers() {
        if (!FrameStreamServer.hasRemoteClients()) return;
        if (capturePending || coreShutdown || surface == null || !surface.isCoreReady() || isFinishing() || isDestroyed()) return;
        capturePending = true;
        int w = RetroPreferences.streamWidth(this);
        int h = RetroPreferences.streamHeight(this);
        Bitmap out;
        try { out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); }
        catch (Throwable t) { capturePending = false; return; }
        try {
            PixelCopy.request(surface, out, result -> {
                capturePending = false;
                if (result == PixelCopy.SUCCESS) {
                    EmulatorFrameHub.publishOwned(out);
                } else if (!out.isRecycled()) {
                    out.recycle();
                }
            }, captureHandler != null ? captureHandler : main);
        } catch (Throwable t) {
            capturePending = false;
            if (!out.isRecycled()) out.recycle();
        }
    }

    @Override public void onCoreReady(String coreInfo, double fps, int sampleRate) {
        syncSessionPlayers();
        SessionState.setContentFps(fps);
        status.setText("N64 INTEGRADO · " + coreInfo);
        stats.setText(String.format(java.util.Locale.US,
                "Objetivo %.2f FPS · %s · %s · RetroSR %s · P1–P%d",
                fps, RetroPreferences.graphicsProfileLabel(this), surface.getGameProfileLabel(),
                SessionState.isDk64Profile() ? "RAW VI/FB" : (RetroPreferences.retroSrEnabled(this) ? "2 ON" : "OFF"), playerCount));
        Toast.makeText(this, "N64 ejecutándose dentro de RetroLink", Toast.LENGTH_LONG).show();
    }

    @Override public void onCoreError(String error) {
        status.setText("ERROR N64 · " + error);
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    @Override public void onStats(String s) {
        syncSessionPlayers();
        stats.setText(s + " · " + surface.getGameProfileLabel() + " · P1–P" + playerCount);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (surface != null && !coreShutdown) {
            surface.setPerformanceProfile(adaptivePlan == null ? RetroPreferences.graphicsProfile(this) : adaptivePlan.n64RenderProfile);
            surface.onResume();
            applyPlayerTransform();
        }
        refreshGameSettingsPanel();
        if (controlRoot != null) ControlLayoutStore.applyAll(this, controlRoot, ControlLayoutStore.SCOPE_N64_LANDSCAPE);
        main.removeCallbacks(captureTick);
        if (!coreShutdown) main.post(captureTick);
    }

    @Override protected void onPause() {
        if (controls != null) controls.release();
        main.removeCallbacks(captureTick);

        // Si SALIR/Back está cerrando la Activity, el core debe descargarse
        // ANTES de pausar/destruir el hilo GL. Esto evita el crash histórico
        // de teardown observado por Android.
        if (surface != null && isFinishing() && !coreShutdown) {
            coreShutdown = surface.shutdownCoreAndWait(1800L);
        }
        if (surface != null) surface.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        capturePending = false;
        InputHub.resetPlayer(1);
        EmulatorFrameHub.clear();

        // Fallback idempotente. En cierre normal ya ocurrió antes de onPause().
        if (surface != null && !coreShutdown) {
            coreShutdown = surface.shutdownCoreAndWait(1200L);
        }

        if (captureThread != null) {
            try { captureThread.quitSafely(); } catch (Throwable ignored) {}
            captureThread = null;
            captureHandler = null;
        }
        super.onDestroy();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
