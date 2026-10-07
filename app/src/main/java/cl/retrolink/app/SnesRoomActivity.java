package cl.retrolink.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import cl.retrolink.app.ble.HostBleManager;
import cl.retrolink.app.net.FrameStreamServer;

/**
 * Sala RetroLink SNES.
 * P1 ejecuta la ROM/Snes9x; P2 sólo envía controles y recibe la misma pantalla.
 */
public class SnesRoomActivity extends Activity
        implements HostBleManager.Listener, FrameStreamServer.Listener {

    private static final int REQ_BT = 392;

    private HostBleManager hostBle;
    private FrameStreamServer stream;
    private DemoGameEngine fallback;
    private SnesRomRepository.ImportedGame selected;

    private TextView status, game, network, p1, p2;
    private Button hostButton;
    private boolean hostStarted;
    private boolean p2Connected;
    private boolean gameLaunched;
    private int remoteScreens;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_snes_room);
        InsetHelper.apply(findViewById(R.id.snesRoomRoot));

        status = findViewById(R.id.txtSnesRoomStatus);
        game = findViewById(R.id.txtSnesRoomGame);
        network = findViewById(R.id.txtSnesRoomNetwork);
        p1 = findViewById(R.id.txtSnesRoomP1);
        p2 = findViewById(R.id.txtSnesRoomP2);
        hostButton = findViewById(R.id.btnSnesCreateRoom);

        selected = SnesRomRepository.lastGame(this);

        fallback = new DemoGameEngine();
        fallback.setPlayerCount(2);
        fallback.setProfileLabel("SUPER NINTENDO · P1 + P2");

        hostBle = new HostBleManager(this, this);
        hostBle.setMaxPlayerNumber(2);
        hostBle.setSessionType("SNES");
        stream = new FrameStreamServer(this, fallback, this);

        findViewById(R.id.btnSnesRoomBack).setOnClickListener(v -> finish());
        hostButton.setOnClickListener(v -> createRoom());
        findViewById(R.id.btnSnesJoinRoom).setOnClickListener(v ->
                startActivity(new Intent(this, SnesControllerActivity.class)));

        refresh();
    }

    private void createRoom() {
        selected = SnesRomRepository.lastGame(this);
        if (selected == null || !selected.file.isFile()) {
            Toast.makeText(this,
                    "P1 debe seleccionar una ROM SNES antes de crear la sala",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!BluetoothPermissionHelper.hasAll(this)) {
            requestPermissions(BluetoothPermissionHelper.requiredPermissions(), REQ_BT);
            return;
        }

        BluetoothManager manager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Activa Bluetooth para crear la sala", Toast.LENGTH_LONG).show();
            return;
        }

        SessionState.reset();
        SessionState.setConfiguredPlayers(2);
        InputHub.resetAll();
        EmulatorFrameHub.clear();

        p2Connected = false;
        gameLaunched = false;
        hostBle.start();
        stream.start();
        hostStarted = true;
        status.setText("Sala SNES activa · esperando Player 2");
        refresh();
    }

    private void launchHostGame() {
        if (gameLaunched || !hostStarted || !p2Connected
                || selected == null || !selected.file.isFile()) return;

        gameLaunched = true;
        status.setText("P2 listo · iniciando Snes9x en P1…");

        Intent i = new Intent(this, SnesGameActivity.class);
        i.putExtra(SnesGameActivity.EXTRA_ROM_PATH, selected.file.getAbsolutePath());
        i.putExtra(SnesGameActivity.EXTRA_GAME_TITLE, selected.title);
        i.putExtra(SnesGameActivity.EXTRA_HOST_SESSION, true);
        startActivity(i);
    }

    private void refresh() {
        selected = SnesRomRepository.lastGame(this);
        boolean has = selected != null && selected.file.isFile();

        game.setText(has
                ? selected.title + " · Super Nintendo"
                : "P1: selecciona un juego SNES · P2 no necesita ROM");

        String ip = NetworkUtils.localIpv4();
        network.setText(ip.isEmpty()
                ? "Misma red Wi-Fi/hotspot · Bluetooth para descubrir la sala"
                : "Host " + ip + ":" + FrameStreamServer.PORT
                    + " · pantallas remotas " + remoteScreens);

        p1.setText(hostStarted ? "● P1 · HOST SNES" : "P1 · sala detenida");
        p2.setText(p2Connected ? "● P2 · MANDO REMOTO LISTO" : "P2 · esperando jugador…");

        hostButton.setEnabled(has && !hostStarted);
        hostButton.setAlpha(hostButton.isEnabled() ? 1f : 0.42f);
    }

    @Override protected void onResume() {
        super.onResume();
        if (gameLaunched) gameLaunched = false;
        refresh();
    }

    @Override public void onStatus(String text) {
        if (hostStarted && text != null && !text.trim().isEmpty()) status.setText(text);
    }

    @Override public void onPlayerConnected(int player, String label) {
        if (player != 2) return;
        p2Connected = true;
        SessionState.playerConnected(2);
        status.setText("Player 2 conectado · iniciando partida SNES");
        refresh();
        p2.postDelayed(this::launchHostGame, 700);
    }

    @Override public void onPlayerDisconnected(int player) {
        if (player != 2) return;
        p2Connected = false;
        SessionState.playerDisconnected(2);
        InputHub.resetPlayer(2);
        if (hostStarted && !gameLaunched) status.setText("Player 2 desconectado");
        refresh();
    }

    @Override public void onInput(int player, int mask, int axisX, int axisY) {
        if (player == 2) InputHub.set(2, mask, axisX, axisY);
    }

    @Override public void onLog(String line) {}

    @Override public void onVideoServerStatus(String text) {
        if (hostStarted && text != null && !text.trim().isEmpty()) status.setText(text);
    }

    @Override public void onClientCount(int count) {
        remoteScreens = Math.max(0, count);
        refresh();
    }

    @Override public void onRequestPermissionsResult(int requestCode,
                                                      String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_BT) return;
        if (BluetoothPermissionHelper.hasAll(this)) createRoom();
        else Toast.makeText(this,
                "RetroLink necesita permiso Bluetooth para la sala SNES",
                Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        if (stream != null) stream.stop();
        if (hostBle != null) hostBle.stop();
        InputHub.resetPlayer(2);
        EmulatorFrameHub.clear();
        super.onDestroy();
    }
}
