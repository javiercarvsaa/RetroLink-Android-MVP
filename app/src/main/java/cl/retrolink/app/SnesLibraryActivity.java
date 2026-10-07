package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Biblioteca Super Nintendo con carátulas, 1P y sala RetroLink 2P. */
public class SnesLibraryActivity extends Activity {
    private static final int REQ_ROM = 381;

    private GridLayout grid;
    private TextView count, title, meta, state, coverStatus;
    private ImageView selectedCover;
    private SnesRomRepository.ImportedGame selected;
    private List<SnesRomRepository.ImportedGame> games;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_snes_library);
        InsetHelper.apply(findViewById(R.id.snesLibraryRoot));

        grid = findViewById(R.id.snesRomGrid);
        count = findViewById(R.id.txtSnesCount);
        title = findViewById(R.id.txtSnesTitle);
        meta = findViewById(R.id.txtSnesMeta);
        state = findViewById(R.id.txtSnesState);
        selectedCover = findViewById(R.id.imgSnesSelectedCover);
        coverStatus = findViewById(R.id.txtSnesCoverStatus);

        findViewById(R.id.btnSnesBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSnesImport).setOnClickListener(v -> pickRom());
        findViewById(R.id.btnSnesPlay).setOnClickListener(v -> play());
        findViewById(R.id.btnSnesMultiplayer).setOnClickListener(v -> {
            if (selected != null && selected.file.isFile())
                SnesRomRepository.select(this, selected);
            startActivity(new Intent(this, SnesRoomActivity.class));
        });
        findViewById(R.id.btnSnesRefreshCovers).setOnClickListener(v -> {
            SnesCoverArtManager.invalidateIndex(this);
            refresh();
        });

        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        games = SnesRomRepository.listGames(this);
        SnesRomRepository.ImportedGame last = SnesRomRepository.lastGame(this);
        selected = null;

        if (last != null) {
            for (SnesRomRepository.ImportedGame g : games)
                if (last.file.equals(g.file)) {
                    selected = g;
                    break;
                }
        }
        if (selected == null && !games.isEmpty()) selected = games.get(0);

        count.setText(games.size() + (games.size() == 1 ? " juego" : " juegos"));
        refreshSelected();
        refreshCoverStatus();
        grid.post(this::rebuildGrid);
    }

    private void rebuildGrid() {
        grid.removeAllViews();
        int available = grid.getWidth() > 0 ? grid.getWidth() : dp(620);
        int cardW = Math.max(dp(190), (available - dp(10)) / 2);

        if (games.isEmpty()) {
            TextView empty = simpleCard("＋\nIMPORTAR PRIMER JUEGO\n.sfc / .smc", true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = available;
            lp.height = dp(150);
            empty.setLayoutParams(lp);
            empty.setOnClickListener(v -> pickRom());
            grid.addView(empty);
            return;
        }

        for (SnesRomRepository.ImportedGame g : games) {
            boolean active = selected != null && selected.file.equals(g.file);
            LinearLayout card = gameCard(g, active);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardW;
            lp.height = dp(180);
            lp.setMargins(0, 0, dp(10), dp(10));
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> {
                selected = g;
                SnesRomRepository.select(this, g);
                refresh();
            });
            grid.addView(card);
        }

        TextView add = simpleCard("＋\nIMPORTAR ROM\nSuper Nintendo", false);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cardW;
        lp.height = dp(180);
        lp.setMargins(0, 0, dp(10), dp(10));
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> pickRom());
        grid.addView(add);
    }

    private LinearLayout gameCard(SnesRomRepository.ImportedGame g, boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackgroundResource(active ? R.drawable.card_cyan_active : R.drawable.card_glass);
        card.setClickable(true);
        card.setFocusable(true);

        ImageView image = new ImageView(this);
        image.setBackgroundResource(R.drawable.card_blue);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTag(g.file.getAbsolutePath());
        image.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setPlaceholder(image);

        File cached = SnesCoverArtManager.cachedCover(this, g);
        if (cached != null) setCover(image, cached);
        else SnesCoverArtManager.request(this, g, (file, matched) -> {
            if (isFinishing() || isDestroyed()) return;
            Object tag = image.getTag();
            if (tag == null || !g.file.getAbsolutePath().equals(tag.toString())) return;
            if (file != null) setCover(image, file);
            refreshCoverStatus();
            if (selected != null && selected.file.equals(g.file)) refreshSelectedCoverOnly();
        });

        TextView label = new TextView(this);
        label.setText(g.title + "\nSuper Nintendo");
        label.setTextColor(getColor(R.color.retro_text));
        label.setTextSize(8.5f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        label.setMaxLines(2);
        label.setPadding(dp(5), dp(4), dp(5), 0);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        card.addView(image);
        card.addView(label);
        return card;
    }

    private TextView simpleCard(String text, boolean active) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(getColor(R.color.retro_text));
        v.setTextSize(10f);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackgroundResource(active ? R.drawable.card_cyan_active : R.drawable.card_glass);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private void refreshSelected() {
        boolean has = selected != null && selected.file.isFile();
        title.setText(has ? selected.title : "Selecciona una ROM .sfc / .smc");
        meta.setText(has ? "Super Nintendo · Snes9x · ARM64" : "Super Nintendo Entertainment System");
        state.setText(has ? "● LISTO PARA JUGAR" : "○ SIN JUEGO SELECCIONADO");

        findViewById(R.id.btnSnesPlay).setEnabled(has);
        findViewById(R.id.btnSnesPlay).setAlpha(has ? 1f : 0.42f);

        // P2 puede entrar a una sala sin ROM local.
        findViewById(R.id.btnSnesMultiplayer).setEnabled(true);
        findViewById(R.id.btnSnesMultiplayer).setAlpha(1f);

        refreshSelectedCoverOnly();
    }

    private void refreshSelectedCoverOnly() {
        if (selectedCover == null) return;
        if (selected == null) {
            setPlaceholder(selectedCover);
            return;
        }

        selectedCover.setTag(selected.file.getAbsolutePath());
        File cached = SnesCoverArtManager.cachedCover(this, selected);
        if (cached != null) {
            setCover(selectedCover, cached);
            return;
        }

        setPlaceholder(selectedCover);
        SnesRomRepository.ImportedGame requested = selected;
        SnesCoverArtManager.request(this, requested, (file, matched) -> {
            if (isFinishing() || isDestroyed() || selected == null) return;
            if (!selected.file.equals(requested.file)) return;
            if (file != null) setCover(selectedCover, file);
            refreshCoverStatus();
        });
    }

    private void refreshCoverStatus() {
        if (coverStatus == null || games == null) return;
        int found = SnesCoverArtManager.cachedCount(this, games);
        int total = games.size();
        if (total == 0)
            coverStatus.setText("Carátulas SNES · se buscan al importar");
        else if (found >= total)
            coverStatus.setText("✓ Carátulas " + found + "/" + total + " · caché local");
        else
            coverStatus.setText("Carátulas " + found + "/" + total + " · búsqueda automática activa");
    }

    private void setPlaceholder(ImageView image) {
        image.setImageResource(R.mipmap.ic_launcher);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(18), dp(12), dp(18), dp(12));
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

    private void play() {
        if (selected == null || !selected.file.isFile()) {
            pickRom();
            return;
        }
        SnesRomRepository.select(this, selected);
        Intent i = new Intent(this, SnesGameActivity.class);
        i.putExtra(SnesGameActivity.EXTRA_ROM_PATH, selected.file.getAbsolutePath());
        i.putExtra(SnesGameActivity.EXTRA_GAME_TITLE, selected.title);
        i.putExtra(SnesGameActivity.EXTRA_HOST_SESSION, false);
        startActivity(i);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_ROM || result != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        try {
            selected = SnesRomRepository.importRom(this, uri);
            Toast.makeText(this, "SNES añadido · buscando carátula", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "ROM SNES no válida: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
