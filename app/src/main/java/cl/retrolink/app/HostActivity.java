package cl.retrolink.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import cl.retrolink.app.ble.HostBleManager;
import cl.retrolink.app.net.FrameStreamServer;

/** Sala local: solo expone estados y acciones conectados al host BLE/Wi-Fi real. */
public class HostActivity extends Activity implements HostBleManager.Listener, FrameStreamServer.Listener {
    public static final String EXTRA_OPEN_ROM_PICKER = "open_rom_picker";
    private static final int REQ_ROM = 220;
    private static final int REQ_BT = 222;

    private HostBleManager ble;
    private FrameStreamServer stream;
    private DemoGameEngine engine;
    private TextView status, romInfo, clients, coreStatus, roomState, p1State, p2State, p3State, p4State;
    private Button[] playerButtons;
    private N64ControlBinder controls;
    private int playerCount = 1;
    private File romFile;
    private boolean hostStarted;
    private int remoteScreenCount;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_host);
        SessionState.reset();
        InsetHelper.apply(findViewById(R.id.hostRoot));

        status = findViewById(R.id.txtHostStatus);
        romInfo = findViewById(R.id.txtRomInfo);
        clients = findViewById(R.id.txtVideoClients);
        coreStatus = findViewById(R.id.txtCoreStatus);
        roomState = findViewById(R.id.txtRoomState);
        p1State = findViewById(R.id.txtP1State);
        p2State = findViewById(R.id.txtP2State);
        p3State = findViewById(R.id.txtP3State);
        p4State = findViewById(R.id.txtP4State);
        playerButtons = new Button[]{null, findViewById(R.id.btnPlayers1), findViewById(R.id.btnPlayers2), findViewById(R.id.btnPlayers3), findViewById(R.id.btnPlayers4)};

        engine = new DemoGameEngine(); // fallback de video antes de que exista frame del emulador.
        engine.setPlayerCount(playerCount);
        ble = new HostBleManager(this, this);
        stream = new FrameStreamServer(this, engine, this);
        controls = new N64ControlBinder(this, (m, x, y) -> {
            InputHub.set(1, m, x, y);
            engine.setInput(1, m, x, y);
        });

        findViewById(R.id.btnStartHost).setOnClickListener(v -> startHost());
        findViewById(R.id.btnStopHost).setOnClickListener(v -> stopHost());
        findViewById(R.id.btnRom).setOnClickListener(v -> pickRom());
        findViewById(R.id.btnLaunch).setOnClickListener(v -> launchIntegratedN64());
        for (int p = 1; p <= 4; p++) {
            final int count = p;
            playerButtons[p].setOnClickListener(v -> setPlayers(count));
        }

        findViewById(R.id.navHostHome).setOnClickListener(v -> finish());
        findViewById(R.id.navHostGames).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.navHostProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.navHostSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        restoreLastRom();
        refreshPlayerCards();
        refreshRoomStatus();
        updatePlayerButtons();
        if (getIntent().getBooleanExtra(EXTRA_OPEN_ROM_PICKER, false)) {
            findViewById(R.id.hostRoot).postDelayed(this::pickRom, 180);
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

    private void restoreLastRom() {
        romFile = N64RomRepository.lastRom(this);
        if (romFile == null) {
            romInfo.setText("Selecciona una ROM N64");
            coreStatus.setText("Mupen64Plus-Next · sin juego seleccionado");
            return;
        }
        try {
            N64RomInfo info = N64RomRepository.readLastInfo(this);
            if (info != null) {
                romInfo.setText(info.summary());
                coreStatus.setText("Mupen64Plus-Next · ARM64/GLES3 · PresentSync");
                if (info.marioKart64) engine.setProfileLabel("MARIO KART 64 · FULL / RACE VIEW");
            }
        } catch (Exception e) {
            romFile = null;
            romInfo.setText("ROM guardada no disponible");
            coreStatus.setText("Selecciona nuevamente el juego");
        }
    }

    private void refreshPlayerCards() {
        if (p1State != null) p1State.setText("● Este dispositivo · P1");
        if (p2State != null) p2State.setText(SessionState.isConnected(2) ? "● Conectado · Player 2" : "Esperando jugador…");
        if (p3State != null) p3State.setText(SessionState.isConnected(3) ? "● Conectado · Player 3" : "Esperando jugador…");
        if (p4State != null) p4State.setText(SessionState.isConnected(4) ? "● Conectado · Player 4" : "Esperando jugador…");
    }

    private void refreshRoomStatus() {
        String ip = NetworkUtils.localIpv4();
        if (!hostStarted) {
            status.setText("Sala detenida · configura jugadores y activa la sala");
            roomState.setText("○ SALA DETENIDA");
            clients.setText(ip.isEmpty() ? "Wi‑Fi local sin IP" : "Red local: " + ip + ":" + FrameStreamServer.PORT);
        } else {
            status.setText("Sala activa · esperando/conectando jugadores por BLE");
            roomState.setText("● SALA ACTIVA");
            clients.setText((ip.isEmpty() ? "Wi‑Fi local" : ip + ":" + FrameStreamServer.PORT) + " · pantallas " + remoteScreenCount);
        }
        findViewById(R.id.btnStartHost).setEnabled(!hostStarted);
        findViewById(R.id.btnStartHost).setAlpha(hostStarted ? 0.45f : 1f);
        findViewById(R.id.btnStopHost).setEnabled(hostStarted);
        findViewById(R.id.btnStopHost).setAlpha(hostStarted ? 1f : 0.45f);
    }

    private void startHost() {
        if (!BluetoothPermissionHelper.hasAll(this)) {
            requestPermissions(BluetoothPermissionHelper.requiredPermissions(), REQ_BT);
            Toast.makeText(this, "Autoriza Dispositivos cercanos para activar la sala.", Toast.LENGTH_LONG).show();
            return;
        }
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        if (adapter == null || !adapter.isEnabled()) {
            status.setText("Bluetooth apagado · actívalo para crear la sala");
            Toast.makeText(this, "Activa Bluetooth para crear la sala local.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!hostStarted) {
            ble.start();
            stream.start();
            hostStarted = true;
        }
        refreshRoomStatus();
    }

    private void stopHost() {
        hostStarted = false;
        remoteScreenCount = 0;
        ble.stop();
        stream.stop();
        InputHub.resetAll();
        EmulatorFrameHub.clear();
        refreshRoomStatus();
        refreshPlayerCards();
    }

    private void setPlayers(int count) {
        playerCount = Math.max(1, Math.min(4, count));
        SessionState.setConfiguredPlayers(playerCount);
        engine.setPlayerCount(playerCount);
        updatePlayerButtons();
        refreshPlayerCards();
    }

    private void updatePlayerButtons() {
        for (int p = 1; p <= 4; p++) {
            playerButtons[p].setBackgroundResource(p == playerCount ? R.drawable.segment_selected : R.drawable.btn_secondary_neon);
            playerButtons[p].setAlpha(p == playerCount ? 1f : 0.78f);
        }
    }

    private void pickRom() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_ROM);
    }

    private void launchIntegratedN64() {
        if (romFile == null || !romFile.isFile()) {
            Toast.makeText(this, "Selecciona primero tu ROM N64", Toast.LENGTH_SHORT).show();
            pickRom();
            return;
        }
        if (playerCount > 1 || hostStarted) {
            if (!hostStarted) startHost();
            if (!hostStarted) {
                Toast.makeText(this, "Activa la sala para jugar con más de un jugador.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        InputHub.resetAll();
        EmulatorFrameHub.clear();
        Intent i = new Intent(this, IntegratedN64Activity.class);
        i.putExtra(IntegratedN64Activity.EXTRA_ROM_PATH, romFile.getAbsolutePath());
        i.putExtra(IntegratedN64Activity.EXTRA_PLAYERS, SessionState.getPlayerCount());
        startActivity(i);
    }

    private void importRom(Uri uri) {
        try {
            N64RomRepository.ImportedRom imported = N64RomRepository.importRom(this, uri);
            romFile = imported.file;
            romInfo.setText(imported.info.summary());
            coreStatus.setText("Mupen64Plus-Next · ARM64/GLES3 · PresentSync");
            if (imported.info.marioKart64) engine.setProfileLabel("MARIO KART 64 · FULL / RACE VIEW");
            else engine.setProfileLabel("N64 · PERFIL GENÉRICO");
            Toast.makeText(this, "ROM N64 reconocida", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            romFile = null;
            romInfo.setText("ROM no reconocida: " + e.getMessage());
            coreStatus.setText("Core N64 · archivo inválido");
        }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_ROM && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) {}
            importRom(uri);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT) {
            if (BluetoothPermissionHelper.hasAll(this)) {
                Toast.makeText(this, "Permisos listos · toca ACTIVAR SALA", Toast.LENGTH_SHORT).show();
                status.setText("Permisos listos · activa la sala");
            } else {
                status.setText("Permiso Bluetooth pendiente");
            }
        }
    }

    @Override protected void onPause() {
        if (controls != null) controls.release();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (stream != null) stream.stop();
        if (ble != null) ble.stop();
        super.onDestroy();
    }

    @Override public void onStatus(String s) {
        if (hostStarted) status.setText(s);
    }

    @Override public void onPlayerConnected(int p, String d) {
        SessionState.playerConnected(p);
        int sessionPlayers = SessionState.getPlayerCount();
        if (sessionPlayers > playerCount) {
            playerCount = sessionPlayers;
            engine.setPlayerCount(playerCount);
            updatePlayerButtons();
        }
        refreshPlayerCards();
        Toast.makeText(this, "P" + p + " conectado · " + d, Toast.LENGTH_SHORT).show();
    }

    @Override public void onPlayerDisconnected(int p) {
        SessionState.playerDisconnected(p);
        InputHub.resetPlayer(p);
        engine.setInput(p, 0, 0, 0);
        refreshPlayerCards();
    }

    @Override public void onInput(int p, int m, int x, int y) {
        InputHub.set(p, m, x, y);
        engine.setInput(p, m, x, y);
    }

    @Override public void onLog(String l) {}

    @Override public void onVideoServerStatus(String s) {
        if (hostStarted && s != null && !s.isEmpty()) status.setText(s);
    }

    @Override public void onClientCount(int count) {
        remoteScreenCount = Math.max(0, count);
        refreshRoomStatus();
    }
}
