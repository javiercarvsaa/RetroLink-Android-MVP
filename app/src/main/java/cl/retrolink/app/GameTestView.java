package cl.retrolink.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import cl.retrolink.app.ble.BleProtocol;

public class GameTestView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] masks = new int[4];
    private final boolean[] active = new boolean[4];
    private final float[] x = new float[4];
    private final float[] y = new float[4];
    private final int[] colors = new int[]{0xFF56D364, 0xFF58A6FF, 0xFFFFC857, 0xFFFF7B72};
    private boolean initialized;
    private long lastFrameNanos;
    private boolean running;

    public GameTestView(Context context) {
        super(context);
        init();
    }

    public GameTestView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(0xFF14181E);
        textPaint.setColor(0xFFF3F5F7);
        textPaint.setTextSize(32f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setPlayerActive(int player, boolean isActive) {
        if (player < 1 || player > 4) return;
        active[player - 1] = isActive;
        if (!isActive) masks[player - 1] = 0;
        invalidate();
    }

    public void setInput(int player, int mask) {
        if (player < 1 || player > 4) return;
        masks[player - 1] = mask;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetPositions();
    }

    private void resetPositions() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        x[0] = w * 0.25f; y[0] = h * 0.30f;
        x[1] = w * 0.75f; y[1] = h * 0.30f;
        x[2] = w * 0.25f; y[2] = h * 0.70f;
        x[3] = w * 0.75f; y[3] = h * 0.70f;
        initialized = true;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastFrameNanos = 0L;
        postOnAnimation(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = System.nanoTime();
            float dt = lastFrameNanos == 0L ? 0f : Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000f);
            lastFrameNanos = now;
            update(dt);
            invalidate();
            postOnAnimation(this);
        }
    };

    private void update(float dt) {
        if (!initialized) return;
        float speed = Math.max(160f, getWidth() * 0.32f);
        float margin = 34f;
        for (int i = 0; i < 4; i++) {
            if (!active[i]) continue;
            int m = masks[i];
            if ((m & BleProtocol.LEFT) != 0) x[i] -= speed * dt;
            if ((m & BleProtocol.RIGHT) != 0) x[i] += speed * dt;
            if ((m & BleProtocol.UP) != 0) y[i] -= speed * dt;
            if ((m & BleProtocol.DOWN) != 0) y[i] += speed * dt;
            x[i] = Math.max(margin, Math.min(getWidth() - margin, x[i]));
            y[i] = Math.max(margin, Math.min(getHeight() - margin, y[i]));
            if ((m & BleProtocol.START) != 0) {
                // START lleva el marcador de vuelta a su cuadrante inicial.
                switch (i) {
                    case 0: x[i] = getWidth() * 0.25f; y[i] = getHeight() * 0.30f; break;
                    case 1: x[i] = getWidth() * 0.75f; y[i] = getHeight() * 0.30f; break;
                    case 2: x[i] = getWidth() * 0.25f; y[i] = getHeight() * 0.70f; break;
                    case 3: x[i] = getWidth() * 0.75f; y[i] = getHeight() * 0.70f; break;
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xFF343A43);
        canvas.drawRoundRect(new RectF(1f, 1f, getWidth() - 1f, getHeight() - 1f), 22f, 22f, paint);

        for (int i = 0; i < 4; i++) {
            float radius = ((masks[i] & BleProtocol.A) != 0) ? 32f : 23f;
            paint.setStyle(active[i] ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(active[i] ? colors[i] : 0xFF59616C);
            canvas.drawCircle(x[i], y[i], radius, paint);

            textPaint.setColor(active[i] ? 0xFF101216 : 0xFFAAB2BD);
            textPaint.setTextSize(radius > 25f ? 25f : 20f);
            canvas.drawText("P" + (i + 1), x[i], y[i] + 8f, textPaint);
        }
    }
}
