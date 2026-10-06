package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import java.io.File;

/** Perfil local: resume únicamente información que RetroLink puede comprobar. */
public class ProfileActivity extends Activity {
    private TextView last, gamepad, libraryCount, coreState, device;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_profile);
        InsetHelper.apply(findViewById(R.id.profileRoot));

        device = findViewById(R.id.txtProfileDevice);
        last = findViewById(R.id.txtProfileLastGame);
        gamepad = findViewById(R.id.txtProfileGamepad);
        libraryCount = findViewById(R.id.txtProfileLibraryCount);
        coreState = findViewById(R.id.txtProfileCoreState);
        ((TextView) findViewById(R.id.txtProfileVersion)).setText("RetroLink v0.6.3 RC1");

        findViewById(R.id.navProfileHome).setOnClickListener(v -> finish());
        findViewById(R.id.navProfileGames).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.navProfileRoom).setOnClickListener(v -> startActivity(new Intent(this, HostActivity.class)));
        findViewById(R.id.navProfileSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnProfileControls).setOnClickListener(v -> startActivity(new Intent(this, ControlLayoutActivity.class)));
        findViewById(R.id.btnProfileTestControls).setOnClickListener(v -> startActivity(new Intent(this, ControlTestActivity.class)));
        findViewById(R.id.btnProfileLibrary).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        device.setText(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        String summary = RetroPreferences.lastRomSummary(this);
        last.setText(summary == null || summary.isEmpty() ? "Sin partida reciente" : summary);
        gamepad.setText(AndroidGamepadMapper.connectedGamepadsSummary());
        int count = N64RomRepository.listRoms(this).size();
        libraryCount.setText(count + (count == 1 ? " juego N64" : " juegos N64"));
        File core = new File(getApplicationInfo().nativeLibraryDir, CoreRegistry.N64.libraryFile);
        coreState.setText(core.isFile() ? "● Mupen64Plus-Next instalado" : "○ Core N64 no encontrado");
    }
}
