package cl.retrolink.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** User-facing controls for the rule-based adaptive optimizer. */
public class OptimizationSettingsActivity extends Activity {
    private Button modeButton;
    private Switch calibrationSwitch;
    private Switch temporalSwitch;
    private TextView capabilityText;
    private TextView statusText;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_optimization_settings);
        InsetHelper.apply(findViewById(R.id.optimizerRoot));

        modeButton = findViewById(R.id.btnOptimizerMode);
        calibrationSwitch = findViewById(R.id.swOptimizerCalibration);
        temporalSwitch = findViewById(R.id.swOptimizerTemporal);
        capabilityText = findViewById(R.id.txtOptimizerCapabilities);
        statusText = findViewById(R.id.txtOptimizerStatus);

        modeButton.setOnClickListener(v -> {
            OptimizationProfileStore.nextMode(this);
            refresh();
        });
        calibrationSwitch.setOnCheckedChangeListener((button, checked) -> {
            OptimizationProfileStore.setCalibrationEnabled(this, checked);
            refresh();
        });
        temporalSwitch.setOnCheckedChangeListener((button, checked) -> {
            OptimizationProfileStore.setExperimentalTemporalEnabled(this, checked);
            refresh();
        });
        findViewById(R.id.btnOptimizerResetProfiles).setOnClickListener(v -> {
            OptimizationProfileStore.resetStableProfiles(this);
            Toast.makeText(this, "Perfiles estables restablecidos", Toast.LENGTH_SHORT).show();
            refresh();
        });
        findViewById(R.id.btnOptimizerDone).setOnClickListener(v -> finish());
        findViewById(R.id.btnOptimizerBack).setOnClickListener(v -> finish());
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        int mode = OptimizationProfileStore.mode(this);
        modeButton.setText("MODO · " + OptimizationProfileStore.modeLabel(mode));
        calibrationSwitch.setChecked(OptimizationProfileStore.calibrationEnabled(this));
        temporalSwitch.setChecked(OptimizationProfileStore.experimentalTemporalEnabled(this));
        AdaptiveOptimizationEngine.Plan sample = AdaptiveOptimizationEngine.resolve(
                this, CoreRegistry.N64, "", false);
        capabilityText.setText(sample.capabilities.summary());
        statusText.setText("Motor actual: reglas verificables + ADPF cuando existe.\n"
                + "IA neuronal: no incluida en este incremento.\n"
                + "Última medición: " + OptimizationProfileStore.lastSummary(this));
    }
}
