package cl.retrolink.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.View;
import android.content.Intent;
import android.widget.FrameLayout;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import cl.retrolink.app.ble.ControllerBleManager;
import cl.retrolink.app.net.FrameStreamClient;

public class ControllerActivity extends Activity implements ControllerBleManager.Listener, FrameStreamClient.Listener {
    private ControllerBleManager ble;
    private FrameStreamClient video;
    private RemoteFrameView screen;
    private TextView status, videoStatus, inputStatus, playerTitle;
    private Button viewButton, hudButton;
    private View topBar, viewportPanel;
    private TextView viewportLabel;
    private FrameLayout controlRoot;
    private N64ControlBinder controls;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int player;
    private String lastHostIp = "";
    private boolean redirectingToGameBoy;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_controller);
        hideSystemUi();

        status = findViewById(R.id.txtControllerStatus);
        videoStatus = findViewById(R.id.txtVideoStatus);
        inputStatus = findViewById(R.id.txtInputStatus);
        playerTitle = findViewById(R.id.txtControllerPlayerTitle);
        screen = findViewById(R.id.remoteFrameView);
        viewButton = findViewById(R.id.btnViewMode);
        hudButton = findViewById(R.id.btnControllerHud);
        topBar = findViewById(R.id.controllerTopBar);
        viewportPanel = findViewById(R.id.controllerViewportPanel);
        viewportLabel = findViewById(R.id.txtControllerViewport);
        controlRoot = findViewById(R.id.controllerRoot);
        ble = new ControllerBleManager(this, this);
        video = new FrameStreamClient(this);
        controls = new N64ControlBinder(this, (m, x, y) -> {
            ble.sendInput(m, x, y);
            if (player > 0) inputStatus.setText("P" + player + " → núcleo N64 · " + cl.retrolink.app.ble.BleProtocol.buttonsToText(m));
        });
        findViewById(R.id.btnFindHost).setOnClickListener(v -> ble.startScan());
        viewButton.setOnClickListener(v -> toggleView());
        hudButton.setOnClickListener(v -> setHudVisible(topBar == null || topBar.getVisibility() != View.VISIBLE, true));
        findViewById(R.id.btnControllerViewport).setOnClickListener(v -> toggleViewportPanel());
        findViewById(R.id.btnControllerViewLeft).setOnClickListener(v -> adjustViewport(-0.20f, 0f, 0f));
        findViewById(R.id.btnControllerViewRight).setOnClickListener(v -> adjustViewport(0.20f, 0f, 0f));
        findViewById(R.id.btnControllerViewUp).setOnClickListener(v -> adjustViewport(0f, -0.20f, 0f));
        findViewById(R.id.btnControllerViewDown).setOnClickListener(v -> adjustViewport(0f, 0.20f, 0f));
        findViewById(R.id.btnControllerViewZoomOut).setOnClickListener(v -> adjustViewport(0f, 0f, -0.02f));
        findViewById(R.id.btnControllerViewZoomIn).setOnClickListener(v -> adjustViewport(0f, 0f, 0.02f));
        findViewById(R.id.btnControllerViewReset).setOnClickListener(v -> { if (screen != null) screen.resetViewport(); updateViewportLabel(); });
        findViewById(R.id.btnControllerViewDone).setOnClickListener(v -> { if (viewportPanel != null) viewportPanel.setVisibility(View.GONE); });
        findViewById(R.id.btnControllerSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnControllerEditControls).setOnClickListener(v -> {
            Intent i = new Intent(this, ControlLayoutActivity.class);
            i.putExtra(ControlLayoutActivity.EXTRA_SCOPE, ControlLayoutStore.SCOPE_N64_REMOTE_LANDSCAPE);
            startActivity(i);
        });
        if (controlRoot != null) ControlLayoutStore.applyAll(this, controlRoot, ControlLayoutStore.SCOPE_N64_REMOTE_LANDSCAPE);
        // Antes de enlazar necesitamos ver CONECTAR. Tras conectar se colapsa a HUD limpio.
        setHudVisible(true, false);
        screen.setPlayerView(false);
        viewButton.setText("FULL");
        screen.refreshRetroSr();
    }

    private void toggleView() {
        boolean next = !screen.isPlayerView();
        screen.setPlayerView(next);
        if (video != null) video.setPlayerView(next);
        viewButton.setText(next ? (player > 0 ? "PLAYER P" + player : "PLAYER") : "FULL");
    }


    private void toggleViewportPanel() {
        if (screen == null || viewportPanel == null) return;
        if (!screen.isPlayerView()) {
            screen.setPlayerView(true);
            if (video != null) video.setPlayerView(true);
            viewButton.setText(player > 0 ? "PLAYER P" + player : "PLAYER");
        }
        boolean show = viewportPanel.getVisibility() != View.VISIBLE;
        viewportPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            // Mantener el panel por encima de los controles táctiles para que sus
            // botones siempre reciban el toque en P2-P4.
            viewportPanel.bringToFront();
            if (hudButton != null) hudButton.bringToFront();
            updateViewportLabel();
        }
    }

    private void adjustViewport(float dx, float dy, float dz) {
        if (screen == null) return;
        if (dx != 0f || dy != 0f) screen.nudgeViewport(dx, dy);
        if (dz != 0f) screen.changeViewportZoom(dz);
        screen.invalidate();
        updateViewportLabel();
    }

    private void updateViewportLabel() {
        if (viewportLabel != null && screen != null) viewportLabel.setText(screen.viewportLabel());
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

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (screen != null) screen.refreshRetroSr();
        if (controlRoot != null) ControlLayoutStore.applyAll(this, controlRoot, ControlLayoutStore.SCOPE_N64_REMOTE_LANDSCAPE);
        if (player > 0) setHudVisible(RetroPreferences.gameHudVisible(this), false);
    }

    @Override protected void onPause() {
        if (controls != null) controls.release();
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (video != null) video.stop();
        if (ble != null) ble.disconnect();
        super.onDestroy();
    }

    @Override public void onStatus(String s) { status.setText(s); }

    @Override public void onConnected(int p) {
        player = p;
        screen.setPlayer(p);
        if (playerTitle != null) playerTitle.setText("PLAYER P" + p);
        updateViewportLabel();
        status.setText("P" + p + " · BLE LISTO");
        inputStatus.setText("P" + p + " conectado al núcleo N64 del Host");
        videoStatus.setText("Solicitando IP del Host…");
        Toast.makeText(this, "Asignado como P" + p, Toast.LENGTH_SHORT).show();
        // Vista FULL al iniciar evita recortar menús/pantallas compartidas antes de que
        // el juego entre realmente en split-screen. PLAYER queda bajo control del usuario.
        screen.setPlayerView(false);
        if (video != null) video.setPlayerView(false);
        viewButton.setText("FULL");
        main.postDelayed(() -> setHudVisible(RetroPreferences.gameHudVisible(this), false), 1200);
    }

    @Override public void onSessionInfo(String sessionType, String ip) {
        if (!"GBLINK".equalsIgnoreCase(sessionType) || redirectingToGameBoy) return;

        redirectingToGameBoy = true;
        if (video != null) video.stop();

        GameBoyRomRepository.ImportedGame gb = GameBoyRomRepository.lastGame(this);
        if (gb == null || !gb.file.isFile()) {
            status.setText("Sala Game Boy Link detectada");
            videoStatus.setText("Selecciona el mismo juego GB/GBC para entrar como P2");
            inputStatus.setText("No se usará la pantalla remota N64");
            Toast.makeText(this,
                    "Sala GB/GBC detectada. Selecciona el mismo juego en Biblioteca Game Boy.",
                    Toast.LENGTH_LONG).show();
            if (ble != null) ble.disconnect();
            startActivity(new Intent(this, GameBoyLibraryActivity.class));
            finish();
            return;
        }

        status.setText("P2 · GAME BOY LINK");
        videoStatus.setText("Host " + ip + " · abriendo Gambatte local…");
        inputStatus.setText("A/B · START · SELECT · control local");

        Intent i = new Intent(this, IntegratedGameActivity.class);
        i.putExtra(IntegratedGameActivity.EXTRA_CORE_ID, CoreRegistry.GAME_BOY.id);
        i.putExtra(IntegratedGameActivity.EXTRA_ROM_PATH, gb.file.getAbsolutePath());
        i.putExtra(IntegratedGameActivity.EXTRA_GAME_TITLE, gb.title);
        i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_MODE, "Network Client");
        i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_HOST, ip == null ? "" : ip.trim());
        i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_PORT, 56400);

        if (ble != null) ble.disconnect();
        startActivity(i);
        finish();
    }

    @Override public void onHostInfo(String ip) {
        if (redirectingToGameBoy) return;
        if (ip == null || ip.trim().isEmpty()) {
            videoStatus.setText("BLE OK · esperando IP Wi‑Fi del Host…");
            main.postDelayed(() -> { if (ble != null) ble.requestHostInfo(); }, 1200);
            return;
        }
        lastHostIp = ip.trim();
        videoStatus.setText("IP Host " + lastHostIp + " · conectando video…");
        video.start(lastHostIp, Math.max(1, player), screen.isPlayerView());
    }

    @Override public void onDisconnected() {
        player = 0;
        if (playerTitle != null) playerTitle.setText("PLAYER REMOTO");
        lastHostIp = "";
        if (video != null) video.stop();
        videoStatus.setText("Video desconectado");
        inputStatus.setText("Entrada N64 desconectada");
        setHudVisible(true, false);
    }

    @Override public void onLog(String l) {}
    @Override public void onFrame(Bitmap b, int players, boolean preCropped, boolean dk64Raw) {
        screen.setDk64Raw(dk64Raw);
        screen.setFrame(b, players, preCropped);
    }

    private void setHudVisible(boolean visible, boolean persist) {
        if (topBar != null) {
            topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) topBar.bringToFront();
        }
        if (!visible && viewportPanel != null) viewportPanel.setVisibility(View.GONE);
        if (hudButton != null) {
            hudButton.setText(visible ? "×" : "⚙");
            hudButton.setContentDescription(visible ? "Ocultar menú del jugador" : "Mostrar menú del jugador");
            hudButton.setAlpha(visible ? 0.98f : 0.82f);
            hudButton.bringToFront();
        }
        if (persist && player > 0) RetroPreferences.setGameHudVisible(this, visible);
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

    @Override public void onVideoStatus(String s) {
        videoStatus.setText(s);
        if (s != null && s.startsWith("Video desconectado")) {
            main.postDelayed(() -> { if (ble != null) ble.requestHostInfo(); }, 1200);
        }
    }
}
