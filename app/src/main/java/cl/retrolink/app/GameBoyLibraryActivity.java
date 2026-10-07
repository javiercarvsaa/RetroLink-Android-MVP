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

/** Biblioteca funcional Game Boy / Game Boy Color con carátulas automáticas. */
public class GameBoyLibraryActivity extends Activity {
    private static final int REQ_ROM = 271;
    private GridLayout grid;
    private TextView count, title, meta, state, coverStatus;
    private ImageView selectedCover;
    private GameBoyRomRepository.ImportedGame selected;
    private List<GameBoyRomRepository.ImportedGame> games;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_gameboy_library);
        InsetHelper.apply(findViewById(R.id.gbLibraryRoot));
        grid = findViewById(R.id.gbRomGrid);
        count = findViewById(R.id.txtGbCount);
        title = findViewById(R.id.txtGbTitle);
        meta = findViewById(R.id.txtGbMeta);
        state = findViewById(R.id.txtGbState);
        selectedCover = findViewById(R.id.imgGbSelectedCover);
        coverStatus = findViewById(R.id.txtGbCoverStatus);

        findViewById(R.id.btnGbBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnGbImport).setOnClickListener(v -> pickRom());
        findViewById(R.id.btnGbPlay).setOnClickListener(v -> play());
        findViewById(R.id.btnGbRefreshCovers).setOnClickListener(v -> {
            GameBoyCoverArtManager.invalidateIndexes(this);
            refresh();
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        games = GameBoyRomRepository.listGames(this);
        GameBoyRomRepository.ImportedGame last = GameBoyRomRepository.lastGame(this);
        selected = null;
        if (last != null) {
            for (GameBoyRomRepository.ImportedGame g : games)
                if (last.file.equals(g.file)) { selected = g; break; }
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
        int cardW = Math.max(dp(180), (available - dp(10)) / 2);
        if (games.isEmpty()) {
            TextView empty = simpleCard("＋\nIMPORTAR PRIMER JUEGO\n.gb / .gbc", true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = available; lp.height = dp(150);
            empty.setLayoutParams(lp);
            empty.setOnClickListener(v -> pickRom());
            grid.addView(empty);
            return;
        }

        for (GameBoyRomRepository.ImportedGame game : games) {
            boolean active = selected != null && selected.file.equals(game.file);
            LinearLayout card = gameCard(game, active);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardW; lp.height = dp(172); lp.setMargins(0, 0, dp(10), dp(10));
            card.setLayoutParams(lp);
            card.setOnClickListener(x -> {
                selected = game;
                GameBoyRomRepository.select(this, game);
                refresh();
            });
            grid.addView(card);
        }

        TextView add = simpleCard("＋\nIMPORTAR ROM\nGame Boy / Color", false);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cardW; lp.height = dp(172); lp.setMargins(0, 0, dp(10), dp(10));
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> pickRom());
        grid.addView(add);
    }

    private LinearLayout gameCard(GameBoyRomRepository.ImportedGame game, boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackgroundResource(active ? R.drawable.card_cyan_active : R.drawable.card_glass);
        card.setClickable(true);
        card.setFocusable(true);

        ImageView image = new ImageView(this);
        image.setBackgroundResource(R.drawable.card_blue);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTag(game.file.getAbsolutePath());
        image.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setPlaceholder(image);

        File cached = GameBoyCoverArtManager.cachedCover(this, game);
        if (cached != null) setCover(image, cached);
        else GameBoyCoverArtManager.request(this, game, (file, matched) -> {
            if (isFinishing() || isDestroyed()) return;
            Object tag = image.getTag();
            if (tag == null || !game.file.getAbsolutePath().equals(tag.toString())) return;
            if (file != null) setCover(image, file);
            refreshCoverStatus();
            if (selected != null && selected.file.equals(game.file)) refreshSelectedCoverOnly();
        });

        TextView label = new TextView(this);
        label.setText(game.title + "\n" + game.systemLabel);
        label.setTextColor(getColor(R.color.retro_text));
        label.setTextSize(8.5f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        label.setMaxLines(2);
        label.setPadding(dp(5), dp(4), dp(5), 0);
        label.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

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
        v.setClickable(true); v.setFocusable(true);
        return v;
    }

    private void refreshSelected() {
        boolean has = selected != null && selected.file.isFile();
        title.setText(has ? selected.title : "Selecciona una ROM .gb / .gbc");
        meta.setText(has ? selected.systemLabel + " · Gambatte · ARM64" : "Game Boy / Game Boy Color");
        state.setText(has ? "● LISTO PARA JUGAR" : "○ SIN JUEGO SELECCIONADO");
        findViewById(R.id.btnGbPlay).setEnabled(has);
        findViewById(R.id.btnGbPlay).setAlpha(has ? 1f : 0.42f);
        refreshSelectedCoverOnly();
    }

    private void refreshSelectedCoverOnly() {
        if (selectedCover == null) return;
        if (selected == null) { setPlaceholder(selectedCover); return; }
        selectedCover.setTag(selected.file.getAbsolutePath());
        File cached = GameBoyCoverArtManager.cachedCover(this, selected);
        if (cached != null) { setCover(selectedCover, cached); return; }
        setPlaceholder(selectedCover);
        GameBoyRomRepository.ImportedGame requested = selected;
        GameBoyCoverArtManager.request(this, requested, (file, matched) -> {
            if (isFinishing() || isDestroyed() || selected == null) return;
            if (!selected.file.equals(requested.file)) return;
            if (file != null) setCover(selectedCover, file);
            refreshCoverStatus();
        });
    }

    private void refreshCoverStatus() {
        if (coverStatus == null || games == null) return;
        int found = GameBoyCoverArtManager.cachedCount(this, games);
        int total = games.size();
        if (total == 0) coverStatus.setText("Carátulas GB/GBC · se buscan al importar");
        else if (found >= total) coverStatus.setText("✓ Carátulas " + found + "/" + total + " · caché local");
        else coverStatus.setText("Carátulas " + found + "/" + total + " · búsqueda automática activa");
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
        if (selected == null || !selected.file.isFile()) { pickRom(); return; }
        GameBoyRomRepository.select(this, selected);
        Intent i = new Intent(this, IntegratedGameActivity.class);
        i.putExtra(IntegratedGameActivity.EXTRA_CORE_ID, CoreRegistry.GAME_BOY.id);
        i.putExtra(IntegratedGameActivity.EXTRA_ROM_PATH, selected.file.getAbsolutePath());
        i.putExtra(IntegratedGameActivity.EXTRA_GAME_TITLE, selected.title);
        startActivity(i);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_ROM || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) {}
        try {
            selected = GameBoyRomRepository.importRom(this, uri);
            Toast.makeText(this, selected.systemLabel + " añadido · buscando carátula", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "ROM GB/GBC no válida: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
