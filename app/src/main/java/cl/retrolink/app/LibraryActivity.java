package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class LibraryActivity extends Activity {
    private TextView selectedTitle, selectedMeta, selectedStatus;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_library);
        InsetHelper.apply(findViewById(R.id.libraryRoot));

        selectedTitle = findViewById(R.id.txtLibraryTitle);
        selectedMeta = findViewById(R.id.txtLibraryMeta);
        selectedStatus = findViewById(R.id.txtLibraryStatus);

        findViewById(R.id.btnLibraryBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLibraryPlay).setOnClickListener(v -> openHost(false));
        findViewById(R.id.btnLibraryImport).setOnClickListener(v -> openHost(true));
        findViewById(R.id.btnLibraryHost).setOnClickListener(v -> openHost(false));
        findViewById(R.id.btnLibraryFavorite).setOnClickListener(v -> Toast.makeText(this, "Favoritos llegará en la siguiente expansión de biblioteca", Toast.LENGTH_SHORT).show());
        findViewById(R.id.navLibraryHome).setOnClickListener(v -> finish());
        findViewById(R.id.navLibraryRoom).setOnClickListener(v -> openHost(false));
        findViewById(R.id.navLibraryProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.navLibrarySettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        String summary = RetroPreferences.lastRomSummary(this);
        String path = RetroPreferences.lastRomPath(this);
        boolean hasRom = path != null && !path.isEmpty();
        selectedTitle.setText(hasRom ? shortTitle(summary) : "Importa tu primer juego N64");
        selectedMeta.setText(hasRom ? summary : "N64 · ROM externa · no se incluye contenido comercial");
        selectedStatus.setText(hasRom ? "● LISTO PARA JUGAR" : "○ SIN ROM SELECCIONADA");
    }

    private String shortTitle(String summary) {
        if (summary == null || summary.isEmpty()) return "Juego N64 seleccionado";
        int dot = summary.indexOf(" · ");
        return dot > 0 ? summary.substring(0, dot) : summary;
    }

    private void openHost(boolean picker) {
        Intent i = new Intent(this, HostActivity.class);
        if (picker) i.putExtra(HostActivity.EXTRA_OPEN_ROM_PICKER, true);
        startActivity(i);
    }
}
