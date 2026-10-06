package cl.retrolink.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Editor visual del overlay N64. */
public class ControlLayoutActivity extends Activity {
    public static final String EXTRA_SCOPE = "control_scope";
    private FrameLayout root;
    private TextView selectedLabel;
    private SeekBar sizeBar, opacityBar;
    private View selected;
    private float downRawX, downRawY, startX, startY;
    private boolean programmaticBars;
    private String scope = ControlLayoutStore.SCOPE_N64_LANDSCAPE;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_control_layout);
        hideSystemUi();

        String requestedScope = getIntent().getStringExtra(EXTRA_SCOPE);
        if (ControlLayoutStore.SCOPE_N64_REMOTE_LANDSCAPE.equals(requestedScope)) {
            scope = ControlLayoutStore.SCOPE_N64_REMOTE_LANDSCAPE;
        }

        root = findViewById(R.id.controlEditorRoot);
        selectedLabel = findViewById(R.id.txtSelectedControl);
        sizeBar = findViewById(R.id.seekControlSize);
        opacityBar = findViewById(R.id.seekControlOpacity);

        root.post(() -> {
            ControlLayoutStore.applyAll(this, root, scope);
            bindDrags();
        });

        sizeBar.setMax(110); // 55%..165%
        opacityBar.setMax(75); // 25%..100%
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (programmaticBars || selected == null) return;
                float scale = 0.55f + progress / 100f;
                selected.setScaleX(scale);
                selected.setScaleY(scale);
                clampSelected();
                updateSelectedLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (programmaticBars || selected == null) return;
                selected.setAlpha(0.25f + progress / 100f);
                updateSelectedLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.btnControlSave).setOnClickListener(v -> {
            ControlLayoutStore.saveAll(this, root, scope);
            Toast.makeText(this, "Layout de controles guardado", Toast.LENGTH_SHORT).show();
            finish();
        });
        findViewById(R.id.btnControlReset).setOnClickListener(v -> {
            ControlLayoutStore.reset(this, scope);
            recreate();
        });
        findViewById(R.id.btnControlMirror).setOnClickListener(v -> {
            ControlLayoutStore.mirrorHorizontally(this, root);
            Toast.makeText(this, "Distribución izquierda/derecha invertida", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnControlClose).setOnClickListener(v -> finish());
    }

    private void bindDrags() {
        for (int id : ControlLayoutStore.CONTROL_IDS) {
            View v = findViewById(id);
            if (v == null) continue;
            v.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        select(view);
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = view.getX();
                        startY = view.getY();
                        view.bringToFront();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float nx = startX + event.getRawX() - downRawX;
                        float ny = startY + event.getRawY() - downRawY;
                        placeClamped(view, nx, ny);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.performClick();
                        updateSelectedLabel();
                        return true;
                    default:
                        return true;
                }
            });
        }
    }

    private void select(View v) {
        selected = v;
        programmaticBars = true;
        sizeBar.setProgress(Math.round((v.getScaleX() - 0.55f) * 100f));
        opacityBar.setProgress(Math.round((v.getAlpha() - 0.25f) * 100f));
        programmaticBars = false;
        updateSelectedLabel();
    }

    private void updateSelectedLabel() {
        if (selected == null) {
            selectedLabel.setText("Selecciona y arrastra un control");
            return;
        }
        int index = ControlLayoutStore.indexForId(selected.getId());
        selectedLabel.setText(ControlLayoutStore.labelForIndex(index)
                + " · tamaño " + Math.round(selected.getScaleX() * 100f) + "%"
                + " · opacidad " + Math.round(selected.getAlpha() * 100f) + "%");
    }

    private void clampSelected() {
        if (selected != null) placeClamped(selected, selected.getX(), selected.getY());
    }

    private void placeClamped(View v, float x, float y) {
        float visualHalfW = v.getWidth() * v.getScaleX() * 0.5f;
        float visualHalfH = v.getHeight() * v.getScaleY() * 0.5f;
        float centerX = x + v.getWidth() * 0.5f;
        float centerY = y + v.getHeight() * 0.5f;
        float topSafe = dp(58) + visualHalfH;
        float bottomSafe = root.getHeight() - dp(78) - visualHalfH;
        centerX = Math.max(visualHalfW, Math.min(root.getWidth() - visualHalfW, centerX));
        centerY = Math.max(topSafe, Math.min(bottomSafe, centerY));
        v.setX(centerX - v.getWidth() * 0.5f);
        v.setY(centerY - v.getHeight() * 0.5f);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
