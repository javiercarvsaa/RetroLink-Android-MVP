package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Ajustes v0.6.3: toda opción editable está conectada a una función real. */
public class SettingsActivity extends Activity {
    private Button gfx, retroSrMode, volUp, volDown, streamFps, splitProfile;
    private Switch volumeButtons, blockVolume, haptic, invertStickY;
    private SeekBar sharpness, streamQuality, stickDeadzone, stickSensitivity;
    private TextView sharpValue, diagnostics, stickDeadzoneValue, stickSensitivityValue,
            gamepadStatus, streamQualityValue, streamResolution, presentSyncStatus;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        InsetHelper.apply(findViewById(R.id.settingsRoot));

        gfx = findViewById(R.id.btnGraphicsProfile);
        retroSrMode = findViewById(R.id.btnRetroSrMode);
        sharpness = findViewById(R.id.seekSharpness);
        sharpValue = findViewById(R.id.txtSharpnessValue);
        streamFps = findViewById(R.id.btnStreamFps);
        streamQuality = findViewById(R.id.seekStreamQuality);
        streamQualityValue = findViewById(R.id.txtStreamQualityValue);
        streamResolution = findViewById(R.id.txtStreamResolution);
        volumeButtons = findViewById(R.id.swVolumeButtons);
        volUp = findViewById(R.id.btnVolumeUpMap);
        volDown = findViewById(R.id.btnVolumeDownMap);
        blockVolume = findViewById(R.id.swBlockVolume);
        haptic = findViewById(R.id.swHaptic);
        splitProfile = findViewById(R.id.btnSplitScreenProfile);
        diagnostics = findViewById(R.id.txtSettingsDiagnostics);
        stickDeadzone = findViewById(R.id.seekStickDeadzone);
        stickSensitivity = findViewById(R.id.seekStickSensitivity);
        invertStickY = findViewById(R.id.swInvertStickY);
        stickDeadzoneValue = findViewById(R.id.txtStickDeadzoneValue);
        stickSensitivityValue = findViewById(R.id.txtStickSensitivityValue);
        gamepadStatus = findViewById(R.id.txtGamepadStatus);
        presentSyncStatus = findViewById(R.id.txtPresentSyncStatus);

        gfx.setOnClickListener(v -> {
            RetroPreferences.setGraphicsProfile(this, (RetroPreferences.graphicsProfile(this) + 1) % 3);
            refresh();
        });
        retroSrMode.setOnClickListener(v -> {
            RetroPreferences.nextRetroSrMode(this);
            refresh();
        });

