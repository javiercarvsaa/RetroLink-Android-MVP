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

/** Emulador 2D genérico para cores libretro por software. */
public class IntegratedGameActivity extends Activity implements EmulatorSurfaceView.Listener {
    public static final String EXTRA_CORE_ID = "core_id";
    public static final String EXTRA_ROM_PATH = "rom_path";
    public static final String EXTRA_GAME_TITLE = "game_title";

    private SoftwareCoreView surface;
    private TextView title, status, stats;
    private View topBar;
    private Button hud;
    private N64ControlBinder controls;
    private CoreRegistry.Core core;
    private boolean coreShutdown;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_integrated_game);
        hideSystemUi();

        core = CoreRegistry.byId(getIntent().getStringExtra(EXTRA_CORE_ID));
        String romPath = getIntent().getStringExtra(EXTRA_ROM_PATH);
        String gameTitle = getIntent().getStringExtra(EXTRA_GAME_TITLE);
        if (romPath == null || !new File(romPath).isFile()) {
            Toast.makeText(this, "Juego no disponible", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        surface = findViewById(R.id.genericEmulatorSurface);
        title = findViewById(R.id.txtGenericTitle);
        status = findViewById(R.id.txtGenericStatus);
        stats = findViewById(R.id.txtGenericStats);
        topBar = findViewById(R.id.genericTopBar);
        hud = findViewById(R.id.btnGenericHud);

        title.setText(core.shortSystem + " · " + (gameTitle == null || gameTitle.trim().isEmpty() ? "JUEGO" : gameTitle));
        status.setText(core.system + " · preparando núcleo seguro…");

        SessionState.reset();
        SessionState.setConfiguredPlayers(1);
        InputHub.resetAll();
        EmulatorFrameHub.clear();

        controls = new N64ControlBinder(this, (mask, x, y) -> InputHub.set(1, mask, x, y));

        findViewById(R.id.btnGenericExit).setOnClickListener(v -> finish());
        findViewById(R.id.btnGenericReset).setOnClickListener(v -> surface.resetCore());
        findViewById(R.id.btnGenericSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        hud.setOnClickListener(v -> setHudVisible(topBar == null || topBar.getVisibility() != View.VISIBLE));

        File systemDir = new File(getFilesDir(), "cores/" + safeCoreDir(core.id) + "/system");
        File saveDir = new File(getFilesDir(), "cores/" + safeCoreDir(core.id) + "/saves");
        systemDir.mkdirs();
        saveDir.mkdirs();

        String corePath = getApplicationInfo().nativeLibraryDir + "/" + core.libraryFile;
        surface.configure(core, corePath, romPath, systemDir.getAbsolutePath(), saveDir.getAbsolutePath(), this);
        setHudVisible(false);
    }

    private String safeCoreDir(String id) {
        return id == null ? "generic" : id.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private void setHudVisible(boolean visible) {
        if (topBar != null) topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (hud != null) {
            hud.setText(visible ? "×" : "⚙");
            hud.setAlpha(visible ? 0.98f : 0.78f);
        }
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

    @Override public void onCoreReady(String coreInfo, double fps, int sampleRate) {
        status.setText(core.system + " · " + coreInfo);
        stats.setText(String.format(java.util.Locale.US, "%.2f FPS · %d Hz · SOFTWARE SAFE", fps, sampleRate));
        Toast.makeText(this, core.shortSystem + " listo", Toast.LENGTH_SHORT).show();
    }

    @Override public void onCoreError(String error) {
        status.setText("ERROR · " + error);
        setHudVisible(true);
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    @Override public void onStats(String s) { stats.setText(s); }

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
        InputHub.resetAll();
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
