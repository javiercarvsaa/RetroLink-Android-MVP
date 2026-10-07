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

/** Biblioteca Atari 2600 con carátulas, 1P y sala RetroLink 2P. */
public class Atari2600LibraryActivity extends Activity {
    private static final int REQ_ROM = 491;

    private GridLayout grid;
    private TextView count, title, meta, state, coverStatus;
    private ImageView selectedCover;
    private Atari2600RomRepository.ImportedGame selected;
    private List<Atari2600RomRepository.ImportedGame> games;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_atari2600_library);
        InsetHelper.apply(findViewById(R.id.atariLibraryRoot));

        grid = findViewById(R.id.atariRomGrid);
        count = findViewById(R.id.txtAtariCount);
        title = findViewById(R.id.txtAtariTitle);
        meta = findViewById(R.id.txtAtariMeta);
        state = findViewById(R.id.txtAtariState);
        selectedCover = findViewById(R.id.imgAtariSelectedCover);
        coverStatus = findViewById(R.id.txtAtariCoverStatus);

        findViewById(R.id.btnAtariBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAtariImport).setOnClickListener(v -> pickRom());
        findViewById(R.id.btnAtariPlay).setOnClickListener(v -> play());
        findViewById(R.id.btnAtariMultiplayer).setOnClickListener(v -> {
            if (selected != null && selected.file.isFile())
                Atari2600RomRepository.select(this, selected);
            startActivity(new Intent(this, Atari2600RoomActivity.class));
        });
        findViewById(R.id.btnAtariRefreshCovers).setOnClickListener(v -> {
            Atari2600CoverArtManager.invalidateIndex(this);
            refresh();
        });

        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        games = Atari2600RomRepository.listGames(this);
        Atari2600RomRepository.ImportedGame last =
                Atari2600RomRepository.lastGame(this);
        selected = null;

        if (last != null) {
            for (Atari2600RomRepository.ImportedGame g : games)
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
            TextView empty = simpleCard(
                    "＋\nIMPORTAR PRIMER JUEGO\n.a26 / .bin", true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = available;
            lp.height = dp(150);
            empty.setLayoutParams(lp);
            empty.setOnClickListener(v -> pickRom());
            grid.addView(empty);
            return;
        }

        for (Atari2600RomRepository.ImportedGame g : games) {
            boolean active = selected != null && selected.file.equals(g.file);
            LinearLayout card = gameCard(g, active);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardW;
            lp.height = dp(180);
            lp.setMargins(0, 0, dp(10), dp(10));
            card.setLayoutParams(lp);

            card.setOnClickListener(v -> {
                selected = g;
                Atari2600RomRepository.select(this, g);
                refresh();
            });
            grid.addView(card);
        }

        TextView add = simpleCard(
                "＋\nIMPORTAR ROM\nAtari 2600", false);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cardW;
        lp.height = dp(180);
        lp.setMargins(0, 0, dp(10), dp(10));
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> pickRom());
        grid.addView(add);
    }

    private LinearLayout gameCard(Atari2600RomRepository.ImportedGame g,
                                  boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackgroundResource(
                active ? R.drawable.card_cyan_active : R.drawable.card_glass);
        card.setClickable(true);
        card.setFocusable(true);

        ImageView image = new ImageView(this);
        image.setBackgroundResource(R.drawable.card_blue);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTag(g.file.getAbsolutePath());
        image.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setPlaceholder(image);

        File cached = Atari2600CoverArtManager.cachedCover(this, g);
        if (cached != null) setCover(image, cached);
        else Atari2600CoverArtManager.request(this, g, (file, matched) -> {
            if (isFinishing() || isDestroyed()) return;
            Object tag = image.getTag();
            if (tag == null || !g.file.getAbsolutePath().equals(tag.toString()))
                return;
            if (file != null) setCover(image, file);
            refreshCoverStatus();
            if (selected != null && selected.file.equals(g.file))
                refreshSelectedCoverOnly();
        });

        TextView label = new TextView(this);
        label.setText(g.title + "\nAtari 2600");
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
        v.setBackgroundResource(
                active ? R.drawable.card_cyan_active : R.drawable.card_glass);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private void refreshSelected() {
        boolean has = selected != null && selected.file.isFile();

        title.setText(has ? selected.title : "Selecciona una ROM .a26 / .bin");
        meta.setText(has
                ? "Atari 2600 · Stella 2014 · ARM64"
                : "Atari Video Computer System");
        state.setText(has
                ? "● LISTO PARA JUGAR"
                : "○ SIN JUEGO SELECCIONADO");

        findViewById(R.id.btnAtariPlay).setEnabled(has);
        findViewById(R.id.btnAtariPlay).setAlpha(has ? 1f : 0.42f);

        // P2 no necesita ROM local.
        findViewById(R.id.btnAtariMultiplayer).setEnabled(true);
        findViewById(R.id.btnAtariMultiplayer).setAlpha(1f);

        refreshSelectedCoverOnly();
    }

    private void refreshSelectedCoverOnly() {
        if (selectedCover == null) return;
        if (selected == null) {
            setPlaceholder(selectedCover);
            return;
        }

        selectedCover.setTag(selected.file.getAbsolutePath());
        File cached = Atari2600CoverArtManager.cachedCover(this, selected);
        if (cached != null) {
            setCover(selectedCover, cached);
            return;
        }

        setPlaceholder(selectedCover);
        Atari2600RomRepository.ImportedGame requested = selected;
        Atari2600CoverArtManager.request(this, requested, (file, matched) -> {
            if (isFinishing() || isDestroyed() || selected == null) return;
            if (!selected.file.equals(requested.file)) return;
            if (file != null) setCover(selectedCover, file);
            refreshCoverStatus();
        });
    }

    private void refreshCoverStatus() {
        if (coverStatus == null || games == null) return;
        int found = Atari2600CoverArtManager.cachedCount(this, games);
        int total = games.size();

        if (total == 0)
            coverStatus.setText("Carátulas Atari 2600 · se buscan al importar");
        else if (found >= total)
            coverStatus.setText("✓ Carátulas " + found + "/" + total + " · caché local");
        else
            coverStatus.setText("Carátulas " + found + "/" + total
                    + " · búsqueda automática activa");
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

        Atari2600RomRepository.select(this, selected);
        Intent i = new Intent(this, Atari2600GameActivity.class);
        i.putExtra(Atari2600GameActivity.EXTRA_ROM_PATH,
                selected.file.getAbsolutePath());
        i.putExtra(Atari2600GameActivity.EXTRA_GAME_TITLE, selected.title);
        i.putExtra(Atari2600GameActivity.EXTRA_HOST_SESSION, false);
        startActivity(i);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_ROM || result != RESULT_OK
                || data == null || data.getData() == null)
            return;

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        try {
            selected = Atari2600RomRepository.importRom(this, uri);
            Toast.makeText(this,
                    "Atari 2600 añadido · buscando carátula",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "ROM Atari 2600 no válida: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        refresh();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
