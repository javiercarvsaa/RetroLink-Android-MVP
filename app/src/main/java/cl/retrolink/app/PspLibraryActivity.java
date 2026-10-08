package cl.retrolink.app;

import android.app.Activity;
import android.content.ClipData;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Biblioteca PSP con PPSSPP embebido y acceso a sala Ad Hoc. */
public class PspLibraryActivity extends Activity {
    private static final int REQ_GAME = 641;

    private GridLayout grid;
    private TextView count, title, meta, state, coverStatus, engineStatus;
    private ImageView selectedCover;
    private PspRomRepository.ImportedGame selected;
    private List<PspRomRepository.ImportedGame> games;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_psp_library);
        InsetHelper.apply(findViewById(R.id.pspLibraryRoot));

        grid = findViewById(R.id.pspRomGrid);
        count = findViewById(R.id.txtPspCount);
        title = findViewById(R.id.txtPspTitle);
        meta = findViewById(R.id.txtPspMeta);
        state = findViewById(R.id.txtPspState);
        selectedCover = findViewById(R.id.imgPspSelectedCover);
        coverStatus = findViewById(R.id.txtPspCoverStatus);
        engineStatus = findViewById(R.id.txtPspEngineStatus);

        findViewById(R.id.btnPspBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPspImport).setOnClickListener(v -> pickGame());
        findViewById(R.id.btnPspPlay).setOnClickListener(v -> play());
        findViewById(R.id.btnPspMultiplayer).setOnClickListener(v -> {
            if (selected != null && selected.file.isFile()) PspRomRepository.select(this, selected);
            startActivity(new Intent(this, PspRoomActivity.class));
        });
        findViewById(R.id.btnPspEngine).setOnClickListener(v -> openEngine());
        findViewById(R.id.btnPspRefreshCovers).setOnClickListener(v -> {
            PspCoverArtManager.invalidateIndex(this); refresh();
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        games = PspRomRepository.listGames(this);
        PspRomRepository.ImportedGame last = PspRomRepository.lastGame(this);
        selected = null;
        if (last != null)
            for (PspRomRepository.ImportedGame g : games)
                if (last.file.equals(g.file)) { selected = g; break; }
        if (selected == null && !games.isEmpty()) selected = games.get(0);
        count.setText(games.size() + (games.size() == 1 ? " juego" : " juegos"));
        engineStatus.setText(PspLauncher.engineAvailable()
                ? "● " + PspIniManager.status(this)
                : "○ Motor PPSSPP no disponible");
        refreshSelected();
        refreshCoverStatus();
        grid.post(this::rebuildGrid);
    }

    private void rebuildGrid() {
        grid.removeAllViews();
        int available = grid.getWidth() > 0 ? grid.getWidth() : dp(620);
        int cardW = Math.max(dp(190), (available - dp(10)) / 2);
        if (games.isEmpty()) {
            TextView empty = simpleCard("＋\nIMPORTAR PRIMER JUEGO\nISO · CSO · CHD · PBP", true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = available; lp.height = dp(158); empty.setLayoutParams(lp);
            empty.setOnClickListener(v -> pickGame()); grid.addView(empty); return;
        }
        for (PspRomRepository.ImportedGame g : games) {
            boolean active = selected != null && selected.file.equals(g.file);
            LinearLayout card = gameCard(g, active);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardW; lp.height = dp(184); lp.setMargins(0, 0, dp(10), dp(10));
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> { selected = g; PspRomRepository.select(this, g); refresh(); });
            grid.addView(card);
        }
        TextView add = simpleCard("＋\nIMPORTAR JUEGO\nPlayStation Portable", false);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cardW; lp.height = dp(184); lp.setMargins(0, 0, dp(10), dp(10));
        add.setLayoutParams(lp); add.setOnClickListener(v -> pickGame()); grid.addView(add);
    }

    private LinearLayout gameCard(PspRomRepository.ImportedGame g, boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackgroundResource(active ? R.drawable.card_cyan_active : R.drawable.card_glass);
        card.setClickable(true); card.setFocusable(true);

        ImageView image = new ImageView(this);
        image.setBackgroundResource(R.drawable.card_blue);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTag(g.file.getAbsolutePath());
        image.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setPlaceholder(image);
        File cached = PspCoverArtManager.cachedCover(this, g);
        if (cached != null) setCover(image, cached);
        else PspCoverArtManager.request(this, g, (file, matched) -> {
            if (isFinishing() || isDestroyed()) return;
            Object tag = image.getTag();
            if (tag == null || !g.file.getAbsolutePath().equals(tag.toString())) return;
            if (file != null) setCover(image, file);
            refreshCoverStatus();
            if (selected != null && selected.file.equals(g.file)) refreshSelectedCoverOnly();
        });

        TextView label = new TextView(this);
        label.setText(g.title + "\nPSP · " + extension(g.file));
        label.setTextColor(getColor(R.color.retro_text));
        label.setTextSize(8.5f); label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); label.setMaxLines(2);
        label.setPadding(dp(5), dp(4), dp(5), 0);
        label.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        card.addView(image); card.addView(label); return card;
    }

    private TextView simpleCard(String text, boolean active) {
        TextView v = new TextView(this);
        v.setText(text); v.setGravity(Gravity.CENTER); v.setTextColor(getColor(R.color.retro_text));
        v.setTextSize(10f); v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackgroundResource(active ? R.drawable.card_cyan_active : R.drawable.card_glass);
        v.setClickable(true); v.setFocusable(true); return v;
    }

    private void refreshSelected() {
        boolean has = selected != null && selected.file.isFile();
        title.setText(has ? selected.title : "Selecciona un juego de PSP");
        meta.setText(has ? "PlayStation Portable · PPSSPP · " + extension(selected.file)
                : "ISO · CSO · CHD · PBP · motor PPSSPP integrado");
        state.setText(has ? "● LISTO PARA JUGAR" : "○ SIN JUEGO SELECCIONADO");
        findViewById(R.id.btnPspPlay).setEnabled(has && PspLauncher.engineAvailable());
        findViewById(R.id.btnPspPlay).setAlpha(has && PspLauncher.engineAvailable() ? 1f : 0.42f);
        findViewById(R.id.btnPspMultiplayer).setEnabled(true);
        refreshSelectedCoverOnly();
    }

    private void refreshSelectedCoverOnly() {
        if (selectedCover == null) return;
        if (selected == null) { setPlaceholder(selectedCover); return; }
        selectedCover.setTag(selected.file.getAbsolutePath());
        File cached = PspCoverArtManager.cachedCover(this, selected);
        if (cached != null) { setCover(selectedCover, cached); return; }
        setPlaceholder(selectedCover);
        PspRomRepository.ImportedGame requested = selected;
        PspCoverArtManager.request(this, requested, (file, matched) -> {
            if (isFinishing() || isDestroyed() || selected == null) return;
            if (!selected.file.equals(requested.file)) return;
            if (file != null) setCover(selectedCover, file);
            refreshCoverStatus();
        });
    }

    private void refreshCoverStatus() {
        if (coverStatus == null || games == null) return;
        int found = PspCoverArtManager.cachedCount(this, games), total = games.size();
        if (total == 0) coverStatus.setText("Carátulas PSP · se buscan al importar");
        else if (found >= total) coverStatus.setText("✓ Carátulas " + found + "/" + total + " · caché local");
        else coverStatus.setText("Carátulas " + found + "/" + total + " · búsqueda automática activa");
    }

    private void pickGame() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); startActivityForResult(i, REQ_GAME);
    }

    private void play() {
        if (selected == null || !selected.file.isFile()) { pickGame(); return; }
        try {
            PspRomRepository.select(this, selected);
            PspLauncher.launchGame(this, selected.file, PspIniManager.Mode.SINGLE, null);
        } catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void openEngine() {
        try { PspLauncher.launchMenu(this); }
        catch (Exception e) { Toast.makeText(this, "No se pudo abrir PPSSPP: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_GAME || result != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++)
                if (clip.getItemAt(i).getUri() != null) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());

        int imported = 0;
        for (Uri uri : uris) {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) {}
            try { selected = PspRomRepository.importUri(this, uri); imported++; }
            catch (Exception e) {
                Toast.makeText(this, "No se pudo importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        if (imported > 0) Toast.makeText(this, imported + (imported == 1 ? " juego PSP añadido" : " juegos PSP añadidos"), Toast.LENGTH_LONG).show();
        refresh();
    }

    private void setPlaceholder(ImageView image) {
        image.setImageResource(R.mipmap.ic_launcher); image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(18), dp(12), dp(18), dp(12)); image.setAlpha(0.30f);
    }
    private void setCover(ImageView image, File file) {
        if (image == null || file == null || !file.isFile()) return;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return;
        image.setPadding(0, 0, 0, 0); image.setAlpha(1f);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setImageBitmap(bitmap);
    }
    private static String extension(File f) {
        if (f == null) return "PSP";
        String n = f.getName(); int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1).toUpperCase(Locale.US) : "PSP";
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
