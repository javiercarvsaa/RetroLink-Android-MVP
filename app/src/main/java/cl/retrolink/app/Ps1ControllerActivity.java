package cl.retrolink.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import cl.retrolink.app.ble.ControllerBleManager;
import cl.retrolink.app.net.FrameStreamClient;

/** Player 2 PlayStation: mando remoto digital + pantalla compartida. */
public class Ps1ControllerActivity extends Activity
        implements ControllerBleManager.Listener, FrameStreamClient.Listener {

    private static final int REQ_BT = 594;

    private ControllerBleManager ble;
    private FrameStreamClient video;
    private RemoteFrameView screen;
    private TextView status, videoStatus, playerTitle;
    private View topBar;
    private Button hud;
    private Ps1ControlBinder controls;

    private int player;
    private String sessionType = "";
    private String hostIp = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ps1_controller);
        hideSystemUi();

        screen = findViewById(R.id.ps1RemoteFrame);
        status = findViewById(R.id.txtPs1ControllerStatus);
        videoStatus = findViewById(R.id.txtPs1ControllerVideo);
        playerTitle = findViewById(R.id.txtPs1ControllerPlayer);
        topBar = findViewById(R.id.ps1ControllerTopBar);
        hud = findViewById(R.id.btnPs1ControllerHud);

        ble = new ControllerBleManager(this, this);
        video = new FrameStreamClient(this);
        controls = new Ps1ControlBinder(this,
                (mask, x, y) -> ble.sendInput(mask, x, y));

        findViewById(R.id.btnPs1Reconnect).setOnClickListener(v -> startJoin());
        findViewById(R.id.btnPs1RemoteExit).setOnClickListener(v -> finish());
        hud.setOnClickListener(v -> setHudVisible(
                topBar == null || topBar.getVisibility() != View.VISIBLE));

        screen.setPlayer(2);
        screen.setPlayerView(false);
        setHudVisible(true);

        findViewById(R.id.ps1ControllerRoot).postDelayed(this::startJoin, 200);
    }

    private void startJoin() {
        if (!BluetoothPermissionHelper.hasAll(this)) {
            requestPermissions(BluetoothPermissionHelper.requiredPermissions(), REQ_BT);
            return;
        }
        sessionType = "";
        hostIp = "";
        status.setText("Buscando sala PlayStation…");
        videoStatus.setText("Esperando Host RetroLink");
        ble.startScan();
    }

    private void setHudVisible(boolean visible) {
        if (topBar != null) topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (hud != null) {
            hud.setText(visible ? "×" : "⚙");
            hud.bringToFront();
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

    @Override public void onStatus(String text) {
        if (text != null && !text.trim().isEmpty()) status.setText(text);
    }

    @Override public void onConnected(int p) {
        player = p;
        if (p != 2) {
            status.setText("Sala incompatible · PlayStation v1.0 usa P1 + P2");
            return;
        }
        playerTitle.setText("PLAYER 2 · PLAYSTATION");
        status.setText("P2 · BLE LISTO");
        videoStatus.setText("Solicitando IP del Host…");
    }

    @Override public void onSessionInfo(String type, String ip) {
        sessionType = type == null
                ? ""
                : type.trim().toUpperCase(java.util.Locale.US);

        if (!sessionType.isEmpty() && !"PS1".equals(sessionType)) {
            status.setText("Host incompatible · la sala encontrada es " + sessionType);
            videoStatus.setText("Busca una sala PlayStation");
        }
    }

    @Override public void onHostInfo(String ip) {
        if (!"PS1".equals(sessionType)) return;

        hostIp = ip == null ? "" : ip.trim();
        if (hostIp.isEmpty()) {
            videoStatus.setText("BLE listo · esperando IP Wi-Fi del Host…");
            return;
        }

        videoStatus.setText("Host " + hostIp + " · conectando pantalla PS1…");
        screen.setPlayerView(false);
        video.start(hostIp, 2, false);
    }

    @Override public void onDisconnected() {
        player = 0;
        hostIp = "";
        if (video != null) video.stop();
        status.setText("P2 desconectado");
        videoStatus.setText("Pantalla PlayStation desconectada");
        setHudVisible(true);
    }

    @Override public void onLog(String line) {}

    @Override public void onFrame(Bitmap bitmap, int players,
                                  boolean preCropped, boolean dk64Raw) {
        screen.setDk64Raw(false);
        screen.setFrame(bitmap, Math.max(1, players), false);
    }

    @Override public void onVideoStatus(String text) {
        if (text != null && !text.trim().isEmpty()) videoStatus.setText(text);
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_BT) return;
        if (BluetoothPermissionHelper.hasAll(this)) startJoin();
        else Toast.makeText(this,
                "RetroLink necesita permiso Bluetooth para unirse a PS1",
                Toast.LENGTH_LONG).show();
    }

    @Override protected void onPause() {
        if (controls != null) controls.release();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (video != null) video.stop();
        if (ble != null) ble.disconnect();
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
