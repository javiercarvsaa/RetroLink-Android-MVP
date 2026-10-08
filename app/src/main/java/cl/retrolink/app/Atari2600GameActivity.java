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

/** Emulación Atari 2600 local/Host P1 con Stella 2014 libretro. */
public class Atari2600GameActivity extends Activity
        implements EmulatorSurfaceView.Listener {

    public static final String EXTRA_ROM_PATH = "atari2600_rom_path";
    public static final String EXTRA_GAME_TITLE = "atari2600_game_title";
    public static final String EXTRA_HOST_SESSION = "atari2600_host_session";

    private SoftwareCoreView surface;
    private TextView title, status, stats;
    private View topBar;
    private Button hud;
    private Atari2600ControlBinder controls;
    private boolean coreShutdown;
    private boolean hostSession;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_atari2600_game);
        hideSystemUi();

        String romPath = getIntent().getStringExtra(EXTRA_ROM_PATH);
        String gameTitle = getIntent().getStringExtra(EXTRA_GAME_TITLE);
        hostSession = getIntent().getBooleanExtra(EXTRA_HOST_SESSION, false);

        if (romPath == null || !new File(romPath).isFile()) {
            Toast.makeText(this, "Juego Atari 2600 no disponible",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        surface = findViewById(R.id.atariEmulatorSurface);
        title = findViewById(R.id.txtAtariGameTitle);
        status = findViewById(R.id.txtAtariGameStatus);
        stats = findViewById(R.id.txtAtariGameStats);
        topBar = findViewById(R.id.atariGameTopBar);
        hud = findViewById(R.id.btnAtariGameHud);

        title.setText("ATARI 2600 · "
                + (gameTitle == null || gameTitle.trim().isEmpty()
                ? "JUEGO" : gameTitle.trim()));

        status.setText(hostSession
                ? "Stella 2014 · P1 HOST · preparando P2 remoto…"
                : "Stella 2014 · preparando núcleo…");

        if (!hostSession) {
            SessionState.reset();
            SessionState.setConfiguredPlayers(1);
            InputHub.resetAll();
        } else {
            SessionState.setConfiguredPlayers(
                    Math.max(2, SessionState.getPlayerCount()));
            InputHub.resetPlayer(1);
        }
        EmulatorFrameHub.clear();

        controls = new Atari2600ControlBinder(
                this, (mask, x, y) -> InputHub.set(1, mask, x, y));

        findViewById(R.id.btnAtariGameExit)
                .setOnClickListener(v -> finish());

        findViewById(R.id.btnAtariGameResetCore)
                .setOnClickListener(v -> surface.resetCore());

        findViewById(R.id.btnAtariGameSettings)
                .setOnClickListener(v ->
                        startActivity(new Intent(this, SettingsActivity.class)));

        hud.setOnClickListener(v ->
                setHudVisible(topBar == null
                        || topBar.getVisibility() != View.VISIBLE));

        File systemDir = new File(
                getFilesDir(), "cores/atari2600.stella2014/system");
        File saveDir = new File(
                getFilesDir(), "cores/atari2600.stella2014/saves");
        systemDir.mkdirs();
        saveDir.mkdirs();

        String corePath = getApplicationInfo().nativeLibraryDir
                + "/" + CoreRegistry.ATARI_2600.libraryFile;

        AdaptiveOptimizationEngine.Plan adaptivePlan = AdaptiveOptimizationEngine.resolve(
                this, CoreRegistry.ATARI_2600, romPath, hostSession);
        surface.setAdaptivePlan(adaptivePlan);
        surface.configure(CoreRegistry.ATARI_2600,
                corePath,
                romPath,
                systemDir.getAbsolutePath(),
                saveDir.getAbsolutePath(),
                this);

        setHudVisible(false);
    }

    private void setHudVisible(boolean visible) {
        if (topBar != null)
            topBar.setVisibility(visible ? View.VISIBLE : View.GONE);

        if (hud != null) {
            hud.setText(visible ? "×" : "⚙");
            hud.setAlpha(visible ? 0.98f : 0.78f);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (Atari2600GamepadMapper.handleKeyEvent(this, controls, event))
            return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (Atari2600GamepadMapper.handleMotionEvent(this, controls, event))
            return true;
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public void onCoreReady(String coreInfo,
                                      double fps,
                                      int sampleRate) {
        status.setText(hostSession
                ? "ATARI 2600 · " + coreInfo + " · P1 HOST / P2 REMOTO"
                : "ATARI 2600 · " + coreInfo);

        stats.setText(String.format(java.util.Locale.US,
                "%.2f FPS · %d Hz · SOFTWARE SAFE",
                fps, sampleRate));

        Toast.makeText(this, "Atari 2600 listo",
                Toast.LENGTH_SHORT).show();
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
        if (surface != null && !coreShutdown)
            surface.onResumeCore();
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
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
