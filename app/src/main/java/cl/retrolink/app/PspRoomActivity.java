package cl.retrolink.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** Sala PSP: dos teléfonos, dos instancias PPSSPP y red Ad Hoc. */
public class PspRoomActivity extends Activity {
    private static final int REQ_NEARBY_WIFI = 714;
    private EditText hostIp;
    private TextView game, localIp, state;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_psp_room);
        InsetHelper.apply(findViewById(R.id.pspRoomRoot));
        hostIp = findViewById(R.id.editPspHostIp);
        game = findViewById(R.id.txtPspRoomGame);
        localIp = findViewById(R.id.txtPspLocalIp);
        state = findViewById(R.id.txtPspRoomState);

        findViewById(R.id.btnPspRoomBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPspRoomHost).setOnClickListener(v -> launch(PspIniManager.Mode.HOST));
        findViewById(R.id.btnPspRoomJoin).setOnClickListener(v -> launch(PspIniManager.Mode.CLIENT));
        findViewById(R.id.btnPspRoomEngine).setOnClickListener(v -> {
            try { PspLauncher.launchMenu(this); }
            catch (Exception e) { toast("No se pudo abrir PPSSPP: " + e.getMessage()); }
        });
        findViewById(R.id.btnPspRoomLibrary).setOnClickListener(v ->
                startActivity(new Intent(this, PspLibraryActivity.class)));
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        PspRomRepository.ImportedGame selected = PspRomRepository.lastGame(this);
        game.setText(selected == null ? "Sin juego PSP seleccionado" : selected.title);
        String ip = PspNetworkInfo.localIpv4(this);
        localIp.setText("IP de este teléfono: " + ip);
        if (PspNetworkInfo.isValidIpv4(ip) && hostIp.getText().toString().trim().isEmpty())
            hostIp.setHint("IP del host, por ejemplo " + ip);
        state.setText("Mismo juego en ambos teléfonos · P1 crea host · P2 escribe la IP del P1");
    }

    private void launch(PspIniManager.Mode mode) {
        if (!ensureNearbyWifiPermission()) return;
        PspRomRepository.ImportedGame selected = PspRomRepository.lastGame(this);
        if (selected == null || !selected.file.isFile()) {
            toast("Importa y selecciona el mismo juego PSP en este teléfono");
            startActivity(new Intent(this, PspLibraryActivity.class));
            return;
        }
        String server = hostIp.getText().toString().trim();
        if (mode == PspIniManager.Mode.CLIENT && !PspNetworkInfo.isValidIpv4(server)) {
            toast("Escribe la IP local que muestra el teléfono P1");
            return;
        }
        try {
            state.setText(mode == PspIniManager.Mode.HOST
                    ? "Abriendo PSP P1 · servidor Ad Hoc local activo"
                    : "Abriendo PSP P2 · conectando a " + server);
            PspLauncher.launchGame(this, selected.file, mode, server);
        } catch (Exception e) { toast("No se pudo iniciar la sesión: " + e.getMessage()); }
    }

    private boolean ensureNearbyWifiPermission() {
        if (Build.VERSION.SDK_INT < 33) return true;
        if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED)
            return true;
        requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, REQ_NEARBY_WIFI);
        toast("Autoriza Dispositivos cercanos y vuelve a iniciar la sala PSP");
        return false;
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
