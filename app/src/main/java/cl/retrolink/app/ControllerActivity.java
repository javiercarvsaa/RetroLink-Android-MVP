package cl.retrolink.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import cl.retrolink.app.ble.ControllerBleManager;
import cl.retrolink.app.net.FrameStreamClient;

public class ControllerActivity extends Activity implements ControllerBleManager.Listener, FrameStreamClient.Listener {
    private ControllerBleManager ble;
    private FrameStreamClient video;
    private RemoteFrameView screen;
    private TextView status, videoStatus, inputStatus;
    private Button viewButton;
    private N64ControlBinder controls;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int player;
    private String lastHostIp = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_controller);
        InsetHelper.apply(findViewById(R.id.controllerRoot));

        status = findViewById(R.id.txtControllerStatus);
        videoStatus = findViewById(R.id.txtVideoStatus);
        inputStatus = findViewById(R.id.txtInputStatus);
        screen = findViewById(R.id.remoteFrameView);
        viewButton = findViewById(R.id.btnViewMode);
        ble = new ControllerBleManager(this, this);
        video = new FrameStreamClient(this);
        controls = new N64ControlBinder(this, (m, x, y) -> {
            ble.sendInput(m, x, y);
            if (player > 0) inputStatus.setText("P" + player + " → núcleo N64 · " + cl.retrolink.app.ble.BleProtocol.buttonsToText(m));
        });
        findViewById(R.id.btnFindHost).setOnClickListener(v -> ble.startScan());
        viewButton.setOnClickListener(v -> toggleView());
        screen.refreshRetroSr();
    }

    private void toggleView() {
        boolean next = !screen.isPlayerView();
        screen.setPlayerView(next);
        if (video != null) video.setPlayerView(next);
        viewButton.setText(next ? "PLAYER" : "FULL");
    }


    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (HardwareButtonMapper.handle(this, controls, event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onResume() {
        super.onResume();
        if (screen != null) screen.refreshRetroSr();
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
        status.setText("P" + p + " · BLE LISTO");
        inputStatus.setText("P" + p + " conectado al núcleo N64 del Host");
        videoStatus.setText("Solicitando IP del Host…");
        Toast.makeText(this, "Asignado como P" + p, Toast.LENGTH_SHORT).show();
    }

    @Override public void onHostInfo(String ip) {
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
        lastHostIp = "";
        if (video != null) video.stop();
        videoStatus.setText("Video desconectado");
        inputStatus.setText("Entrada N64 desconectada");
    }

    @Override public void onLog(String l) {}
    @Override public void onFrame(Bitmap b, int players, boolean preCropped, boolean dk64Raw) {
        screen.setDk64Raw(dk64Raw);
        screen.setFrame(b, players, preCropped);
    }

    @Override public void onVideoStatus(String s) {
        videoStatus.setText(s);
        if (s != null && s.startsWith("Video desconectado")) {
            main.postDelayed(() -> { if (ble != null) ble.requestHostInfo(); }, 1200);
        }
    }
}
