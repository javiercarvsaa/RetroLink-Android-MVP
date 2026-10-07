package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Emulación local SNES / host P1 usando Snes9x libretro. */
public class SnesGameActivity extends Activity implements EmulatorSurfaceView.Listener {
    public static final String EXTRA_ROM_PATH = "snes_rom_path";
    public static final String EXTRA_GAME_TITLE = "snes_game_title";
    public static final String EXTRA_HOST_SESSION = "snes_host_session";

    private SoftwareCoreView surface;
    private TextView title, status, stats;
    private View topBar;
    private Button hud;
    private SnesControlBinder controls;
    private boolean coreShutdown;
    private boolean hostSession;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_snes_game);
        hideSystemUi();

        String romPath = getIntent().getStringExtra(EXTRA_ROM_PATH);
        String gameTitle = getIntent().getStringExtra(EXTRA_GAME_TITLE);
        hostSession = getIntent().getBooleanExtra(EXTRA_HOST_SESSION, false);

        if (romPath == null || !new File(romPath).isFile()) {
            Toast.makeText(this, "Juego SNES no disponible", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        surface = findViewById(R.id.snesEmulatorSurface);
        title = findViewById(R.id.txtSnesGameTitle);
        status = findViewById(R.id.txtSnesGameStatus);
        stats = findViewById(R.id.txtSnesGameStats);
        topBar = findViewById(R.id.snesGameTopBar);
        hud = findViewById(R.id.btnSnesGameHud);

        title.setText("SNES · " + (gameTitle == null || gameTitle.trim().isEmpty()
                ? "JUEGO" : gameTitle.trim()));
        status.setText(hostSession
                ? "Snes9x · P1 HOST · preparando P2 remoto…"
                : "Snes9x · preparando núcleo…");

        if (!hostSession) {
            SessionState.reset();
            SessionState.setConfiguredPlayers(1);
            InputHub.resetAll();
        } else {
            SessionState.setConfiguredPlayers(Math.max(2, SessionState.getPlayerCount()));
            InputHub.resetPlayer(1);
        }
        EmulatorFrameHub.clear();

        controls = new SnesControlBinder(this, (mask, x, y) -> InputHub.set(1, mask, x, y));

        findViewById(R.id.btnSnesGameExit).setOnClickListener(v -> finish());
        findViewById(R.id.btnSnesGameReset).setOnClickListener(v -> surface.resetCore());
        findViewById(R.id.btnSnesGameSettings).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        hud.setOnClickListener(v -> setHudVisible(
                topBar == null || topBar.getVisibility() != View.VISIBLE));

        File systemDir = new File(getFilesDir(), "cores/snes.snes9x/system");
        File saveDir = new File(getFilesDir(), "cores/snes.snes9x/saves");
        systemDir.mkdirs();
        saveDir.mkdirs();

        String corePath = getApplicationInfo().nativeLibraryDir + "/" + CoreRegistry.SNES.libraryFile;
        surface.configure(CoreRegistry.SNES, corePath, romPath,
                systemDir.getAbsolutePath(), saveDir.getAbsolutePath(), this);

        setHudVisible(false);
    }

    private void setHudVisible(boolean visible) {
        if (topBar != null) topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (hud != null) {
            hud.setText(visible ? "×" : "⚙");
            hud.setAlpha(visible ? 0.98f : 0.78f);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (SnesGamepadMapper.handleKeyEvent(this, controls, event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (SnesGamepadMapper.handleMotionEvent(this, controls, event)) return true;
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public void onCoreReady(String coreInfo, double fps, int sampleRate) {
        status.setText(hostSession
                ? "SNES · " + coreInfo + " · P1 HOST / P2 REMOTO"
                : "SNES · " + coreInfo);
        stats.setText(String.format(java.util.Locale.US,
                "%.2f FPS · %d Hz · SOFTWARE SAFE", fps, sampleRate));
        Toast.makeText(this, "SNES listo", Toast.LENGTH_SHORT).show();
    }

    @Override public void onCoreError(String error) {
        status.setText("ERROR · " + error);
        setHudVisible(true);
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    @Override public void onStats(String s) {
        stats.setText(s);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (surface != null && !coreShutdown) surface.onResumeCore();
    }

    @Override protected void onPause() {
        if (controls != null) controls.release();
        if (surface != null) surface.onPauseCore();
        if (surface != null && isFinishing() && !coreShutdown)
            coreShutdown = surface.shutdownCoreAndWait(1800L);
        super.onPause();
    }

    @Override protected void onDestroy() {
        InputHub.resetPlayer(1);
        EmulatorFrameHub.clear();
        if (surface != null && !coreShutdown)
            coreShutdown = surface.shutdownCoreAndWait(1800L);
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
