package cl.retrolink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private Button gfx, retroSrMode, volUp, volDown, stream, splitProfile;
    private Switch volumeButtons, blockVolume, haptic;
    private SeekBar sharpness, stickDeadzone, stickSensitivity;
    private Switch invertStickY;
    private TextView sharpValue, diagnostics, stickDeadzoneValue, stickSensitivityValue, gamepadStatus;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        InsetHelper.apply(findViewById(R.id.settingsRoot));

        gfx = findViewById(R.id.btnGraphicsProfile);
        retroSrMode = findViewById(R.id.btnRetroSrMode);
        sharpness = findViewById(R.id.seekSharpness);
        sharpValue = findViewById(R.id.txtSharpnessValue);
        volumeButtons = findViewById(R.id.swVolumeButtons);
        volUp = findViewById(R.id.btnVolumeUpMap);
        volDown = findViewById(R.id.btnVolumeDownMap);
        blockVolume = findViewById(R.id.swBlockVolume);
        haptic = findViewById(R.id.swHaptic);
        stream = findViewById(R.id.btnStreamProfile);
        splitProfile = findViewById(R.id.btnSplitScreenProfile);
        diagnostics = findViewById(R.id.txtSettingsDiagnostics);
        stickDeadzone = findViewById(R.id.seekStickDeadzone);
        stickSensitivity = findViewById(R.id.seekStickSensitivity);
        invertStickY = findViewById(R.id.swInvertStickY);
        stickDeadzoneValue = findViewById(R.id.txtStickDeadzoneValue);
        stickSensitivityValue = findViewById(R.id.txtStickSensitivityValue);
        gamepadStatus = findViewById(R.id.txtGamepadStatus);

        gfx.setOnClickListener(v -> {
            RetroPreferences.setGraphicsProfile(this, (RetroPreferences.graphicsProfile(this) + 1) % 3);
            refresh();
        });
        retroSrMode.setOnClickListener(v -> {
            RetroPreferences.nextRetroSrMode(this);
            refresh();
        });
        sharpness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = progress / 100f;
                sharpValue.setText(progress + "%");
                if (fromUser) RetroPreferences.setRetroSrSharpness(SettingsActivity.this, v);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        volumeButtons.setOnCheckedChangeListener((b1, checked) -> RetroPreferences.setVolumeButtonsEnabled(this, checked));
        blockVolume.setOnCheckedChangeListener((b1, checked) -> RetroPreferences.setBlockSystemVolume(this, checked));
        haptic.setOnCheckedChangeListener((b1, checked) -> RetroPreferences.setHardwareHaptic(this, checked));
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
        volUp.setOnClickListener(v -> { RetroPreferences.setVolumeUpMapping(this, nextMap(RetroPreferences.volumeUpMapping(this))); refresh(); });
        volDown.setOnClickListener(v -> { RetroPreferences.setVolumeDownMapping(this, nextMap(RetroPreferences.volumeDownMapping(this))); refresh(); });
        stream.setOnClickListener(v -> cycleStream());
        findViewById(R.id.btnEditControls).setOnClickListener(v -> startActivity(new Intent(this, ControlLayoutActivity.class)));
        splitProfile.setOnClickListener(v -> {
            RetroPreferences.nextSplitCropMode(this);
            refresh();
        });
        findViewById(R.id.btnTestControls).setOnClickListener(v -> startActivity(new Intent(this, ControlTestActivity.class)));
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
        for (int i=0;i<m.length;i++) if (m[i].equals(current)) return m[(i+1)%m.length];
        return "A";
    }

    private void cycleStream() {
        int fps = RetroPreferences.streamFps(this);
        if (fps <= 0) { RetroPreferences.setStreamFps(this, 40); RetroPreferences.setStreamQuality(this, 82); }
        else if (fps >= 40) { RetroPreferences.setStreamFps(this, 30); RetroPreferences.setStreamQuality(this, 86); }
        else { RetroPreferences.setStreamFps(this, 0); RetroPreferences.setStreamQuality(this, 80); }
        refresh();
    }

    private void refresh() {
        gfx.setText(RetroPreferences.graphicsProfileLabel(this));
        retroSrMode.setText("RETROSR 2.1 · " + RetroPreferences.retroSrModeLabel(this));
        int s = Math.round(RetroPreferences.retroSrSharpness(this)*100f);
        sharpness.setProgress(s); sharpValue.setText(s+"%");
        volumeButtons.setChecked(RetroPreferences.volumeButtonsEnabled(this));
        volUp.setText("VOL +  →  " + RetroPreferences.volumeUpMapping(this));
        volDown.setText("VOL −  →  " + RetroPreferences.volumeDownMapping(this));
        blockVolume.setChecked(RetroPreferences.blockSystemVolume(this));
        haptic.setChecked(RetroPreferences.hardwareHaptic(this));
        int dz = Math.round(RetroPreferences.stickDeadzone(this) * 100f);
        int sens = Math.round(RetroPreferences.stickSensitivity(this) * 100f);
        stickDeadzone.setProgress(Math.max(0, dz - 2));
        stickSensitivity.setProgress(Math.max(0, sens - 50));
        stickDeadzoneValue.setText(dz + "%");
        stickSensitivityValue.setText(sens + "%");
        invertStickY.setChecked(RetroPreferences.stickInvertY(this));
        gamepadStatus.setText(AndroidGamepadMapper.connectedGamepadsSummary());
        stream.setText(RetroPreferences.streamProfileLabel(this));
        splitProfile.setText("SPLIT: " + RetroPreferences.splitCropModeLabel(this));
        diagnostics.setText("RetroSR 2.2 " + RetroPreferences.retroSrModeLabel(this)
                + " · nitidez " + s + "% · salida " + RetroPreferences.resolvedStreamFps(this) + " FPS");
    }
}
