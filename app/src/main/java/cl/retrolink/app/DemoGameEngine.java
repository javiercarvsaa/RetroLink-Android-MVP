package cl.retrolink.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;

import cl.retrolink.app.ble.BleProtocol;

public class DemoGameEngine {
    public static final int WIDTH = 640;
    public static final int HEIGHT = 480;

    private final int[] masks = new int[4];
    private final int[] axisX = new int[4];
    private final int[] axisY = new int[4];
    private final float[] posX = {0.5f,0.5f,0.5f,0.5f};
    private final float[] posY = {0.62f,0.62f,0.62f,0.62f};
    private int playerCount = 4;
    private String profileLabel = "DISTRIBUTED DISPLAY TEST";

    public synchronized void setInput(int player, int mask, int x, int y) {
        if (player < 1 || player > 4) return;
        masks[player-1] = mask;
        axisX[player-1] = Math.max(-127, Math.min(127, x));
        axisY[player-1] = Math.max(-127, Math.min(127, y));
    }

    public synchronized void setPlayerCount(int count) { playerCount = Math.max(1, Math.min(4, count)); }
    public synchronized int getPlayerCount() { return playerCount; }
    public synchronized void setProfileLabel(String label) { if (label != null && !label.isEmpty()) profileLabel = label; }

    public synchronized Bitmap renderFrame() {
        updatePositions();
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.drawColor(Color.rgb(12, 15, 20));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        long t = SystemClock.uptimeMillis();
        int count = playerCount;
        for (int player = 1; player <= count; player++) {
            Rect r = SplitScreenProfile.sourceRect(WIDTH, HEIGHT, count, player);
            int[] bg = {Color.rgb(24,42,52), Color.rgb(44,30,52), Color.rgb(38,48,28), Color.rgb(52,34,24)};
            p.setStyle(Paint.Style.FILL); p.setColor(bg[player-1]);
            c.drawRect(r, p);

            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5f); p.setColor(Color.argb(170,220,225,230));
            RectF track = new RectF(r.left + r.width()*0.12f, r.top + r.height()*0.18f, r.right - r.width()*0.12f, r.bottom - r.height()*0.14f);
            c.drawOval(track, p);
            p.setStrokeWidth(2f); p.setColor(Color.argb(130,90,220,120));
            RectF inner = new RectF(track.left+18, track.top+16, track.right-18, track.bottom-16);
            c.drawOval(inner, p);

            p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextSize(Math.max(22f, r.width()*0.075f)); p.setFakeBoldText(true);
            c.drawText("P" + player, r.left + 12, r.top + 32, p);
            p.setFakeBoldText(false); p.setTextSize(Math.max(12f, r.width()*0.035f)); p.setColor(Color.argb(210,230,235,240));
            c.drawText(profileLabel, r.left + 12, r.top + 52, p);

            float x = r.left + posX[player-1] * r.width();
            float y = r.top + posY[player-1] * r.height();
            float pulse = ((masks[player-1] & BleProtocol.A) != 0) ? 1.35f : 1f;
            float radius = Math.max(9f, r.width()*0.035f) * pulse;
            int[] kart = {Color.rgb(84,211,100), Color.rgb(86,156,255), Color.rgb(255,196,72), Color.rgb(255,107,107)};
            p.setColor(kart[player-1]); p.setStyle(Paint.Style.FILL);
            c.drawCircle(x, y, radius, p);
            p.setColor(Color.WHITE); p.setTextSize(radius*1.25f); p.setFakeBoldText(true);
            c.drawText(String.valueOf(player), x-radius*0.35f, y+radius*0.4f, p);
            p.setFakeBoldText(false);

            p.setColor(Color.argb(130,255,255,255)); p.setTextSize(12f);
            c.drawText("AX " + axisX[player-1] + "  AY " + axisY[player-1], r.left + 12, r.bottom - 10, p);
        }
        if (count == 3) {
            Rect empty = SplitScreenProfile.sourceRect(WIDTH, HEIGHT, 4, 4);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(8,10,14)); c.drawRect(empty,p);
            p.setTextSize(20f); p.setColor(Color.GRAY); c.drawText("P4 libre", empty.left+24, empty.top+42,p);
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(Color.argb(180,255,255,255));
        if (count >= 3) { c.drawLine(WIDTH/2f,0,WIDTH/2f,HEIGHT,p); c.drawLine(0,HEIGHT/2f,WIDTH,HEIGHT/2f,p); }
        else if (count == 2) c.drawLine(0,HEIGHT/2f,WIDTH,HEIGHT/2f,p);
        return bitmap;
    }

    private void updatePositions() {
        for (int i=0;i<4;i++) {
            float dx = axisX[i] / 127f * 0.018f;
            float dy = axisY[i] / 127f * 0.018f;
            if ((masks[i] & BleProtocol.LEFT) != 0) dx -= 0.018f;
            if ((masks[i] & BleProtocol.RIGHT) != 0) dx += 0.018f;
            if ((masks[i] & BleProtocol.UP) != 0) dy -= 0.018f;
            if ((masks[i] & BleProtocol.DOWN) != 0) dy += 0.018f;
            posX[i] = clamp(posX[i] + dx, 0.12f, 0.88f);
            posY[i] = clamp(posY[i] + dy, 0.22f, 0.82f);
            if ((masks[i] & BleProtocol.START) != 0) { posX[i]=0.5f; posY[i]=0.62f; }
        }
    }

    private static float clamp(float v,float a,float b){ return Math.max(a,Math.min(b,v)); }
}
