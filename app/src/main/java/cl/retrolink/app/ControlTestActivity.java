package cl.retrolink.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.TextView;

import cl.retrolink.app.ble.BleProtocol;

public class ControlTestActivity extends Activity {
    private N64ControlBinder controls;
    private TextView status, physical;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_control_test);
        InsetHelper.apply(findViewById(R.id.controlTestRoot));
        status = findViewById(R.id.txtControlTestStatus);
        physical = findViewById(R.id.txtPhysicalMappings);
        physical.setText("VOL + → " + RetroPreferences.volumeUpMapping(this) + "    ·    VOL − → " + RetroPreferences.volumeDownMapping(this));
        controls = new N64ControlBinder(this, (mask,x,y) -> status.setText("INPUT · " + BleProtocol.buttonsToText(mask) + " · Stick " + x + "," + y));
        findViewById(R.id.btnTestBack).setOnClickListener(v -> finish());
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (HardwareButtonMapper.handle(this, controls, event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onPause() {
        if (controls != null) controls.release();
        super.onPause();
    }
}
