package cl.retrolink.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Native horizontal hub. No emulator, save, controller or transport implementation is replaced. */
public final class RetroHubActivity extends Activity {
    private static final int BG = 0xff090c12, PANEL = 0xff121821, LINE = 0xff2e3747;
    private static final int TEXT = 0xfff3f5fa, MUTED = 0xffb7c1d0, ACCENT = 0xffff4659;
    private static final int REQUEST_BT = 1601, ENABLE_BT = 1602;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private Future<?> scanning;
    private HubCoverLoader covers;
    private HubStore store;
    private Set<String> favoriteKeys = java.util.Collections.emptySet();
    private boolean compactHeight;
    private HubCatalog.Snapshot snapshot = new HubCatalog.Snapshot();
    private LinearLayout root, content;
    private FrameLayout page;
    private TextView heading, subheading, listCount, empty;
    private GridView grid;
    private EditText search;
    private GameAdapter gameAdapter;
    private final List<HubGame> displayed = new ArrayList<>();
    private final List<TextView> navButtons = new ArrayList<>();
    private int tab, listMode, loadGeneration, gridPosition, gridOffset, homeScroll;
    private String selectedCore = "", query = "";
    private boolean busy, loading, loaded, destroyed;
    private boolean pendingN64Controller;
    private ScrollView currentScroll;
    private Runnable unregisterBack;
    private final Runnable applySearch = this::filterGames;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new HubStore(this);
        covers = new HubCoverLoader();
        compactHeight = getResources().getConfiguration().screenHeightDp < 360
                || getResources().getConfiguration().fontScale > 1.3f;
        if (state != null) {
            tab = Math.max(0, Math.min(3, state.getInt("hub_tab", 0)));
            listMode = Math.max(0, Math.min(2, state.getInt("hub_mode", 0)));
            selectedCore = state.getString("hub_core", "");
            if (!selectedCore.isEmpty() && HubCatalog.platform(selectedCore) == null) selectedCore = "";
            query = state.getString("hub_query", "");
            gridPosition = state.getInt("hub_position", 0); gridOffset = state.getInt("hub_offset", 0);
            homeScroll = state.getInt("hub_scroll", 0);
            pendingN64Controller = state.getBoolean("hub_pending_controller", false);
        }
        buildShell();
        showPage(false);
        if (android.os.Build.VERSION.SDK_INT >= 33) unregisterBack = BackApi33.install(this, this::handleBack);
    }
    @Override protected void onResume() {
        super.onResume(); busy = false;
        if (root != null) refreshCatalogue();
    }
    @Override protected void onPause() {
        capturePosition(); main.removeCallbacks(applySearch); super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        capturePosition();
        state.putInt("hub_tab", tab); state.putInt("hub_mode", listMode);
        state.putString("hub_core", selectedCore); state.putString("hub_query", query);
        state.putInt("hub_position", gridPosition); state.putInt("hub_offset", gridOffset);
        state.putInt("hub_scroll", homeScroll);
        state.putBoolean("hub_pending_controller", pendingN64Controller);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        destroyed = true; loadGeneration++;
        if (scanning != null) scanning.cancel(true);
        scanner.shutdownNow(); main.removeCallbacksAndMessages(null);
        if (covers != null) covers.close();
        if (unregisterBack != null) unregisterBack.run();
        super.onDestroy();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW && covers != null) covers.trim();
    }
    @Override public void onBackPressed() { handleBack(); }
    private void handleBack() {
        if (tab != 0) navigate(0);
        else finish();
    }
    /** API-isolated back callback for Android 13+ and targetSdk 36. */
    private static final class BackApi33 {
        static Runnable install(Activity activity, Runnable action) {
            android.window.OnBackInvokedCallback callback = action::run;
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }

    private void buildShell() {
        root = row(); root.setBackgroundColor(BG);
        setContentView(root); InsetHelper.apply(root);
        ScrollView railScroll = new ScrollView(this);
        railScroll.setFillViewport(true); railScroll.setVerticalScrollBarEnabled(false);
        railScroll.setBackgroundColor(PANEL);
        int railWidth = getResources().getConfiguration().screenWidthDp >= 840 ? 144 : 116;
        root.addView(railScroll, new LinearLayout.LayoutParams(dp(railWidth), -1));
        LinearLayout rail = column(); rail.setPadding(dp(10), dp(12), dp(10), dp(12));
        railScroll.addView(rail);
        ImageView brand = new ImageView(this); brand.setImageResource(R.drawable.hub_brand);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER); brand.setContentDescription("RetroLink: mando retro y moderno enlazado");
        rail.addView(brand, new LinearLayout.LayoutParams(-1, dp(56)));
        TextView logo = text("RetroLink", 19, TEXT, true); logo.setGravity(Gravity.CENTER);
        rail.addView(logo, lp(-1, -2, 0, 0, 0, 12));
        String[] labels = {"Inicio", "Biblioteca", "Multijugador", "Ajustes"};
        for (int k = 0; k < labels.length; k++) {
            final int destination = k;
            TextView button = button(labels[k], () -> navigate(destination), false);
            button.setTextSize(13); button.setGravity(Gravity.CENTER);
            rail.addView(button, lp(-1, -2, 0, 0, 0, 8)); navButtons.add(button);
        }
        TextView version = text("v1.6 · RC1\nBiblioteca local", 11, MUTED, false);
        version.setGravity(Gravity.CENTER); rail.addView(version, lp(-1, -2, 0, 6, 0, 0));
        content = column(); content.setPadding(dp(16), dp(10), dp(16), dp(8));
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout bar = row(); bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column();
        heading = text("", compactHeight ? 20 : 24, TEXT, true); subheading = text("", 12, MUTED, false);
        if (compactHeight) subheading.setVisibility(View.GONE);
        titles.addView(heading); titles.addView(subheading);
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = button(compactHeight ? "+" : "+ Añadir", this::chooseImportPlatform, true);
        add.setContentDescription("Añadir juegos: elegir consola");
        bar.addView(add, lp(-2, -2, 8, 0, 0, 0));
        content.addView(bar, lp(-1, -2, 0, 0, 0, 10));
        page = new FrameLayout(this); content.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private void navigate(int destination) {
        if (tab == destination) return;
        capturePosition(); tab = destination;
        hideKeyboard(); showPage(false);
    }
    private void capturePosition() {
        if (grid != null && tab == 1) {
            gridPosition = grid.getFirstVisiblePosition();
            gridOffset = grid.getChildCount() == 0 ? 0 : grid.getChildAt(0).getTop();
        }
        if (currentScroll != null && tab == 0) homeScroll = currentScroll.getScrollY();
    }
    private void showPage(boolean resetGrid) {
        main.removeCallbacks(applySearch);
        page.removeAllViews(); grid = null; listCount = null; search = null; currentScroll = null;
        String[] titles = {"Todo tu juego. Un solo lugar.", "Tu biblioteca", "Juega acompañado", "Tu RetroLink"};
        heading.setText(compactHeight ? new String[]{"Inicio", "Biblioteca", "Multijugador", "Ajustes"}[tab] : titles[tab]);
        subheading.setText(loading ? "Actualizando biblioteca local…" : snapshot.games.size() + " juegos · " + HubCatalog.PLATFORMS.size() + " plataformas");
        for (int i = 0; i < navButtons.size(); i++) {
            TextView nav = navButtons.get(i); nav.setSelected(i == tab);
            nav.setTextColor(i == tab ? TEXT : MUTED);
            nav.setBackground(interactive(i == tab ? 0xff51212d : PANEL));
        }
        switch (tab) {
            case 0: showHome(); break;
            case 1: if (resetGrid) { gridPosition = 0; gridOffset = 0; } showLibrary(); break;
            case 2: showMultiplayer(); break;
            default: showSettings(); break;
        }
    }
    private LinearLayout scrollPage() {
        currentScroll = new ScrollView(this); currentScroll.setFillViewport(true);
        LinearLayout body = column(); currentScroll.addView(body);
        page.addView(currentScroll, new FrameLayout.LayoutParams(-1, -1));
        return body;
    }
    private void showHome() {
        LinearLayout body = scrollPage();
        LinearLayout hero = card();
        List<HubGame> recent = HubGame.filter(snapshot.games, "", "", false, true, store.favorites(), store.opened());
        hero.addView(text(recent.isEmpty() ? "Tu próxima partida empieza aquí" : "Volver a abrir", 18, TEXT, true));
        if (!recent.isEmpty()) {
            HubGame latest = recent.get(0);
            hero.addView(text(latest.title + " · " + latest.platform, 15, MUTED, false), lp(-1, -2, 0, 6, 0, 8));
            hero.addView(button("Jugar", () -> play(latest), true), lp(-2, -2, 0, 0, 0, 0));
            hero.addView(text("Abre el juego; carga tu partida desde el juego o su menú.", 11, MUTED, false), lp(-1, -2, 0, 6, 0, 0));
        } else {
            hero.addView(text("PSP, PlayStation, Nintendo 64, Super Nintendo, Atari 2600 y Game Boy / Color.", 14, MUTED, false), lp(-1, -2, 0, 6, 0, 8));
            hero.addView(button("Explorar biblioteca", () -> navigate(1), true), lp(-2, -2, 0, 0, 0, 0));
        }
        body.addView(hero, lp(-1, -2, 0, 0, 0, 12));
        body.addView(text("Plataformas", 17, TEXT, true), lp(-1, -2, 0, 0, 0, 8));
        HorizontalScrollView strip = new HorizontalScrollView(this); strip.setHorizontalScrollBarEnabled(true);
        LinearLayout platformRow = row(); strip.addView(platformRow);
        for (HubCatalog.Platform p : HubCatalog.PLATFORMS) {
            int count = count(p.core.id);
            TextView item = button(p.core.shortSystem + "\n" + count + (count == 1 ? " juego" : " juegos"), () -> {
                selectedCore = p.core.id; query = ""; listMode = 0; tab = 1; showPage(true);
            }, false);
            item.setTextSize(14); item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            platformRow.addView(item, lp(172, -2, 0, 0, 10, 8));
        }
        body.addView(strip, lp(-1, -2, 0, 0, 0, 12));
        body.addView(button("Multijugador · elegir plataforma", () -> navigate(2), false), lp(-1, -2, 0, 0, 0, 10));
        body.addView(button("Adaptive Core · " + OptimizationProfileStore.modeLabel(this),
                () -> open(OptimizationSettingsActivity.class), false), lp(-1, -2, 0, 0, 0, 8));
        if (!snapshot.errors.isEmpty()) body.addView(text("No se pudo leer: " + String.join(", ", snapshot.errors) + ". Reintenta o abre su biblioteca.", 13, ACCENT, false));
        body.addView(text("Aperturas recientes se registran desde este inicio. No se inventa un historial anterior.", 11, MUTED, false));
        currentScroll.post(() -> { if (currentScroll != null && tab == 0) currentScroll.scrollTo(0, homeScroll); });
    }
    private void showLibrary() {
        LinearLayout body = column(); page.addView(body, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout searchRow = row(); searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(15);
        search.setTextColor(TEXT); search.setHintTextColor(MUTED); search.setHint("Buscar por nombre o consola");
        search.setContentDescription("Buscar juegos en la biblioteca local");
        search.setMinHeight(dp(48)); search.setPadding(dp(12), dp(8), dp(12), dp(8));
        search.setBackground(shape(PANEL, LINE)); search.setText(query);
        search.setSelectAllOnFocus(false);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        TextView platform = button(selectedCore.isEmpty() ? "Todas" : HubCatalog.platform(selectedCore).core.shortSystem,
                this::chooseLibraryPlatform, false);
        platform.setTextSize(12); searchRow.addView(platform, lp(-2, -2, 8, 0, 0, 0));
        body.addView(searchRow, lp(-1, -2, 0, 0, 0, 8));
        HorizontalScrollView filters = new HorizontalScrollView(this); LinearLayout options = row(); filters.addView(options);
        String[] labels = {"Todos", "Favoritos", "Recientes"};
        for (int k = 0; k < labels.length; k++) {
            final int value = k;
            TextView chip = button(labels[k], () -> { query = search.getText().toString(); listMode = value; hideKeyboard(); showPage(true); }, k == listMode);
            chip.setTextSize(12); options.addView(chip, lp(-2, -2, 0, 0, 8, 0));
        }
        TextView manage = button("Gestionar / importar", this::chooseImportPlatform, false); manage.setTextSize(12);
        options.addView(manage, lp(-2, -2, 0, 0, 0, 0));
        body.addView(filters, lp(-1, -2, 0, 0, 0, 4));
        listCount = text("", 12, MUTED, false); if (compactHeight) listCount.setVisibility(View.GONE); body.addView(listCount, lp(-1, -2, 0, 0, 0, 6));
        FrameLayout gridArea = new FrameLayout(this); body.addView(gridArea, new LinearLayout.LayoutParams(-1, 0, 1));
        grid = new GridView(this); grid.setNumColumns(GridView.AUTO_FIT); grid.setColumnWidth(dp(220));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); grid.setHorizontalSpacing(dp(10)); grid.setVerticalSpacing(dp(10));
        grid.setClipToPadding(false); grid.setPadding(0, 0, 0, dp(8)); grid.setSelector(android.R.color.transparent);
        gridArea.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        empty = text("", 15, MUTED, false); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20), dp(20), dp(20), dp(20));
        gridArea.addView(empty, new FrameLayout.LayoutParams(-1, -1)); grid.setEmptyView(empty);
        gameAdapter = new GameAdapter(); grid.setAdapter(gameAdapter); filterGames();
        grid.setSelectionFromTop(Math.max(0, Math.min(gridPosition, Math.max(0, displayed.size() - 1))), gridOffset);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString(); gridPosition = 0; gridOffset = 0;
                main.removeCallbacks(applySearch); main.postDelayed(applySearch, 160);
            }
            public void afterTextChanged(Editable e) {}
        });
        body.setFocusableInTouchMode(true); body.requestFocus();
    }
    private void filterGames() {
        if (tab != 1 || gameAdapter == null || destroyed) return;
        favoriteKeys = store.favorites();
        displayed.clear(); displayed.addAll(HubGame.filter(snapshot.games, selectedCore, query,
                listMode == 1, listMode == 2, favoriteKeys, store.opened()));
        listCount.setText(displayed.size() + " resultados · carátulas locales" + (snapshot.errors.isEmpty() ? "" : " · algunas bibliotecas no pudieron leerse"));
        empty.setText(loading && !loaded ? "Leyendo tus bibliotecas…" :
                listMode == 2 ? "Todavía no hay aperturas recientes con este filtro.\nJuega desde este inicio para registrarlas." :
                listMode == 1 ? "No hay favoritos con este filtro.\nMarca la estrella de un juego." :
                "No hay juegos con este filtro.\nPrueba otra búsqueda o pulsa + Añadir.");
        gameAdapter.notifyDataSetChanged();
    }
    private final class GameAdapter extends BaseAdapter {
        public int getCount() { return displayed.size(); }
        public HubGame getItem(int position) { return displayed.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View convertView, ViewGroup parent) {
            GameCard holder;
            if (convertView == null) {
                holder = new GameCard(); LinearLayout card = card(); holder.card = card;
                holder.cover = new ImageView(RetroHubActivity.this); holder.cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                card.addView(holder.cover, new LinearLayout.LayoutParams(-1, dp(66)));
                holder.title = text("", 15, TEXT, true); holder.title.setMinLines(2); holder.title.setMaxLines(2);
                holder.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(holder.title, lp(-1, -2, 0, 6, 0, 2));
                LinearLayout bottom = row(); bottom.setGravity(Gravity.CENTER_VERTICAL);
                holder.platform = text("", 11, MUTED, false); bottom.addView(holder.platform, new LinearLayout.LayoutParams(0, -2, 1));
                holder.favorite = button("☆", () -> {}, false); holder.favorite.setTextSize(23);
                bottom.addView(holder.favorite, new LinearLayout.LayoutParams(dp(48), dp(48))); card.addView(bottom);
                card.setFocusable(true); card.setClickable(true); card.setBackground(interactive(PANEL));
                card.setTag(holder); convertView = card;
            } else holder = (GameCard) convertView.getTag();
            HubGame game = getItem(position);
            holder.title.setText(game.title); holder.platform.setText(game.platform + "\nTocar para jugar");
            holder.favorite.setText(favoriteKeys.contains(game.key) ? "★" : "☆"); holder.favorite.setTextColor(ACCENT);
            holder.favorite.setContentDescription((favoriteKeys.contains(game.key) ? "Quitar de favoritos: " : "Añadir a favoritos: ") + game.title);
            holder.favorite.setOnClickListener(v -> {
                store.toggleFavorite(game.key); capturePosition(); filterGames();
            });
            holder.card.setContentDescription("Jugar " + game.title + ", " + game.platform);
            holder.card.setOnClickListener(v -> play(game));
            holder.card.setOnLongClickListener(v -> {
                HubCatalog.Platform platform = HubCatalog.platform(game.coreId);
                if (platform != null) startActivity(HubCatalog.libraryIntent(RetroHubActivity.this, platform));
                return true;
            });
            covers.load(holder.cover, game.cover, game.key); return convertView;
        }
    }
    private static final class GameCard { LinearLayout card; ImageView cover; TextView title, platform, favorite; }

    private void showMultiplayer() {
        LinearLayout body = scrollPage();
        body.addView(text("Elige la consola. Cada sala conserva su conexión y sus controles actuales.", 14, MUTED, false), lp(-1, -2, 0, 0, 0, 12));
        for (HubCatalog.Platform p : HubCatalog.PLATFORMS) {
            LinearLayout card = card(); card.addView(text(p.label, 18, TEXT, true));
            card.addView(text(p.connection, 14, ACCENT, true), lp(-1, -2, 0, 4, 0, 6));
            String description = p.core == CoreRegistry.PSP
                    ? "Cada teléfono ejecuta PPSSPP y su copia compatible del juego. La sala Ad Hoc usa la red Wi-Fi; no es un segundo mando del anfitrión."
                    : p.core == CoreRegistry.GAME_BOY
                    ? "Se conserva la sala Game Boy Link y su configuración existente. No equivale a conectar un mando P2 a una sola consola."
                    : "El anfitrión ejecuta el juego. El otro teléfono usa la ruta de mando remoto y la imagen compartida; no necesita la misma potencia gráfica.";
            card.addView(text(description, 13, MUTED, false), lp(-1, -2, 0, 0, 0, 10));
            if (p.core == CoreRegistry.N64) {
                card.addView(button("Crear sala N64", () -> openN64Room(false), true), lp(-1, -2, 0, 0, 0, 8));
                card.addView(button("Usar este teléfono como mando", () -> openN64Room(true), false));
            } else card.addView(button("Abrir sala · crear o unirse", () -> startActivity(HubCatalog.roomIntent(this, p)), false));
            body.addView(card, lp(-1, -2, 0, 0, 0, 12));
        }
        body.addView(button("Probar controles", () -> open(ControlTestActivity.class), false));
    }
    private void showSettings() {
        LinearLayout body = scrollPage();
        addSetting(body, "Adaptive Core", "Perfiles y modo de optimización existentes. Motor basado en reglas; este avance no añade IA neuronal.", OptimizationSettingsActivity.class);
        addSetting(body, "Configuración completa", "Audio, gráficos y ajustes actuales de RetroLink.", SettingsActivity.class);
        addSetting(body, "Controles", "Prueba la respuesta antes de iniciar una partida.", ControlTestActivity.class);
        addSetting(body, "Perfil", "Acceso al perfil existente.", ProfileActivity.class);
        addSetting(body, "Abrir inicio clásico", "Volver a la pantalla anterior en esta sesión, sin borrar datos. Al volver a abrir la app se muestra el nuevo inicio.", MainActivity.class);
        LinearLayout summary = card(); summary.addView(text("Núcleos de esta instalación", 17, TEXT, true));
        for (HubCatalog.Platform p : HubCatalog.PLATFORMS)
            summary.addView(text(p.core.shortSystem + " · " + p.engine + " · " + (HubCatalog.enginePackaged(this, p) ? "incluido" : "no encontrado"), 12, MUTED, false), lp(-1, -2, 0, 8, 0, 0));
        body.addView(summary, lp(-1, -2, 0, 0, 0, 12));
        body.addView(text("El inicio no descarga carátulas ni sube datos. Las bibliotecas clásicas conservan sus servicios previos. Ningún resultado de rendimiento se presupone.", 12, MUTED, false));
    }
    private void addSetting(LinearLayout body, String title, String description, Class<? extends Activity> target) {
        LinearLayout card = card(); card.addView(text(description, 13, MUTED, false), lp(-1, -2, 0, 0, 0, 8));
        card.addView(button(title, () -> open(target), false)); body.addView(card, lp(-1, -2, 0, 0, 0, 10));
    }
    private void chooseLibraryPlatform() {
        String[] labels = new String[HubCatalog.PLATFORMS.size() + 1]; labels[0] = "Todas las plataformas";
        for (int k = 0; k < HubCatalog.PLATFORMS.size(); k++) labels[k + 1] = HubCatalog.PLATFORMS.get(k).label;
        new AlertDialog.Builder(this).setTitle("Filtrar biblioteca").setItems(labels, (d, which) -> {
            selectedCore = which == 0 ? "" : HubCatalog.PLATFORMS.get(which - 1).core.id;
            hideKeyboard(); showPage(true);
        }).setNegativeButton("Cancelar", null).show();
    }
    private void chooseImportPlatform() {
        String[] labels = new String[HubCatalog.PLATFORMS.size()];
        for (int k = 0; k < labels.length; k++) labels[k] = HubCatalog.PLATFORMS.get(k).label;
        new AlertDialog.Builder(this).setTitle("Añadir / gestionar juegos de…").setItems(labels, (d, which) -> {
            HubCatalog.Platform p = HubCatalog.PLATFORMS.get(which);
            startActivity(HubCatalog.libraryIntent(this, p));
            Toast.makeText(this, "Usa Importar en la biblioteca de " + p.label + ".", Toast.LENGTH_LONG).show();
        }).setNegativeButton("Cancelar", null).show();
    }
    private void refreshCatalogue() {
        if (loading || destroyed) return;
        loading = true; int generation = ++loadGeneration;
        subheading.setText("Actualizando biblioteca local…");
        Context app = getApplicationContext();
        scanning = scanner.submit(() -> {
            HubCatalog.Snapshot result = HubCatalog.load(app);
            main.post(() -> {
                if (destroyed || generation != loadGeneration) return;
                loading = false; loaded = true; capturePosition(); snapshot = result;
                subheading.setText(snapshot.games.size() + " juegos · " + HubCatalog.PLATFORMS.size() + " plataformas");
                if (tab == 1 && grid != null) {
                    filterGames(); grid.setSelectionFromTop(Math.max(0, Math.min(gridPosition, Math.max(0, displayed.size() - 1))), gridOffset);
                } else if (tab == 0) showPage(false);
            });
        });
    }
    private void play(HubGame game) {
        if (busy) return; busy = true; hideKeyboard();
        try { HubCatalog.launchSolo(this, game); store.recordOpen(game.key); }
        catch (Exception e) {
            busy = false;
            new AlertDialog.Builder(this).setTitle("No se pudo abrir el juego")
                    .setMessage("Revisa que el archivo y el núcleo estén disponibles en la biblioteca de " + game.platform + ". Tus guardados no se han eliminado.")
                    .setPositiveButton("Abrir biblioteca", (d, w) -> {
                        HubCatalog.Platform p = HubCatalog.platform(game.coreId);
                        if (p != null) startActivity(HubCatalog.libraryIntent(this, p));
                    }).setNegativeButton("Volver", null).show();
            refreshCatalogue();
        }
    }
    private void open(Class<? extends Activity> target) { startActivity(new Intent(this, target)); }
    private void openN64Room(boolean controller) {
        pendingN64Controller = controller;
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) { Toast.makeText(this, "Bluetooth no disponible en este teléfono.", Toast.LENGTH_LONG).show(); return; }
        if (!BluetoothPermissionHelper.hasAll(this)) {
            requestPermissions(BluetoothPermissionHelper.requiredPermissions(), REQUEST_BT); return;
        }
        try {
            if (!adapter.isEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BT); return;
            }
            open(controller ? ControllerActivity.class : HostActivity.class);
        } catch (SecurityException e) {
            Toast.makeText(this, "Autoriza Dispositivos cercanos para esta sala.", Toast.LENGTH_LONG).show();
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQUEST_BT && BluetoothPermissionHelper.hasAll(this)) openN64Room(pendingN64Controller);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ENABLE_BT && result == RESULT_OK) openN64Room(pendingN64Controller);
    }
    private int count(String coreId) { int count = 0; for (HubGame g : snapshot.games) if (g.coreId.equals(coreId)) count++; return count; }
    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && root != null) keyboard.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout card() {
        LinearLayout v = column(); v.setPadding(dp(14), dp(12), dp(14), dp(12)); v.setBackground(shape(PANEL, LINE)); return v;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setIncludeFontPadding(true); return v;
    }
    private TextView button(String value, Runnable action, boolean primary) {
        TextView v = text(value, 14, TEXT, true); v.setMinHeight(dp(48)); v.setMinWidth(dp(48));
        v.setPadding(dp(12), dp(10), dp(12), dp(10)); v.setGravity(Gravity.CENTER);
        v.setBackground(interactive(primary ? 0xff882639 : PANEL));
        v.setClickable(true); v.setFocusable(true); v.setOnClickListener(view -> action.run()); return v;
    }
    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(14)); d.setStroke(dp(1), stroke); return d;
    }
    private RippleDrawable interactive(int color) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(color, ACCENT));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(0xff51212d, ACCENT));
        states.addState(new int[]{}, shape(color, LINE));
        return new RippleDrawable(ColorStateList.valueOf(0x44ff4659), states, shape(0xffffffff, 0xffffffff));
    }
    private LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
