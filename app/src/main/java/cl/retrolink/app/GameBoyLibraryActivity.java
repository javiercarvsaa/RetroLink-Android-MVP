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

import java.util.List;

/** Biblioteca funcional Game Boy / Game Boy Color. */
public class GameBoyLibraryActivity extends Activity {
    private static final int REQ_ROM = 271;
    private GridLayout grid;
    private TextView count, title, meta, state;
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

        findViewById(R.id.btnGbBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnGbImport).setOnClickListener(v -> pickRom());
        findViewById(R.id.btnGbPlay).setOnClickListener(v -> play());
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        games = GameBoyRomRepository.listGames(this);
        GameBoyRomRepository.ImportedGame last = GameBoyRomRepository.lastGame(this);
        selected = null;
        if (last != null) {
            for (GameBoyRomRepository.ImportedGame g : games) {
                if (last.file.equals(g.file)) { selected = g; break; }
            }
        }
        if (selected == null && !games.isEmpty()) selected = games.get(0);
        count.setText(games.size() + (games.size() == 1 ? " juego" : " juegos"));
        refreshSelected();
        grid.post(this::rebuildGrid);
    }

    private void rebuildGrid() {
        grid.removeAllViews();
        int available = grid.getWidth() > 0 ? grid.getWidth() : dp(620);
        int cardW = Math.max(dp(180), (available - dp(10)) / 2);
        if (games.isEmpty()) {
            TextView empty = card("＋\nIMPORTAR PRIMER JUEGO\n.gb / .gbc", true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = available; lp.height = dp(150);
            empty.setLayoutParams(lp);
            empty.setOnClickListener(v -> pickRom());
            grid.addView(empty);
            return;
        }
        for (GameBoyRomRepository.ImportedGame game : games) {
            boolean active = selected != null && selected.file.equals(game.file);
            TextView v = card("▣\n" + game.title + "\n" + game.systemLabel, active);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardW; lp.height = dp(150); lp.setMargins(0, 0, dp(10), dp(10));
            v.setLayoutParams(lp);
            v.setOnClickListener(x -> {
                selected = game;
                GameBoyRomRepository.select(this, game);
                refresh();
            });
            grid.addView(v);
        }
        TextView add = card("＋\nIMPORTAR ROM\nGame Boy / Color", false);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cardW; lp.height = dp(150); lp.setMargins(0, 0, dp(10), dp(10));
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> pickRom());
        grid.addView(add);
    }

    private TextView card(String text, boolean active) {
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
            Toast.makeText(this, selected.systemLabel + " añadido a RetroLink", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "ROM GB/GBC no válida: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
