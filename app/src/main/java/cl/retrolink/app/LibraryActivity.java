package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Biblioteca funcional: muestra únicamente ROM N64 realmente importadas por el usuario. */
public class LibraryActivity extends Activity {
    public static final String EXTRA_OPEN_ROM_PICKER = "open_rom_picker";
    private static final int REQ_ROM = 221;

    private TextView selectedTitle, selectedMeta, selectedStatus, selectedPath, countText;
    private GridLayout grid;
    private N64RomRepository.ImportedRom selected;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_library);
        InsetHelper.apply(findViewById(R.id.libraryRoot));

        selectedTitle = findViewById(R.id.txtLibraryTitle);
        selectedMeta = findViewById(R.id.txtLibraryMeta);
        selectedStatus = findViewById(R.id.txtLibraryStatus);
        selectedPath = findViewById(R.id.txtLibraryPath);
        countText = findViewById(R.id.txtLibraryCount);
        grid = findViewById(R.id.romGrid);

        findViewById(R.id.btnLibraryBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLibraryPlay).setOnClickListener(v -> playSelected());
        findViewById(R.id.btnLibraryImport).setOnClickListener(v -> pickRom());
        findViewById(R.id.btnLibraryHost).setOnClickListener(v -> openHost());
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
        List<N64RomRepository.ImportedRom> roms = N64RomRepository.listRoms(this);
        File last = N64RomRepository.lastRom(this);
        selected = null;
        for (N64RomRepository.ImportedRom r : roms) {
            if (last != null && last.equals(r.file)) { selected = r; break; }
        }
        if (selected == null && !roms.isEmpty()) {
            selected = roms.get(0);
            N64RomRepository.select(this, selected);
        }
        countText.setText(roms.size() + (roms.size() == 1 ? " juego N64" : " juegos N64"));
        rebuildGrid(roms);
        refreshSelected();
    }

    private void rebuildGrid(List<N64RomRepository.ImportedRom> roms) {
        grid.removeAllViews();
        if (roms.isEmpty()) {
            TextView empty = card("＋\nIMPORTAR PRIMER JUEGO\nToca para seleccionar una ROM N64", R.drawable.card_cyan_active);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(178); lp.height = dp(118); lp.setMargins(0, 0, dp(8), dp(8));
            empty.setLayoutParams(lp);
            empty.setOnClickListener(v -> pickRom());
            grid.addView(empty);
            return;
        }
        for (N64RomRepository.ImportedRom rom : roms) {
            String title = rom.info.title == null || rom.info.title.trim().isEmpty() ? "Juego N64" : rom.info.title.trim();
            String text = "N64\n" + title + "\n" + rom.info.friendlyRegion();
            boolean active = selected != null && selected.file.equals(rom.file);
            TextView v = card(text, active ? R.drawable.card_cyan_active : R.drawable.card_glass);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(178); lp.height = dp(118); lp.setMargins(0, 0, dp(8), dp(8));
            v.setLayoutParams(lp);
            v.setOnClickListener(view -> {
                selected = rom;
                N64RomRepository.select(this, rom);
                refresh();
            });
            grid.addView(v);
        }
        TextView add = card("＋\nIMPORTAR ROM\nAñadir otro juego N64", R.drawable.card_purple);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(178); lp.height = dp(118); lp.setMargins(0, 0, dp(8), dp(8));
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> pickRom());
        grid.addView(add);
    }

    private TextView card(String text, int background) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(getColor(R.color.retro_text));
        v.setTextSize(10f);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        v.setPadding(dp(14), dp(12), dp(14), dp(14));
        v.setBackgroundResource(background);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private void refreshSelected() {
        boolean hasRom = selected != null && selected.file.isFile();
        selectedTitle.setText(hasRom ? selected.info.title : "Selecciona una ROM N64");
        selectedMeta.setText(hasRom ? selected.info.summary() : "Nintendo 64 · Mupen64Plus-Next · ROM del usuario");
        selectedStatus.setText(hasRom ? "● LISTO PARA JUGAR" : "○ SIN JUEGO SELECCIONADO");
        selectedPath.setText(hasRom ? "Archivo local: " + selected.file.getName() : "RetroLink no incluye ROM, BIOS ni contenido comercial.");
        findViewById(R.id.btnLibraryPlay).setEnabled(hasRom);
        findViewById(R.id.btnLibraryPlay).setAlpha(hasRom ? 1f : 0.42f);
        findViewById(R.id.btnLibraryHost).setEnabled(hasRom);
        findViewById(R.id.btnLibraryHost).setAlpha(hasRom ? 1f : 0.42f);
    }

    private void pickRom() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_ROM);
    }

    private void playSelected() {
        if (selected == null || !selected.file.isFile()) { pickRom(); return; }
        N64RomRepository.select(this, selected);
        SessionState.reset();
        SessionState.setConfiguredPlayers(1);
        InputHub.resetAll();
        EmulatorFrameHub.clear();
        Intent i = new Intent(this, IntegratedN64Activity.class);
        i.putExtra(IntegratedN64Activity.EXTRA_ROM_PATH, selected.file.getAbsolutePath());
        i.putExtra(IntegratedN64Activity.EXTRA_PLAYERS, 1);
        startActivity(i);
    }

    private void openHost() {
        if (selected == null || !selected.file.isFile()) { pickRom(); return; }
        N64RomRepository.select(this, selected);
        startActivity(new Intent(this, HostActivity.class));
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_ROM || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) {}
        try {
            N64RomRepository.ImportedRom imported = N64RomRepository.importRom(this, uri);
            selected = imported;
            Toast.makeText(this, "Juego añadido · " + imported.info.title, Toast.LENGTH_SHORT).show();
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "ROM N64 no válida: " + e.getMessage(), Toast.LENGTH_LONG).show();
            refresh();
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
