package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.widget.TextView;

public class ProfileActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_profile);
        InsetHelper.apply(findViewById(R.id.profileRoot));

        TextView device = findViewById(R.id.txtProfileDevice);
        TextView last = findViewById(R.id.txtProfileLastGame);
        TextView version = findViewById(R.id.txtProfileVersion);

        device.setText(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        String summary = RetroPreferences.lastRomSummary(this);
        last.setText(summary == null || summary.isEmpty() ? "Sin partida reciente" : summary);
        version.setText("RetroLink v0.6.0 RC1 · PresentSync · RetroSR 2.2");

        findViewById(R.id.navProfileHome).setOnClickListener(v -> finish());
        findViewById(R.id.navProfileGames).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.navProfileRoom).setOnClickListener(v -> startActivity(new Intent(this, HostActivity.class)));
        findViewById(R.id.navProfileSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnProfileControls).setOnClickListener(v -> startActivity(new Intent(this, ControlLayoutActivity.class)));
    }
}
