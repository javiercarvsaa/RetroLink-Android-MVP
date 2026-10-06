package cl.retrolink.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_BT_PERMISSIONS = 100;
    private static final int REQ_ENABLE_BT = 101;

    private TextView status;
    private BluetoothAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        InsetHelper.apply(findViewById(R.id.mainRoot));

        status = findViewById(R.id.txtMainStatus);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;

        findViewById(R.id.btnQuickPlay).setOnClickListener(v -> openMode(HostActivity.class, false));
        findViewById(R.id.btnHostMode).setOnClickListener(v -> openMode(HostActivity.class, false));
        findViewById(R.id.btnHostLocalMode).setOnClickListener(v -> openMode(HostActivity.class, false));
        findViewById(R.id.btnLibraryMode).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.btnControllerMode).setOnClickListener(v -> openMode(ControllerActivity.class, false));
        findViewById(R.id.btnControlsMode).setOnClickListener(v -> startActivity(new Intent(this, ControlTestActivity.class)));
        findViewById(R.id.btnSettingsMode).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.navGames).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.navRoom).setOnClickListener(v -> openMode(HostActivity.class, false));
        findViewById(R.id.navProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.navSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        TextView last = findViewById(R.id.txtLastGame);
        String summary = RetroPreferences.lastRomSummary(this);
        last.setText(summary == null || summary.isEmpty() ? "N64 integrado · listo para importar tu ROM" : summary);

        ensurePermissions();
        updateStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
        TextView last = findViewById(R.id.txtLastGame);
        String summary = RetroPreferences.lastRomSummary(this);
        if (last != null && summary != null && !summary.isEmpty()) last.setText(summary);
    }

    private void ensurePermissions() {
        if (!BluetoothPermissionHelper.hasAll(this)) requestPermissions(BluetoothPermissionHelper.requiredPermissions(), REQ_BT_PERMISSIONS);
    }

    private void openMode(Class<?> target, boolean openLibrary) {
        if (adapter == null) {
            Toast.makeText(this, "Este dispositivo no dispone de Bluetooth.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!BluetoothPermissionHelper.hasAll(this)) {
            ensurePermissions();
            Toast.makeText(this, "Autoriza Dispositivos cercanos y vuelve a tocar el modo.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!adapter.isEnabled()) {
            try { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT); }
            catch (Exception ex) { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
            Toast.makeText(this, "Activa Bluetooth y vuelve a tocar el modo.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, target);
        if (target == HostActivity.class && openLibrary) i.putExtra(HostActivity.EXTRA_OPEN_ROM_PICKER, true);
        startActivity(i);
    }

    private void updateStatus() {
        if (adapter == null) status.setText("Bluetooth no disponible");
        else if (!BluetoothPermissionHelper.hasAll(this)) status.setText("Autoriza dispositivos cercanos");
        else if (!adapter.isEnabled()) status.setText("Bluetooth apagado");
        else status.setText("●  Dispositivo listo · RetroLink local");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERMISSIONS) updateStatus();
    }
}
