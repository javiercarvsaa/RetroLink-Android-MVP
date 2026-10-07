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

/** Emulación local/Host PlayStation con PCSX-ReARMed. */
public class Ps1GameActivity extends Activity implements EmulatorSurfaceView.Listener {
    public static final String EXTRA_ROM_PATH = "ps1_rom_path";
    public static final String EXTRA_GAME_TITLE = "ps1_game_title";
    public static final String EXTRA_HOST_SESSION = "ps1_host_session";

    private SoftwareCoreView surface;
    private TextView title, status, stats;
    private View topBar;
    private Button hud;
    private Ps1ControlBinder controls;
    private boolean coreShutdown;
    private boolean hostSession;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ps1_game);
        hideSystemUi();

        String romPath = getIntent().getStringExtra(EXTRA_ROM_PATH);
        String gameTitle = getIntent().getStringExtra(EXTRA_GAME_TITLE);
        hostSession = getIntent().getBooleanExtra(EXTRA_HOST_SESSION, false);

        if (romPath == null || !new File(romPath).isFile()) {
            Toast.makeText(this, "Juego PS1 no disponible", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        surface = findViewById(R.id.ps1EmulatorSurface);
        title = findViewById(R.id.txtPs1GameTitle);
        status = findViewById(R.id.txtPs1GameStatus);
        stats = findViewById(R.id.txtPs1GameStats);
        topBar = findViewById(R.id.ps1GameTopBar);
        hud = findViewById(R.id.btnPs1GameHud);

        title.setText("PLAYSTATION · "
                + (gameTitle == null || gameTitle.trim().isEmpty()
                ? "JUEGO" : gameTitle.trim()));

        status.setText((hostSession
                ? "PCSX-ReARMed · P1 HOST · preparando P2…"
                : "PCSX-ReARMed · preparando núcleo…")
                + "\n" + Ps1BiosManager.status(this));

        if (!hostSession) {
            SessionState.reset();
            SessionState.setConfiguredPlayers(1);
            InputHub.resetAll();
        } else {
            SessionState.setConfiguredPlayers(Math.max(2, SessionState.getPlayerCount()));
            InputHub.resetPlayer(1);
        }
        EmulatorFrameHub.clear();

        controls = new Ps1ControlBinder(this,
                (mask, x, y) -> InputHub.set(1, mask, x, y));

        findViewById(R.id.btnPs1GameExit).setOnClickListener(v -> finish());
        findViewById(R.id.btnPs1GameReset).setOnClickListener(v -> surface.resetCore());
        findViewById(R.id.btnPs1GameSettings).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        hud.setOnClickListener(v -> setHudVisible(
                topBar == null || topBar.getVisibility() != View.VISIBLE));

        File systemDir = Ps1BiosManager.systemDir(this);
        File saveDir = new File(getFilesDir(), "cores/ps1.pcsx_rearmed/saves");
        saveDir.mkdirs();

        String corePath = getApplicationInfo().nativeLibraryDir + "/"
                + CoreRegistry.PS1.libraryFile;

        surface.configurePs1Defaults();
        surface.configure(CoreRegistry.PS1,
                corePath,
                romPath,
                systemDir.getAbsolutePath(),
                saveDir.getAbsolutePath(),
                this);

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
        if (Ps1GamepadMapper.handleKeyEvent(this, controls, event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (Ps1GamepadMapper.handleMotionEvent(this, controls, event)) return true;
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public void onCoreReady(String coreInfo, double fps, int sampleRate) {
        status.setText((hostSession
                ? "PS1 · " + coreInfo + " · P1 HOST / P2 REMOTO"
                : "PS1 · " + coreInfo)
                + "\n" + Ps1BiosManager.status(this));
        stats.setText(String.format(java.util.Locale.US,
                "%.2f FPS · %d Hz · PCSX SOFTWARE · MEMCARD ACTIVA",
                fps, sampleRate));
        Toast.makeText(this, "PlayStation lista", Toast.LENGTH_SHORT).show();
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
            coreShutdown = surface.shutdownCoreAndWait(2200L);
        super.onPause();
    }

    @Override protected void onDestroy() {
        InputHub.resetPlayer(1);
        EmulatorFrameHub.clear();
        if (surface != null && !coreShutdown)
            coreShutdown = surface.shutdownCoreAndWait(2200L);
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
