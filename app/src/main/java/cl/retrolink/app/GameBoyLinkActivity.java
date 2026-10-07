package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import cl.retrolink.app.ble.ControllerBleManager;
import cl.retrolink.app.ble.HostBleManager;

/**
 * Sala Game Boy Link real de dos consolas.
 *
 * P1 selecciona la ROM una sola vez.
 * P2 no necesita importar ni seleccionar ROM: RetroLink la recibe desde P1,
 * verifica SHA-256 y recién entonces inicia dos instancias Gambatte enlazadas.
 */
public class GameBoyLinkActivity extends Activity
        implements HostBleManager.Listener, ControllerBleManager.Listener, GameBoyRomTransfer.Listener {

    public static final String EXTRA_AUTO_JOIN_IP = "gb_link_auto_join_ip";

    private static final int REQ_BT = 283;
    private static final int LINK_PORT = 56400;
    private static final int ROLE_NONE = 0;
    private static final int ROLE_HOST = 1;
    private static final int ROLE_CLIENT = 2;

    private HostBleManager hostBle;
    private ControllerBleManager clientBle;
    private GameBoyRomTransfer transfer;

    private TextView status, game, network, p1, p2;
    private Button hostButton, joinButton, launch;

    private GameBoyRomRepository.ImportedGame selected;
    private File sessionRom;
    private String sessionTitle = "";
    private String sessionSystem = "Game Boy / Game Boy Color";

    private int role = ROLE_NONE;
    private int pendingRole = ROLE_NONE;
    private boolean p2Connected;
    private boolean transferStarted;
    private boolean romReady;
    private boolean gameStarting;
    private String hostIp = "";
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
        hostButton = findViewById(R.id.btnGbLinkHost);
        joinButton = findViewById(R.id.btnGbLinkJoin);
        launch = findViewById(R.id.btnGbLinkLaunch);

        selected = GameBoyRomRepository.lastGame(this);
        if (selected != null && selected.file.isFile()) {
            sessionRom = selected.file;
            sessionTitle = selected.title;
            sessionSystem = selected.systemLabel;
        }

        hostBle = new HostBleManager(this, this);
        hostBle.setMaxPlayerNumber(2);
        hostBle.setSessionType("GBLINK");
        clientBle = new ControllerBleManager(this, this);
        transfer = new GameBoyRomTransfer(this);

        findViewById(R.id.btnGbLinkBack).setOnClickListener(v -> finish());
        hostButton.setOnClickListener(v -> requestRole(ROLE_HOST));
        joinButton.setOnClickListener(v -> requestRole(ROLE_CLIENT));

        // v0.7.5: el arranque se sincroniza automáticamente. Se oculta el botón
        // manual para impedir que P2 lance Gambatte antes de recibir/verificar la ROM.
        launch.setVisibility(View.GONE);

        String autoIp = getIntent().getStringExtra(EXTRA_AUTO_JOIN_IP);
        if (autoIp != null && !autoIp.trim().isEmpty()) {
            role = ROLE_CLIENT;
            p2Connected = true;
            remoteSessionType = "GBLINK";
            hostIp = autoIp.trim();
            status.setText("Sala GB/GBC detectada · recibiendo juego de P1…");
            refresh();
            startClientTransferIfReady();
        } else {
            refresh();
        }
    }

    private void requestRole(int target) {
        if (target == ROLE_HOST && (selected == null || !selected.file.isFile())) {
            Toast.makeText(this,
                    "Para crear la sala P1 debes seleccionar un juego. P2 no necesita tenerlo.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!BluetoothPermissionHelper.hasAll(this)) {
            pendingRole = target;
            requestPermissions(BluetoothPermissionHelper.requiredPermissions(), REQ_BT);
            return;
        }
        if (target == ROLE_HOST) startHost();
        else startClient();
    }

    private void startHost() {
        stopSession();
        role = ROLE_HOST;
        p2Connected = false;
        transferStarted = true;
        romReady = true;
        gameStarting = false;
        remoteSessionType = "GBLINK";
        hostIp = NetworkUtils.localIpv4();

        selected = GameBoyRomRepository.lastGame(this);
        if (selected == null || !selected.file.isFile()) {
            role = ROLE_NONE;
            transferStarted = false;
            status.setText("Selecciona un juego antes de crear la sala P1");
            refresh();
            return;
        }

        sessionRom = selected.file;
        sessionTitle = selected.title;
        sessionSystem = selected.systemLabel;

        hostBle.start();
        transfer.startHost(sessionRom, sessionTitle, sessionSystem, this);

        status.setText("Sala creada · P2 recibirá automáticamente " + sessionTitle);
        refresh();
    }

    private void startClient() {
        stopSession();
        role = ROLE_CLIENT;
        p2Connected = false;
        transferStarted = false;
        romReady = false;
        gameStarting = false;
        remoteSessionType = "";
        hostIp = "";
        sessionRom = null;
        sessionTitle = "";
        sessionSystem = "Game Boy / Game Boy Color";

        clientBle.startScan();
        status.setText("Buscando sala Game Boy Link… P2 no necesita ROM");
        refresh();
    }

    private void stopSession() {
        try { if (transfer != null) transfer.stop(); } catch (Throwable ignored) {}
        try { if (hostBle != null) hostBle.stop(); } catch (Throwable ignored) {}
        try { if (clientBle != null) clientBle.disconnect(); } catch (Throwable ignored) {}
    }

    private void startClientTransferIfReady() {
        if (role != ROLE_CLIENT || transferStarted || gameStarting) return;
        if (!"GBLINK".equals(remoteSessionType)) return;
        if (hostIp.isEmpty()) return;

        transferStarted = true;
        status.setText("P2 conectado · solicitando ROM a P1…");
        refresh();
        transfer.startClient(hostIp, this);
    }

    private void launchHostGame() {
        if (gameStarting || role != ROLE_HOST || sessionRom == null || !sessionRom.isFile()) return;
        gameStarting = true;
        status.setText("ROM sincronizada · iniciando P1 como servidor Game Link…");
        refresh();

        ResultReceiver receiver = new ResultReceiver(new Handler()) {
            @Override protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (transfer == null) return;
                if (resultCode == IntegratedGameActivity.LINK_CORE_READY) {
                    transfer.signalHostCoreReady();
                } else if (resultCode == IntegratedGameActivity.LINK_CORE_ERROR) {
                    String e = resultData == null ? "" :
                            resultData.getString(IntegratedGameActivity.RESULT_LINK_ERROR, "");
                    transfer.signalHostCoreError(e);
                }
            }
        };

        Intent i = buildGameIntent("Network Server", "", receiver);
        startActivity(i);
    }

    private void launchClientGame() {
        if (gameStarting || role != ROLE_CLIENT || !romReady
                || sessionRom == null || !sessionRom.isFile()) return;
        gameStarting = true;
        status.setText("P1 listo · iniciando P2 como cliente Game Link…");
        refresh();
        startActivity(buildGameIntent("Network Client", hostIp, null));
    }

    private Intent buildGameIntent(String mode, String ip, ResultReceiver receiver) {
        Intent i = new Intent(this, IntegratedGameActivity.class);
        i.putExtra(IntegratedGameActivity.EXTRA_CORE_ID, CoreRegistry.GAME_BOY.id);
        i.putExtra(IntegratedGameActivity.EXTRA_ROM_PATH, sessionRom.getAbsolutePath());
        i.putExtra(IntegratedGameActivity.EXTRA_GAME_TITLE,
                sessionTitle == null || sessionTitle.isEmpty() ? "Game Boy" : sessionTitle);
        i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_MODE, mode);
        i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_HOST, ip == null ? "" : ip);
        i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_PORT, LINK_PORT);
        if (receiver != null)
            i.putExtra(IntegratedGameActivity.EXTRA_GB_LINK_READY_RECEIVER, receiver);
        return i;
    }

    private void refresh() {
        boolean hasLocal = selected != null && selected.file.isFile();

        if (role == ROLE_CLIENT && !sessionTitle.isEmpty()) {
            game.setText(sessionTitle + " · recibido desde P1");
        } else if (hasLocal) {
            game.setText(selected.title + " · " + selected.systemLabel);
        } else {
            game.setText("P2 recibirá automáticamente el juego seleccionado por P1");
        }

        String local = NetworkUtils.localIpv4();
        if (role == ROLE_CLIENT && !hostIp.isEmpty()) {
            network.setText("P1 " + hostIp + " · ROM " + GameBoyRomTransfer.PORT
                    + " · Game Link " + LINK_PORT);
        } else if (local.isEmpty()) {
            network.setText("Conéctense a la misma red Wi-Fi/hotspot");
        } else {
            network.setText("Local " + local + " · ROM " + GameBoyRomTransfer.PORT
                    + " · Game Link " + LINK_PORT);
        }

        if (role == ROLE_HOST) {
            p1.setText(gameStarting ? "● P1 · GAME LINK SERVER" : "● P1 · HOST + ROM");
        } else if (role == ROLE_CLIENT) {
            p1.setText("● P1 · HOST REMOTO");
        } else {
            p1.setText(hasLocal ? "P1 · puede crear sala" : "P1 · requiere seleccionar juego");
        }

        if (role == ROLE_CLIENT) {
            if (gameStarting) p2.setText("● P2 · GAME LINK CLIENT");
            else if (romReady) p2.setText("● P2 · ROM VERIFICADA");
            else if (transferStarted) p2.setText("● P2 · RECIBIENDO ROM…");
            else if (p2Connected) p2.setText("● P2 · BLE LISTO");
            else p2.setText("P2 · buscando Host…");
        } else {
            p2.setText(p2Connected ? "● P2 · conectado" : "P2 · esperando jugador…");
        }

        hostButton.setEnabled(hasLocal && role != ROLE_CLIENT && !gameStarting);
        hostButton.setAlpha(hostButton.isEnabled() ? 1f : 0.42f);
        joinButton.setEnabled(role != ROLE_HOST && !gameStarting);
        joinButton.setAlpha(joinButton.isEnabled() ? 1f : 0.42f);
    }

    @Override public void onTransferStatus(String text) {
        if (text != null && !text.trim().isEmpty()) status.setText(text);
    }

    @Override public void onHostRomAccepted() {
        if (role != ROLE_HOST) return;
        status.setText("✓ P2 recibió la misma ROM · iniciando P1 primero…");
        launchHostGame();
    }

    @Override public void onClientRomReady(GameBoyRomTransfer.SessionRom rom) {
        if (role != ROLE_CLIENT || rom == null || rom.file == null || !rom.file.isFile()) return;
        sessionRom = rom.file;
        sessionTitle = rom.title;
        sessionSystem = rom.systemLabel;
        romReady = true;
        status.setText("✓ ROM recibida y SHA-256 verificado · esperando P1");
        refresh();
    }

    @Override public void onClientStart() {
        if (role != ROLE_CLIENT) return;
        launchClientGame();
    }

    @Override public void onTransferError(String error) {
        gameStarting = false;
        transferStarted = false;
        String message = error == null || error.trim().isEmpty() ? "No se pudo sincronizar la sesión" : error;
        status.setText("ERROR DE SESIÓN · " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        refresh();
    }

    @Override public void onStatus(String s) {
        if (s != null && !s.trim().isEmpty() && !transferStarted && !gameStarting)
            status.setText(s);
    }

    @Override public void onPlayerConnected(int player, String label) {
        if (role == ROLE_HOST && player == 2) {
            p2Connected = true;
            status.setText("P2 detectado · esperando solicitud segura de ROM…");
            refresh();
        }
    }

    @Override public void onPlayerDisconnected(int player) {
        if (gameStarting) return;
        if (player == 2) {
            p2Connected = false;
            if (role == ROLE_HOST && !transferStarted)
                status.setText("Player 2 desconectado");
            refresh();
        }
    }

    @Override public void onInput(int player, int mask, int axisX, int axisY) {
        // Game Boy usa control local en cada teléfono. BLE no transporta inputs GB.
    }

    @Override public void onConnected(int player) {
        if (role != ROLE_CLIENT) return;
        if (player != 2) {
            status.setText("Sala incompatible: Game Boy Link sólo admite P1 + P2");
            return;
        }
        p2Connected = true;
        status.setText("P2 asignado · solicitando datos seguros de la sala…");
        refresh();
    }

    @Override public void onSessionInfo(String sessionType, String ip) {
        if (role != ROLE_CLIENT) return;
        remoteSessionType = sessionType == null ? "" :
                sessionType.trim().toUpperCase(java.util.Locale.US);
        hostIp = ip == null ? "" : ip.trim();

        if (!remoteSessionType.isEmpty() && !"GBLINK".equals(remoteSessionType)) {
            status.setText("Host incompatible · la sala encontrada no es Game Boy Link");
            return;
        }
        startClientTransferIfReady();
        refresh();
    }

    @Override public void onHostInfo(String ip) {
        if (role != ROLE_CLIENT) return;
        if (hostIp.isEmpty() && ip != null) hostIp = ip.trim();
        startClientTransferIfReady();
        refresh();
    }

    @Override public void onDisconnected() {
        if (role == ROLE_CLIENT && !gameStarting && !romReady && !isFinishing()) {
            p2Connected = false;
            if (!transferStarted) {
                hostIp = "";
                        status.setText("Desconectado de la sala Game Boy Link");
            }
            refresh();
        }
    }

    @Override public void onLog(String line) {}

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != REQ_BT) return;
        if (!BluetoothPermissionHelper.hasAll(this)) {
            Toast.makeText(this,
                    "RetroLink necesita permisos Bluetooth para descubrir la sala",
                    Toast.LENGTH_LONG).show();
            pendingRole = ROLE_NONE;
            return;
        }
        int r = pendingRole;
        pendingRole = ROLE_NONE;
        if (r == ROLE_HOST) startHost();
        else if (r == ROLE_CLIENT) startClient();
    }

    @Override protected void onDestroy() {
        stopSession();
        super.onDestroy();
    }
}
