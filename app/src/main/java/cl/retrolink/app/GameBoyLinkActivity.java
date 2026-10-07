package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import cl.retrolink.app.ble.ControllerBleManager;
import cl.retrolink.app.ble.HostBleManager;

/**
 * Sala Game Boy Link de dos consolas.
 * BLE descubre/asigna P2 y comparte la IP del host.
 * El enlace de juego real usa el soporte Game Link TCP de Gambatte por red local.
 */
public class GameBoyLinkActivity extends Activity
        implements HostBleManager.Listener, ControllerBleManager.Listener {

    private static final int REQ_BT = 283;
    private static final int PORT = 56400;
    private static final int ROLE_NONE = 0;
    private static final int ROLE_HOST = 1;
    private static final int ROLE_CLIENT = 2;

    private HostBleManager hostBle;
    private ControllerBleManager clientBle;
    private TextView status, game, network, p1, p2;
    private Button launch;
    private GameBoyRomRepository.ImportedGame selected;
    private int role = ROLE_NONE;
    private int pendingRole = ROLE_NONE;
    private boolean p2Connected;
    private String hostIp = "";
    private boolean linkLaunchStarted;
    private String remoteSessionType = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_gameboy_link);
        InsetHelper.apply(findViewById(R.id.gbLinkRoot));

        status = findViewById(R.id.txtGbLinkStatus);
        game = findViewById(R.id.txtGbLinkGame);
        network = findViewById(R.id.txtGbLinkNetwork);
        p1 = findViewById(R.id.txtGbLinkP1);
        p2 = findViewById(R.id.txtGbLinkP2);
        launch = findViewById(R.id.btnGbLinkLaunch);

        selected = GameBoyRomRepository.lastGame(this);
        if (selected == null || !selected.file.isFile()) {
            Toast.makeText(this, "Selecciona primero un juego GB/GBC", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        game.setText(selected.title + " · " + selected.systemLabel);
        hostBle = new HostBleManager(this, this);
        hostBle.setMaxPlayerNumber(2);
        hostBle.setSessionType("GBLINK");
        clientBle = new ControllerBleManager(this, this);

        findViewById(R.id.btnGbLinkBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnGbLinkHost).setOnClickListener(v -> requestRole(ROLE_HOST));
        findViewById(R.id.btnGbLinkJoin).setOnClickListener(v -> requestRole(ROLE_CLIENT));
        launch.setOnClickListener(v -> launchLink());

        refresh();
    }

    private void requestRole(int target) {
        if (!BluetoothPermissionHelper.hasAll(this)) {
            pendingRole = target;
            requestPermissions(BluetoothPermissionHelper.requiredPermissions(), REQ_BT);
            return;
        }
        if (target == ROLE_HOST) startHost();
        else startClient();
    }

    private void startHost() {
        stopDiscovery();
        role = ROLE_HOST;
        p2Connected = false;
        linkLaunchStarted = false;
        remoteSessionType = "GBLINK";
        hostIp = NetworkUtils.localIpv4();
        hostBle.start();
        status.setText("Sala Game Link activa · esperando Player 2");
        refresh();
    }

    private void startClient() {
        stopDiscovery();
        role = ROLE_CLIENT;
        p2Connected = false;
        linkLaunchStarted = false;
        remoteSessionType = "";
        hostIp = "";
        clientBle.startScan();
        status.setText("Buscando sala Game Boy Link…");
        refresh();
    }

    private void stopDiscovery() {
        try { if (hostBle != null) hostBle.stop(); } catch (Throwable ignored) {}
        try { if (clientBle != null) clientBle.disconnect(); } catch (Throwable ignored) {}
    }

    private void launchLink() {
        if (linkLaunchStarted) return;
        if (role == ROLE_HOST && !p2Connected) {
            Toast.makeText(this, "Espera a que Player 2 se conecte", Toast.LENGTH_SHORT).show();
            return;
        }
        if (role == ROLE_CLIENT && hostIp.isEmpty()) {
            Toast.makeText(this, "Aún no recibimos la IP del Host", Toast.LENGTH_SHORT).show();
            return;
        }
        if (role == ROLE_NONE) return;
        if (role == ROLE_CLIENT && !"GBLINK".equals(remoteSessionType)) {
            Toast.makeText(this, "El Host encontrado no es una sala Game Boy Link", Toast.LENGTH_LONG).show();
            return;
        }

        linkLaunchStarted = true;
        status.setText(role == ROLE_HOST
                ? "Abriendo Gambatte local · P1 SERVER…"
                : "Abriendo Gambatte local · P2 CLIENT…");

        Intent i = new Intent(this, IntegratedGameActivity.class);
        i.putExtra(IntegratedGameActivity.EXTRA_CORE_ID, CoreRegistry.GAME_BOY.id);
        i.putExtra(IntegratedGameActivity.EXTRA_ROM_PATH, selected.file.getAbsolutePath());
        i.putExtra(IntegratedGameActivity.EXTRA_GAME_TITLE, selected.title);
        i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_MODE,
                role == ROLE_HOST ? "Network Server" : "Network Client");
        i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_HOST,
                role == ROLE_CLIENT ? hostIp : "");
        i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_PORT, PORT);
        startActivity(i);
    }

    private void refresh() {
        String local = NetworkUtils.localIpv4();
        network.setText(role == ROLE_CLIENT && !hostIp.isEmpty()
                ? "Host " + hostIp + ":" + PORT
                : (local.isEmpty()
                ? "Conéctense a la misma red Wi-Fi/hotspot"
                : "Local " + local + ":" + PORT));

        if (role == ROLE_HOST) p1.setText("● P1 · HOST GAME LINK");
        else if (role == ROLE_CLIENT) p1.setText("P1 · Host remoto");
        else p1.setText("P1 · esperando rol");

        if (role == ROLE_CLIENT) {
            p2.setText(hostIp.isEmpty() ? "P2 · buscando Host…" : "● P2 · CLIENT listo");
        } else {
            p2.setText(p2Connected ? "● P2 · dispositivo conectado" : "P2 · esperando jugador…");
        }

        boolean canLaunch = (role == ROLE_HOST && p2Connected)
                || (role == ROLE_CLIENT && !hostIp.isEmpty());
        launch.setEnabled(canLaunch);
        launch.setAlpha(canLaunch ? 1f : 0.42f);
        launch.setText(role == ROLE_CLIENT ? "▶ INICIAR COMO P2" : "▶ INICIAR GAME LINK P1");
    }

    @Override public void onStatus(String s) {
        if (s != null && !s.trim().isEmpty()) status.setText(s);
    }

    @Override public void onPlayerConnected(int player, String label) {
        if (role == ROLE_HOST && player == 2) {
            p2Connected = true;
            status.setText("Player 2 listo · abriendo Game Link local…");
            refresh();
            launch.postDelayed(this::launchLink, 700);
        }
    }

    @Override public void onPlayerDisconnected(int player) {
        if (player == 2) {
            p2Connected = false;
            if (role == ROLE_HOST) status.setText("Player 2 desconectado");
            refresh();
        }
    }

    @Override public void onInput(int player, int mask, int axisX, int axisY) {
        // Cada Game Boy ejecuta su propio control local; BLE solo descubre la sala.
    }

    @Override public void onConnected(int player) {
        if (role != ROLE_CLIENT) return;
        if (player != 2) {
            status.setText("Sala incompatible: Game Boy Link solo admite P1 + P2");
            return;
        }
        p2Connected = true;
        status.setText("Asignado como P2 · solicitando IP del Host…");
        refresh();
    }

    @Override public void onSessionInfo(String sessionType, String ip) {
        if (role != ROLE_CLIENT) return;
        remoteSessionType = sessionType == null ? "" : sessionType.trim().toUpperCase(java.util.Locale.US);
        if (!remoteSessionType.isEmpty() && !"GBLINK".equals(remoteSessionType)) {
            status.setText("Host incompatible · esta sala no es Game Boy Link");
            launch.setEnabled(false);
            launch.setAlpha(0.42f);
        }
    }

    @Override public void onHostInfo(String ip) {
        if (role != ROLE_CLIENT) return;
        hostIp = ip == null ? "" : ip.trim();
        status.setText(hostIp.isEmpty()
                ? "BLE conectado · esperando IP Wi-Fi del Host…"
                : ("GBLINK".equals(remoteSessionType)
                    ? "P2 listo · abriendo Gambatte local…"
                    : "P2 detectado · validando tipo de sala…"));
        refresh();
        if (!hostIp.isEmpty() && "GBLINK".equals(remoteSessionType)) {
            launch.postDelayed(this::launchLink, 350);
        }
    }

    @Override public void onDisconnected() {
        if (role == ROLE_CLIENT && !isFinishing()) {
            p2Connected = false;
            hostIp = "";
            status.setText("Desconectado de la sala Game Boy Link");
            refresh();
        }
    }

    @Override public void onLog(String line) {}

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != REQ_BT) return;
        if (!BluetoothPermissionHelper.hasAll(this)) {
            Toast.makeText(this, "RetroLink necesita permisos Bluetooth para descubrir la sala", Toast.LENGTH_LONG).show();
            pendingRole = ROLE_NONE;
            return;
        }
        int r = pendingRole;
        pendingRole = ROLE_NONE;
        if (r == ROLE_HOST) startHost();
        else if (r == ROLE_CLIENT) startClient();
    }

    @Override protected void onDestroy() {
        stopDiscovery();
        super.onDestroy();
    }
}