        sharpness.setMax(100);
        sharpness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sharpValue.setText(progress + "%");
                if (fromUser) RetroPreferences.setRetroSrSharpness(SettingsActivity.this, progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        streamFps.setOnClickListener(v -> {
            int current = RetroPreferences.streamFps(this);
            int next = current <= 0 ? 30 : current == 30 ? 40 : current == 40 ? 50 : current == 50 ? 60 : 0;
            RetroPreferences.setStreamFps(this, next);
            refresh();
        });
        streamQuality.setMax(25); // Q65..Q90
        streamQuality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int q = progress + 65;
                streamQualityValue.setText("Q" + q);
                if (fromUser) RetroPreferences.setStreamQuality(SettingsActivity.this, q);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        stickDeadzone.setMax(28); // 2..30%
        stickDeadzone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = progress + 2;
                stickDeadzoneValue.setText(percent + "%");
                if (fromUser) RetroPreferences.setStickDeadzone(SettingsActivity.this, percent / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        stickSensitivity.setMax(100); // 50..150%
        stickSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = progress + 50;
                stickSensitivityValue.setText(percent + "%");
                if (fromUser) RetroPreferences.setStickSensitivity(SettingsActivity.this, percent / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        invertStickY.setOnCheckedChangeListener((b1, checked) -> RetroPreferences.setStickInvertY(this, checked));

        volumeButtons.setOnCheckedChangeListener((b1, checked) -> RetroPreferences.setVolumeButtonsEnabled(this, checked));
        blockVolume.setOnCheckedChangeListener((b1, checked) -> RetroPreferences.setBlockSystemVolume(this, checked));
        haptic.setOnCheckedChangeListener((b1, checked) -> RetroPreferences.setHardwareHaptic(this, checked));
        volUp.setOnClickListener(v -> { RetroPreferences.setVolumeUpMapping(this, nextMap(RetroPreferences.volumeUpMapping(this))); refresh(); });
        volDown.setOnClickListener(v -> { RetroPreferences.setVolumeDownMapping(this, nextMap(RetroPreferences.volumeDownMapping(this))); refresh(); });

        findViewById(R.id.btnEditControls).setOnClickListener(v -> startActivity(new Intent(this, ControlLayoutActivity.class)));
        findViewById(R.id.btnTestControls).setOnClickListener(v -> startActivity(new Intent(this, ControlTestActivity.class)));
        splitProfile.setOnClickListener(v -> { RetroPreferences.nextSplitCropMode(this); refresh(); });

        findViewById(R.id.btnSettingsReset).setOnClickListener(v -> {
            RetroPreferences.resetUserSettings(this);
            refresh();
            Toast.makeText(this, "Ajustes restablecidos", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnSettingsDone).setOnClickListener(v -> finish());
        findViewById(R.id.btnSettingsBack).setOnClickListener(v -> finish());
        findViewById(R.id.navSettingsHome).setOnClickListener(v -> finish());
        findViewById(R.id.navSettingsGames).setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.navSettingsRoom).setOnClickListener(v -> startActivity(new Intent(this, HostActivity.class)));
        findViewById(R.id.navSettingsProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private String nextMap(String current) {
        String[] m = RetroPreferences.mappings();
        for (int i = 0; i < m.length; i++) if (m[i].equals(current)) return m[(i + 1) % m.length];
        return "A";
    }

    private void refresh() {
        gfx.setText(RetroPreferences.graphicsProfileLabel(this));
        retroSrMode.setText("RETROSR 2.2 · " + RetroPreferences.retroSrModeLabel(this));
        int sharp = Math.round(RetroPreferences.retroSrSharpness(this) * 100f);
        sharpness.setProgress(sharp);
        sharpValue.setText(sharp + "%");

        int fps = RetroPreferences.streamFps(this);
        streamFps.setText(fps <= 0 ? "FPS: AUTO · " + RetroPreferences.resolvedStreamFps(this) : "FPS: " + fps);
        int q = RetroPreferences.streamQuality(this);
        streamQuality.setProgress(Math.max(0, q - 65));
        streamQualityValue.setText("Q" + q);
        streamResolution.setText(RetroPreferences.streamWidth(this) + " × " + RetroPreferences.streamHeight(this) + " · JPEG local");

        int dz = Math.round(RetroPreferences.stickDeadzone(this) * 100f);
        int sens = Math.round(RetroPreferences.stickSensitivity(this) * 100f);
        stickDeadzone.setProgress(Math.max(0, dz - 2));
        stickSensitivity.setProgress(Math.max(0, sens - 50));
        stickDeadzoneValue.setText(dz + "%");
        stickSensitivityValue.setText(sens + "%");
        invertStickY.setChecked(RetroPreferences.stickInvertY(this));

        gamepadStatus.setText(AndroidGamepadMapper.connectedGamepadsSummary());
        volumeButtons.setChecked(RetroPreferences.volumeButtonsEnabled(this));
        volUp.setText("VOL + → " + RetroPreferences.volumeUpMapping(this));
        volDown.setText("VOL − → " + RetroPreferences.volumeDownMapping(this));
        blockVolume.setChecked(RetroPreferences.blockSystemVolume(this));
        haptic.setChecked(RetroPreferences.hardwareHaptic(this));

        splitProfile.setText("RECORTE P1: " + RetroPreferences.splitCropModeLabel(this));
        presentSyncStatus.setText("● PresentSync activo · cadencia del core · EGL VSync");

        File n64 = new File(getApplicationInfo().nativeLibraryDir, CoreRegistry.N64.libraryFile);
        diagnostics.setText((n64.isFile() ? "● Core N64 instalado" : "○ Core N64 no encontrado")
                + "   ·   Render " + RetroPreferences.renderWidth(this) + "×" + RetroPreferences.renderHeight(this)
                + "   ·   Stream " + RetroPreferences.resolvedStreamFps(this) + " FPS / Q" + q);
    }
}
