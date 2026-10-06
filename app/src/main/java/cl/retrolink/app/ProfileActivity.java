package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.widget.TextView;

public class ProfileActivity extends Activity {
    private TextView last, capabilities, gamepad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_profile);
        InsetHelper.apply(findViewById(R.id.profileRoot));

        TextView device = findViewById(R.id.txtProfileDevice);
        last = findViewById(R.id.txtProfileLastGame);
        TextView version = findViewById(R.id.txtProfileVersion);
        capabilities = findViewById(R.id.txtProfileCapabilities);
        gamepad = findViewById(R.id.txtProfileGamepad);

        device.setText(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        version.setText("RetroLink v0.6.2 RC1");

        findViewById(R.id.navProfileHome).setOnClickListener(v -> finish());
        findViewById(R.id.navProfileGames).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.navProfileRoom).setOnClickListener(v -> startActivity(new Intent(this, HostActivity.class)));
        findViewById(R.id.navProfileSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnProfileControls).setOnClickListener(v -> startActivity(new Intent(this, ControlLayoutActivity.class)));
        findViewById(R.id.btnProfileTestControls).setOnClickListener(v -> startActivity(new Intent(this, ControlTestActivity.class)));
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        String summary = RetroPreferences.lastRomSummary(this);
        last.setText(summary == null || summary.isEmpty() ? "Sin partida reciente" : summary);
        gamepad.setText(AndroidGamepadMapper.connectedGamepadsSummary());
        capabilities.setText("✓ Nintendo 64 integrado\n✓ Controles táctiles configurables\n✓ BLE + Wi‑Fi local\n✓ PresentSync / RetroSR");
    }
}
