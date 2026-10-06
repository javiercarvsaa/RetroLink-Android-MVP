package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Biblioteca real v0.6.2: solo muestra sistemas y acciones actualmente implementados. */
public class LibraryActivity extends Activity {
    public static final String EXTRA_OPEN_ROM_PICKER = "open_rom_picker";
    private static final int REQ_ROM = 221;

    private TextView selectedTitle, selectedMeta, selectedStatus, selectedPath;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_library);
        InsetHelper.apply(findViewById(R.id.libraryRoot));

        selectedTitle = findViewById(R.id.txtLibraryTitle);
        selectedMeta = findViewById(R.id.txtLibraryMeta);
        selectedStatus = findViewById(R.id.txtLibraryStatus);
        selectedPath = findViewById(R.id.txtLibraryPath);

        findViewById(R.id.btnLibraryBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLibraryPlay).setOnClickListener(v -> playSelected());
        findViewById(R.id.btnLibraryImport).setOnClickListener(v -> pickRom());
        findViewById(R.id.btnLibraryHost).setOnClickListener(v -> startActivity(new Intent(this, HostActivity.class)));
        findViewById(R.id.btnLibraryControls).setOnClickListener(v -> startActivity(new Intent(this, ControlTestActivity.class)));

        findViewById(R.id.navLibraryHome).setOnClickListener(v -> finish());
        findViewById(R.id.navLibraryRoom).setOnClickListener(v -> startActivity(new Intent(this, HostActivity.class)));
        findViewById(R.id.navLibraryProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.navLibrarySettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        refresh();
        if (getIntent().getBooleanExtra(EXTRA_OPEN_ROM_PICKER, false)) {
            findViewById(R.id.libraryRoot).postDelayed(this::pickRom, 180);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        File rom = N64RomRepository.lastRom(this);
        String summary = RetroPreferences.lastRomSummary(this);
        boolean hasRom = rom != null;
        selectedTitle.setText(hasRom ? shortTitle(summary) : "Selecciona una ROM N64");
        selectedMeta.setText(hasRom ? summary : "Nintendo 64 · Mupen64Plus-Next · ROM del usuario");
        selectedStatus.setText(hasRom ? "● LISTO PARA JUGAR" : "○ SIN JUEGO SELECCIONADO");
        selectedPath.setText(hasRom ? "Archivo local: " + rom.getName() : "RetroLink no incluye ROM, BIOS ni contenido comercial.");
        findViewById(R.id.btnLibraryPlay).setEnabled(hasRom);
        findViewById(R.id.btnLibraryPlay).setAlpha(hasRom ? 1f : 0.45f);
    }

    private String shortTitle(String summary) {
        if (summary == null || summary.isEmpty()) return "Juego N64 seleccionado";
        int dot = summary.indexOf(" · ");
        return dot > 0 ? summary.substring(0, dot) : summary;
    }

    private void pickRom() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_ROM);
    }

    private void playSelected() {
        File rom = N64RomRepository.lastRom(this);
        if (rom == null) {
            pickRom();
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

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_ROM || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) {}
        try {
            N64RomRepository.ImportedRom imported = N64RomRepository.importRom(this, uri);
            Toast.makeText(this, "ROM N64 reconocida · " + shortTitle(imported.info.summary()), Toast.LENGTH_SHORT).show();
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "ROM N64 no válida: " + e.getMessage(), Toast.LENGTH_LONG).show();
            refresh();
        }
    }
}
