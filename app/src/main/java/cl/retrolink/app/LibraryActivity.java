package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Biblioteca N64 funcional con carátulas automáticas y caché local. */
public class LibraryActivity extends Activity {
    public static final String EXTRA_OPEN_ROM_PICKER = "open_rom_picker";
    private static final int REQ_ROM = 221;

    private TextView selectedTitle, selectedMeta, selectedStatus, selectedPath, countText, coverStatus;
    private ImageView selectedCover;
    private GridLayout grid;
    private N64RomRepository.ImportedRom selected;
    private List<N64RomRepository.ImportedRom> currentRoms;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_library);
        InsetHelper.apply(findViewById(R.id.libraryRoot));

        selectedTitle = findViewById(R.id.txtLibraryTitle);
        selectedMeta = findViewById(R.id.txtLibraryMeta);
        selectedStatus = findViewById(R.id.txtLibraryStatus);
        selectedPath = findViewById(R.id.txtLibraryPath);
        selectedCover = findViewById(R.id.imgLibrarySelectedCover);
        countText = findViewById(R.id.txtLibraryCount);
        coverStatus = findViewById(R.id.txtLibraryCoverStatus);
        grid = findViewById(R.id.romGrid);

        findViewById(R.id.btnLibraryBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLibraryPlay).setOnClickListener(v -> playSelected());
        findViewById(R.id.btnLibraryImport).setOnClickListener(v -> pickRom());
        findViewById(R.id.btnLibraryHost).setOnClickListener(v -> openHost());
        findViewById(R.id.btnLibraryControls).setOnClickListener(v -> startActivity(new Intent(this, ControlTestActivity.class)));
        findViewById(R.id.btnLibraryRefreshCovers).setOnClickListener(v -> refreshCovers());
        findViewById(R.id.btnLibraryGameBoy).setOnClickListener(v -> startActivity(new Intent(this, GameBoyLibraryActivity.class)));

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
        currentRoms = N64RomRepository.listRoms(this);
        File last = N64RomRepository.lastRom(this);
        selected = null;
        for (N64RomRepository.ImportedRom r : currentRoms) {
            if (last != null && last.equals(r.file)) { selected = r; break; }
        }
        if (selected == null && !currentRoms.isEmpty()) {
            selected = currentRoms.get(0);
            N64RomRepository.select(this, selected);
        }
        countText.setText(currentRoms.size() + (currentRoms.size() == 1 ? " juego N64" : " juegos N64"));
        updateCoverStatus();
        refreshSelected();
        grid.post(() -> rebuildGrid(currentRoms));
    }

    private void rebuildGrid(List<N64RomRepository.ImportedRom> roms) {
        grid.removeAllViews();
        if (roms == null || roms.isEmpty()) {
            TextView empty = simpleCard("＋\nIMPORTAR PRIMER JUEGO\nToca para seleccionar una ROM N64", R.drawable.card_cyan_active);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Math.max(dp(150), grid.getWidth() > 0 ? grid.getWidth() : dp(360));
            lp.height = dp(128); lp.setMargins(0, 0, 0, dp(8));
            empty.setLayoutParams(lp);
            empty.setOnClickListener(v -> pickRom());
            grid.addView(empty);
            return;
        }

        int available = grid.getWidth() > 0 ? grid.getWidth() : dp(380);
        int width = Math.max(dp(150), (available - dp(8)) / 2);
        for (N64RomRepository.ImportedRom rom : roms) {
            View card = gameCard(rom);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = width; lp.height = dp(158); lp.setMargins(0, 0, dp(8), dp(8));
            card.setLayoutParams(lp);
            card.setOnClickListener(view -> {
                selected = rom;
                N64RomRepository.select(this, rom);
                refresh();
            });
            grid.addView(card);
        }

        TextView add = simpleCard("＋\nIMPORTAR ROM\nAñadir otro juego N64", R.drawable.card_purple);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = width; lp.height = dp(158); lp.setMargins(0, 0, dp(8), dp(8));
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> pickRom());
        grid.addView(add);
    }

    private View gameCard(N64RomRepository.ImportedRom rom) {
        boolean active = selected != null && selected.file.equals(rom.file);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(active ? R.drawable.card_cyan_active : R.drawable.card_glass);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setClickable(true);
        card.setFocusable(true);

        ImageView image = new ImageView(this);
        image.setContentDescription("Carátula de " + rom.info.title);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundResource(R.drawable.card_blue);
        image.setClipToOutline(true);
        image.setTag(rom.file.getAbsolutePath());
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        image.setLayoutParams(imageLp);
        setPlaceholder(image);

        File cached = CoverArtManager.cachedCover(this, rom);
        if (cached != null) setCover(image, cached);
        else CoverArtManager.request(this, rom, (file, matched) -> {
            if (isFinishing() || isDestroyed()) return;
            Object tag = image.getTag();
            if (tag == null || !rom.file.getAbsolutePath().equals(tag.toString())) return;
            if (file != null) setCover(image, file);
            updateCoverStatus();
            if (selected != null && selected.file.equals(rom.file)) refreshSelectedCoverOnly();
        });

        TextView title = new TextView(this);
        String name = rom.info.title == null || rom.info.title.trim().isEmpty() ? "Juego N64" : rom.info.title.trim();
        title.setText(name + "\n" + rom.info.friendlyRegion());
        title.setTextColor(getColor(R.color.retro_text));
        title.setTextSize(8.5f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setMaxLines(2);
        title.setPadding(dp(5), dp(4), dp(5), 0);
        title.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        card.addView(image);
        card.addView(title);
        return card;
    }

    private TextView simpleCard(String text, int background) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(getColor(R.color.retro_text));
        v.setTextSize(10f);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(14), dp(12), dp(14), dp(14));
        v.setBackgroundResource(background);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private void refreshSelected() {
        boolean hasRom = selected != null && selected.file.isFile();
        selectedTitle.setText(hasRom ? selected.info.title : "Selecciona una ROM N64");
        selectedMeta.setText(hasRom ? selected.info.friendlyRegion() + " · " + selected.info.revisionText() + " · " + selected.info.byteOrder : "Nintendo 64 · Mupen64Plus-Next");
        selectedStatus.setText(hasRom ? "● LISTO PARA JUGAR" : "○ SIN JUEGO SELECCIONADO");
        selectedPath.setText(hasRom ? "Carátula automática · " + CoverArtManager.sourceLabel() : "RetroLink no incluye ROM, BIOS ni contenido comercial.");
        findViewById(R.id.btnLibraryPlay).setEnabled(hasRom);
        findViewById(R.id.btnLibraryPlay).setAlpha(hasRom ? 1f : 0.42f);
        findViewById(R.id.btnLibraryHost).setEnabled(hasRom);
        findViewById(R.id.btnLibraryHost).setAlpha(hasRom ? 1f : 0.42f);
        refreshSelectedCoverOnly();
    }

    private void refreshSelectedCoverOnly() {
        if (selectedCover == null) return;
        if (selected == null) { setPlaceholder(selectedCover); return; }
        selectedCover.setTag(selected.file.getAbsolutePath());
        File cached = CoverArtManager.cachedCover(this, selected);
        if (cached != null) {
            setCover(selectedCover, cached);
            return;
        }
        setPlaceholder(selectedCover);
        CoverArtManager.request(this, selected, (file, matched) -> {
            if (isFinishing() || isDestroyed() || selected == null) return;
            Object tag = selectedCover.getTag();
            if (tag == null || !selected.file.getAbsolutePath().equals(tag.toString())) return;
            if (file != null) setCover(selectedCover, file);
            updateCoverStatus();
        });
    }

    private void updateCoverStatus() {
        if (coverStatus == null || currentRoms == null) return;
        int found = CoverArtManager.cachedCount(this, currentRoms);
        int total = currentRoms.size();
        if (total == 0) coverStatus.setText("Carátulas automáticas · se buscan al importar juegos");
        else if (found >= total) coverStatus.setText("✓ Carátulas " + found + "/" + total + " · caché local");
        else coverStatus.setText("Carátulas " + found + "/" + total + " · búsqueda automática activa");
    }

    private void refreshCovers() {
        CoverArtManager.invalidateIndex(this);
        coverStatus.setText("Actualizando catálogo de carátulas…");
        if (currentRoms == null || currentRoms.isEmpty()) return;
        // Las imágenes existentes se conservan; las faltantes vuelven a consultar el catálogo remoto.
        grid.post(() -> rebuildGrid(currentRoms));
        refreshSelectedCoverOnly();
    }

    private void setPlaceholder(ImageView image) {
        image.setImageResource(R.mipmap.ic_launcher);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(22), dp(14), dp(22), dp(14));
        image.setAlpha(0.30f);
    }

    private void setCover(ImageView image, File file) {
        if (image == null || file == null || !file.isFile()) return;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return;
        image.setPadding(0, 0, 0, 0);
        image.setAlpha(1f);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageBitmap(bitmap);
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
            Toast.makeText(this, "Juego añadido · buscando carátula automáticamente", Toast.LENGTH_SHORT).show();
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "ROM N64 no válida: " + e.getMessage(), Toast.LENGTH_LONG).show();
            refresh();
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
