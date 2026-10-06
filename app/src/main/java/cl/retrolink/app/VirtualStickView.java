package cl.retrolink.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Stick táctil analógico de RetroLink.
 * v0.6.1: deadzone radial, sensibilidad, multitouch y visual neón.
 * Android usa Y negativo hacia arriba y positivo hacia abajo; conservamos ese
 * convenio hasta libretro para evitar la doble inversión que afectaba Smash Bros.
 */
public class VirtualStickView extends View {
    public interface Listener { void onStick(int x, int y); }

    private Listener listener;
    private float knobX, knobY;
    private float deadzone = 0.12f;
    private float sensitivity = 1.00f;
    private boolean invertY;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public VirtualStickView(Context c, AttributeSet a) {
        super(c, a);
        setAlpha(0.72f);
        setFocusable(true);
        refreshPreferences();
    }

    public void setListener(Listener l) { listener = l; }

    public void refreshPreferences() {
        deadzone = RetroPreferences.stickDeadzone(getContext());
        sensitivity = RetroPreferences.stickSensitivity(getContext());
        invertY = RetroPreferences.stickInvertY(getContext());
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) * 0.44f;

        // Base oscura translúcida.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(120, 7, 18, 38));
        c.drawCircle(cx, cy, r, p);

        // Aros RetroLink: cian exterior + magenta interior muy sutil.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(3f, r * 0.035f));
        p.setColor(Color.argb(220, 20, 213, 255));
        c.drawCircle(cx, cy, r, p);
        p.setStrokeWidth(Math.max(2f, r * 0.018f));
        p.setColor(Color.argb(150, 239, 47, 255));
        c.drawCircle(cx, cy, r * 0.72f, p);

        // Guías centrales para que arriba/abajo/izquierda/derecha sean evidentes.
        p.setStrokeWidth(Math.max(1.5f, r * 0.012f));
        p.setColor(Color.argb(80, 190, 225, 255));
        c.drawLine(cx - r * 0.82f, cy, cx + r * 0.82f, cy, p);
        c.drawLine(cx, cy - r * 0.82f, cx, cy + r * 0.82f, p);

        // Thumb.
        float kx = cx + knobX * r;
        float ky = cy + knobY * r;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(235, 19, 205, 255));
        c.drawCircle(kx, ky, r * 0.29f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, r * 0.024f));
        p.setColor(Color.argb(230, 255, 80, 240));
        c.drawCircle(kx, ky, r * 0.29f, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        final int action = e.getActionMasked();
        final int actionIndex = e.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = e.getPointerId(0);
                updateFromPointer(e, 0);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Mantener el dedo que ya gobierna el stick. Si no hay uno, adoptar éste.
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
                    activePointerId = e.getPointerId(actionIndex);
                    updateFromPointer(e, actionIndex);
                }
                return true;

            case MotionEvent.ACTION_MOVE: {
                int idx = e.findPointerIndex(activePointerId);
                if (idx >= 0) updateFromPointer(e, idx);
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerId(actionIndex) == activePointerId) {
                    int replacement = actionIndex == 0 ? 1 : 0;
                    if (replacement < e.getPointerCount()) {
                        activePointerId = e.getPointerId(replacement);
                        updateFromPointer(e, replacement);
                    } else {
                        releaseStick(false);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                releaseStick(true);
                return true;

            case MotionEvent.ACTION_CANCEL:
                releaseStick(false);
                return true;

            default:
                return true;
        }
    }

    private void updateFromPointer(MotionEvent e, int index) {
        refreshPreferences();
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.max(1f, Math.min(getWidth(), getHeight()) * 0.46f);
        float dx = (e.getX(index) - cx) / r;
        float dy = (e.getY(index) - cy) / r;
        float len = (float)Math.sqrt(dx * dx + dy * dy);

        if (len > 1f) {
            dx /= len;
            dy /= len;
            len = 1f;
        }

        if (len <= deadzone) {
            knobX = 0f;
            knobY = 0f;
        } else {
            float ux = len > 0f ? dx / len : 0f;
            float uy = len > 0f ? dy / len : 0f;
            float normalized = (len - deadzone) / Math.max(0.001f, 1f - deadzone);
            // Curva suave: precisión cerca del centro y respuesta completa al borde.
            float curved = (float)Math.pow(Math.max(0f, Math.min(1f, normalized)), 1.12f);
            float magnitude = Math.max(0f, Math.min(1f, curved * sensitivity));
            knobX = ux * magnitude;
            knobY = uy * magnitude;
        }

        if (invertY) knobY = -knobY;
        fire();
        invalidate();
    }

    private void releaseStick(boolean click) {
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        knobX = 0f;
        knobY = 0f;
        fire();
        invalidate();
        if (click) performClick();
    }

    private void fire() {
        if (listener != null) {
            listener.onStick(Math.round(knobX * 127f), Math.round(knobY * 127f));
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
