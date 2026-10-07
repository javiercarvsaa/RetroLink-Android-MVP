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

/** Biblioteca PlayStation con CHD/PBP y CUE+BIN, BIOS, carátulas y sala 2P. */
public class Ps1LibraryActivity extends Activity {
    private static final int REQ_GAME = 591;
    private static final int REQ_BIOS = 592;

    private GridLayout grid;
    private TextView count, title, meta, state, coverStatus, biosStatus;
    private ImageView selectedCover;
    private Ps1RomRepository.ImportedGame selected;
    private List<Ps1RomRepository.ImportedGame> games;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_ps1_library);
        InsetHelper.apply(findViewById(R.id.ps1LibraryRoot));

        grid = findViewById(R.id.ps1RomGrid);
        count = findViewById(R.id.txtPs1Count);
        title = findViewById(R.id.txtPs1Title);
        meta = findViewById(R.id.txtPs1Meta);
        state = findViewById(R.id.txtPs1State);
        selectedCover = findViewById(R.id.imgPs1SelectedCover);
        coverStatus = findViewById(R.id.txtPs1CoverStatus);
        biosStatus = findViewById(R.id.txtPs1BiosStatus);

        findViewById(R.id.btnPs1Back).setOnClickListener(v -> finish());
        findViewById(R.id.btnPs1Import).setOnClickListener(v -> pickGame());
        findViewById(R.id.btnPs1ImportBios).setOnClickListener(v -> pickBios());
        findViewById(R.id.btnPs1Play).setOnClickListener(v -> play());
        findViewById(R.id.btnPs1Multiplayer).setOnClickListener(v -> {
            if (selected != null && selected.file.isFile())
                Ps1RomRepository.select(this, selected);
            startActivity(new Intent(this, Ps1RoomActivity.class));
        });
        findViewById(R.id.btnPs1RefreshCovers).setOnClickListener(v -> {
            Ps1CoverArtManager.invalidateIndex(this);
            refresh();
        });

        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        games = Ps1RomRepository.listGames(this);
        Ps1RomRepository.ImportedGame last = Ps1RomRepository.lastGame(this);
        selected = null;

        if (last != null) {
            for (Ps1RomRepository.ImportedGame g : games)
                if (last.file.equals(g.file)) {
                    selected = g;
                    break;
                }
        }
        if (selected == null && !games.isEmpty()) selected = games.get(0);

        count.setText(games.size() + (games.size() == 1 ? " juego" : " juegos"));
        biosStatus.setText(Ps1BiosManager.status(this));
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
                    "＋\nIMPORTAR PRIMER JUEGO\nCHD recomendado · PBP · CUE+BIN", true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = available;
            lp.height = dp(158);
            empty.setLayoutParams(lp);
            empty.setOnClickListener(v -> pickGame());
            grid.addView(empty);
            return;
        }

        for (Ps1RomRepository.ImportedGame g : games) {
            boolean active = selected != null && selected.file.equals(g.file);
            LinearLayout card = gameCard(g, active);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardW;
            lp.height = dp(184);
            lp.setMargins(0, 0, dp(10), dp(10));
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> {
                selected = g;
                Ps1RomRepository.select(this, g);
                refresh();
            });
            grid.addView(card);
        }

        TextView add = simpleCard("＋\nIMPORTAR JUEGO\nPlayStation", false);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cardW;
        lp.height = dp(184);
        lp.setMargins(0, 0, dp(10), dp(10));
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> pickGame());
        grid.addView(add);
    }

    private LinearLayout gameCard(Ps1RomRepository.ImportedGame g, boolean active) {
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

        File cached = Ps1CoverArtManager.cachedCover(this, g);
        if (cached != null) setCover(image, cached);
        else Ps1CoverArtManager.request(this, g, (file, matched) -> {
            if (isFinishing() || isDestroyed()) return;
            Object tag = image.getTag();
            if (tag == null || !g.file.getAbsolutePath().equals(tag.toString())) return;
            if (file != null) setCover(image, file);
            refreshCoverStatus();
            if (selected != null && selected.file.equals(g.file)) refreshSelectedCoverOnly();
        });

        TextView label = new TextView(this);
        label.setText(g.title + "\nPlayStation · " + extension(g.file));
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

        title.setText(has ? selected.title : "Selecciona un juego de PlayStation");
        meta.setText(has
                ? "PlayStation · PCSX-ReARMed · " + extension(selected.file)
                : "CHD recomendado · PBP · CUE+BIN");
        state.setText(has ? "● LISTO PARA JUGAR" : "○ SIN JUEGO SELECCIONADO");

        findViewById(R.id.btnPs1Play).setEnabled(has);
        findViewById(R.id.btnPs1Play).setAlpha(has ? 1f : 0.42f);

        // P2 puede entrar sin copia local del juego.
        findViewById(R.id.btnPs1Multiplayer).setEnabled(true);
        findViewById(R.id.btnPs1Multiplayer).setAlpha(1f);

        refreshSelectedCoverOnly();
    }

    private void refreshSelectedCoverOnly() {
        if (selectedCover == null) return;
        if (selected == null) {
            setPlaceholder(selectedCover);
            return;
        }

        selectedCover.setTag(selected.file.getAbsolutePath());
        File cached = Ps1CoverArtManager.cachedCover(this, selected);
        if (cached != null) {
            setCover(selectedCover, cached);
            return;
        }

        setPlaceholder(selectedCover);
        Ps1RomRepository.ImportedGame requested = selected;
        Ps1CoverArtManager.request(this, requested, (file, matched) -> {
            if (isFinishing() || isDestroyed() || selected == null) return;
            if (!selected.file.equals(requested.file)) return;
            if (file != null) setCover(selectedCover, file);
            refreshCoverStatus();
        });
    }

    private void refreshCoverStatus() {
        if (coverStatus == null || games == null) return;
        int found = Ps1CoverArtManager.cachedCount(this, games);
        int total = games.size();
        if (total == 0)
            coverStatus.setText("Carátulas PS1 · se buscan al importar");
        else if (found >= total)
            coverStatus.setText("✓ Carátulas " + found + "/" + total + " · caché local");
        else
            coverStatus.setText("Carátulas " + found + "/" + total + " · búsqueda automática activa");
    }

    private void pickGame() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_GAME);
    }

    private void pickBios() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_BIOS);
    }

    private void play() {
        if (selected == null || !selected.file.isFile()) {
            pickGame();
            return;
        }
        Ps1RomRepository.select(this, selected);
        Intent i = new Intent(this, Ps1GameActivity.class);
        i.putExtra(Ps1GameActivity.EXTRA_ROM_PATH, selected.file.getAbsolutePath());
        i.putExtra(Ps1GameActivity.EXTRA_GAME_TITLE, selected.title);
        i.putExtra(Ps1GameActivity.EXTRA_HOST_SESSION, false);
        startActivity(i);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null) return;

        if (req == REQ_BIOS) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            try {
                File bios = Ps1BiosManager.importBios(this, uri);
                Toast.makeText(this, "BIOS PS1 importada: " + bios.getName(),
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "BIOS no válida: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            refresh();
            return;
        }

        if (req != REQ_GAME) return;

        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++)
                if (clip.getItemAt(i).getUri() != null)
                    uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
        }

        try {
            selected = Ps1RomRepository.importUris(this, uris);
            Toast.makeText(this,
                    "Juego PS1 añadido · " + extension(selected.file),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "No se pudo importar: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
        refresh();
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

    private static String extension(File f) {
        if (f == null) return "PS1";
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1).toUpperCase(java.util.Locale.US) : "PS1";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
