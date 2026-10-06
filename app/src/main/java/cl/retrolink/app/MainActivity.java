package cl.retrolink.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Inicio funcional: cada tarjeta visible ejecuta una acción real. */
public class MainActivity extends Activity {
    private static final int REQ_BT_PERMISSIONS = 100;
    private static final int REQ_ENABLE_BT = 101;

    private TextView status, lastGame, lastMeta, continueButton, librarySummary;
    private BluetoothAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        InsetHelper.apply(findViewById(R.id.mainRoot));

        status = findViewById(R.id.txtMainStatus);
        lastGame = findViewById(R.id.txtLastGame);
        lastMeta = findViewById(R.id.txtLastGameMeta);
        continueButton = findViewById(R.id.btnQuickPlay);
        librarySummary = findViewById(R.id.txtLibrarySummary);

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;

        // ÚLTIMA PARTIDA: abre directamente la ROM real guardada; si no existe, abre Biblioteca.
        continueButton.setOnClickListener(v -> continueLastGame());

        // JUGAR AHORA: abre Biblioteca y dispara el selector de ROM N64.
        findViewById(R.id.btnPlayNow).setOnClickListener(v -> {
            Intent i = new Intent(this, LibraryActivity.class);
            i.putExtra(LibraryActivity.EXTRA_OPEN_ROM_PICKER, true);
            startActivity(i);
        });
        findViewById(R.id.btnLibraryMode).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.btnHostLocalMode).setOnClickListener(v -> openBluetoothMode(HostActivity.class));
        findViewById(R.id.btnControllerMode).setOnClickListener(v -> openBluetoothMode(ControllerActivity.class));
        findViewById(R.id.btnControlsMode).setOnClickListener(v -> startActivity(new Intent(this, ControlTestActivity.class)));
        findViewById(R.id.btnSettingsMode).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.navGames).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.navRoom).setOnClickListener(v -> openBluetoothMode(HostActivity.class));
        findViewById(R.id.navProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.navSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        File rom = N64RomRepository.lastRom(this);
        String summary = RetroPreferences.lastRomSummary(this);
        boolean hasRom = rom != null;
        lastGame.setText(hasRom && summary != null && !summary.isEmpty() ? shortTitle(summary) : "Sin juego reciente");
        lastMeta.setText(hasRom ? "Nintendo 64 · listo para continuar" : "Importa una ROM N64 desde Jugar ahora o Biblioteca");
        continueButton.setText(hasRom ? "▶  CONTINUAR" : "＋  ELEGIR JUEGO");
        int games = N64RomRepository.listRoms(this).size();
        String gamepad = AndroidGamepadMapper.connectedGamepadsSummary();
        librarySummary.setText((games == 0 ? "Biblioteca N64 vacía" : games + (games == 1 ? " juego N64 importado" : " juegos N64 importados"))
                + "\n" + (gamepad.startsWith("●") ? gamepad : "Controles táctiles + gamepad compatibles"));
        updateStatus();
    }

    private String shortTitle(String summary) {
        int dot = summary == null ? -1 : summary.indexOf(" · ");
        return dot > 0 ? summary.substring(0, dot) : summary;
    }

    private void continueLastGame() {
        File rom = N64RomRepository.lastRom(this);
        if (rom == null) {
            startActivity(new Intent(this, LibraryActivity.class).putExtra(LibraryActivity.EXTRA_OPEN_ROM_PICKER, true));
            return;
        }
        SessionState.reset();
        SessionState.setConfiguredPlayers(1);
        InputHub.resetAll();
        EmulatorFrameHub.clear();
        Intent i = new Intent(this, IntegratedN64Activity.class);
        i.putExtra(IntegratedN64Activity.EXTRA_ROM_PATH, rom.getAbsolutePath());
        i.putExtra(IntegratedN64Activity.EXTRA_PLAYERS, 1);
        startActivity(i);
    }

    private void openBluetoothMode(Class<?> target) {
        if (adapter == null) {
            Toast.makeText(this, "Este dispositivo no dispone de Bluetooth.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!BluetoothPermissionHelper.hasAll(this)) {
            requestPermissions(BluetoothPermissionHelper.requiredPermissions(), REQ_BT_PERMISSIONS);
            Toast.makeText(this, "Autoriza Dispositivos cercanos y vuelve a tocar la opción.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!adapter.isEnabled()) {
            try { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT); }
            catch (Exception ex) { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
            Toast.makeText(this, "Activa Bluetooth para usar el multijugador local.", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, target));
    }

    private void updateStatus() {
        String gamepad = AndroidGamepadMapper.connectedGamepadsSummary();
        if (gamepad.startsWith("●")) {
            status.setText(gamepad);
            return;
        }
        if (adapter == null) status.setText("Juego local listo · Bluetooth no disponible");
        else if (!adapter.isEnabled()) status.setText("Juego local listo · Bluetooth apagado");
        else if (!BluetoothPermissionHelper.hasAll(this)) status.setText("Juego local listo · permiso multijugador pendiente");
        else status.setText("●  RetroLink listo · Bluetooth disponible");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERMISSIONS) updateStatus();
    }
}
