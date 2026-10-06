package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import cl.retrolink.app.ble.HostBleManager;
import cl.retrolink.app.net.FrameStreamServer;

public class HostActivity extends Activity implements HostBleManager.Listener, FrameStreamServer.Listener {
    public static final String EXTRA_OPEN_ROM_PICKER = "open_rom_picker";
    private static final int REQ_ROM = 220;

    private HostBleManager ble;
    private FrameStreamServer stream;
    private DemoGameEngine engine;
    private DemoGameView game;
    private TextView status, romInfo, clients, coreStatus, p1State, p2State, p3State, p4State;
    private Button playersBtn, viewBtn;
    private N64ControlBinder controls;
    private int playerCount = 1;
    private File romFile;
    private boolean hostStarted;

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
        p1State = findViewById(R.id.txtP1State);
        p2State = findViewById(R.id.txtP2State);
        p3State = findViewById(R.id.txtP3State);
        p4State = findViewById(R.id.txtP4State);
        game = findViewById(R.id.gameTestView);
        playersBtn = findViewById(R.id.btnPlayers);
        viewBtn = findViewById(R.id.btnViewMode);

        engine = new DemoGameEngine();
        engine.setPlayerCount(playerCount);
        game.setEngine(engine);
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
        playersBtn.setOnClickListener(v -> cyclePlayers());
        viewBtn.setOnClickListener(v -> toggleView());
        findViewById(R.id.navHostHome).setOnClickListener(v -> finish());
        findViewById(R.id.navHostGames).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.navHostProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.navHostSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        String ip = NetworkUtils.localIpv4();
        clients.setText(ip.isEmpty() ? "Wi‑Fi: sin IP local" : "Wi‑Fi Host: " + ip + ":" + FrameStreamServer.PORT);
        coreStatus.setText("N64 integrado · selecciona una ROM");
        restoreLastRom();
        refreshPlayerCards();
        if (getIntent().getBooleanExtra(EXTRA_OPEN_ROM_PICKER, false)) {
            findViewById(R.id.hostRoot).postDelayed(this::pickRom, 220);
        }
    }


    private void restoreLastRom() {
        String path = RetroPreferences.lastRomPath(this);
        if (path == null || path.isEmpty()) return;
        File f = new File(path);
        if (!f.isFile()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            N64RomInfo info = N64RomInfo.read(in);
            romFile = f;
            romInfo.setText(info.summary());
            coreStatus.setText("Mupen64Plus-Next · ARM64/GLES3 · PresentSync · RetroSR 2.2");
            if (info.marioKart64) engine.setProfileLabel("MARIO KART 64 · FULL / RACE VIEW");
        } catch (Exception ignored) {}
    }

    private void refreshPlayerCards() {
        if (p1State != null) p1State.setText("● Conectado · Host");
        if (p2State != null) p2State.setText(SessionState.isConnected(2) ? "● Conectado · Player 2" : "Esperando jugador…");
        if (p3State != null) p3State.setText(SessionState.isConnected(3) ? "● Conectado · Player 3" : "Esperando jugador…");
        if (p4State != null) p4State.setText(SessionState.isConnected(4) ? "● Conectado · Player 4" : "Esperando jugador…");
    }

    private void startHost() {
        if (!hostStarted) {
            ble.start();
            stream.start();
            hostStarted = true;
        }
        String ip = NetworkUtils.localIpv4();
        clients.setText(ip.isEmpty() ? "Conecta todos a la misma Wi‑Fi/hotspot" : "Wi‑Fi Host: " + ip + ":" + FrameStreamServer.PORT + " · clientes 0");
    }

    private void stopHost() {
        hostStarted = false;
        ble.stop();
        stream.stop();
        InputHub.resetAll();
        EmulatorFrameHub.clear();
    }

    private void cyclePlayers() {
        playerCount = playerCount >= 4 ? 1 : playerCount + 1;
        SessionState.setConfiguredPlayers(playerCount);
        engine.setPlayerCount(playerCount);
        playersBtn.setText(playerCount + "P");
        refreshPlayerCards();
    }

    private void toggleView() {
        boolean next = !game.isPlayerView();
        game.setPlayerView(next);
        viewBtn.setText(next ? "PLAYER P1" : "FULL");
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
        startHost();
        InputHub.resetAll();
        EmulatorFrameHub.clear();
        Intent i = new Intent(this, IntegratedN64Activity.class);
        i.putExtra(IntegratedN64Activity.EXTRA_ROM_PATH, romFile.getAbsolutePath());
        i.putExtra(IntegratedN64Activity.EXTRA_PLAYERS, SessionState.getPlayerCount());
        startActivity(i);
    }

    private void importRom(Uri uri) {
        File dir = new File(getFilesDir(), "roms");
        dir.mkdirs();
        File dst = new File(dir, "selected_n64.rom");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dst, false)) {
            if (in == null) throw new IllegalStateException("No se pudo abrir el archivo");
            byte[] buf = new byte[256 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > 128L * 1024L * 1024L) throw new IllegalStateException("ROM demasiado grande");
            }
        } catch (Exception e) {
            romFile = null;
            romInfo.setText("ROM: error al importar · " + e.getMessage());
            coreStatus.setText("N64 integrado detenido");
            return;
        }

        try (FileInputStream in = new FileInputStream(dst)) {
            N64RomInfo info = N64RomInfo.read(in);
            romFile = dst;
            romInfo.setText(info.summary());
            RetroPreferences.setLastRom(this, dst.getAbsolutePath(), info.summary());
            coreStatus.setText("Mupen64Plus-Next · ARM64/GLES3 · PresentSync · RetroSR 2.2");
            if (info.marioKart64) {
                engine.setProfileLabel("MARIO KART 64 · FULL / RACE VIEW");
                // No asumir 4 jugadores por reconocer Mario Kart 64. El número de
                // jugadores de RetroLink debe corresponder a la sesión real.
                playerCount = 1;
                SessionState.setConfiguredPlayers(1);
                engine.setPlayerCount(1);
                playersBtn.setText("1P");
                Toast.makeText(this, "Mario Kart 64 reconocido · inicia en 1P y cambia a 2P/3P/4P solo si corresponde", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "ROM N64 reconocida · perfil genérico", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            romFile = null;
            romInfo.setText("ROM no reconocida: " + e.getMessage());
            coreStatus.setText("Core N64: ROM inválida");
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

    @Override protected void onPause() {
        if (controls != null) controls.release();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (stream != null) stream.stop();
        if (ble != null) ble.stop();
        super.onDestroy();
    }

    @Override public void onStatus(String s) { status.setText(s); }
    @Override public void onPlayerConnected(int p, String d) {
        SessionState.playerConnected(p);
        int sessionPlayers = SessionState.getPlayerCount();
        if (sessionPlayers != playerCount) {
            playerCount = sessionPlayers;
            engine.setPlayerCount(playerCount);
            playersBtn.setText(playerCount + "P");
        }
        refreshPlayerCards();
        Toast.makeText(this, "P" + p + " conectado", Toast.LENGTH_SHORT).show();
    }
    @Override public void onPlayerDisconnected(int p) {
        SessionState.playerDisconnected(p);
        InputHub.resetPlayer(p);
        engine.setInput(p, 0, 0, 0);
        playerCount = SessionState.getPlayerCount();
        engine.setPlayerCount(playerCount);
        playersBtn.setText(playerCount + "P");
        refreshPlayerCards();
    }
    @Override public void onInput(int p, int m, int x, int y) {
        InputHub.set(p, m, x, y);
        engine.setInput(p, m, x, y);
    }
    @Override public void onLog(String l) {}
    @Override public void onVideoServerStatus(String s) { clients.setText(s); }
    @Override public void onClientCount(int count) {
        String ip = NetworkUtils.localIpv4();
        clients.setText((ip.isEmpty() ? "Wi‑Fi local" : ip + ":" + FrameStreamServer.PORT) + " · pantallas " + count);
    }
}
