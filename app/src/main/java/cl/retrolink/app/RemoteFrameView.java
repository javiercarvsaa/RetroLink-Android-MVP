package cl.retrolink.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

public class RemoteFrameView extends View {
    private Bitmap frame;
    private Bitmap previous;
    private int player = 1;
    private int playerCount = 1;
    private boolean playerView = false;
    private boolean preCropped;
    private boolean previousPreCropped;
    private boolean dk64Raw;
    private float motionScore = 1f;
    private int stillFrameStreak;
    private long lastModeChangeMs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    public RemoteFrameView(Context c, AttributeSet a) {
        super(c,a);
        paint.setColor(Color.WHITE);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        refreshRetroSr();
    }

    public void setPlayer(int p){ player=Math.max(1,Math.min(4,p)); postInvalidateOnAnimation(); }
    public void setPlayerView(boolean v){ playerView=v; lastModeChangeMs=System.currentTimeMillis(); postInvalidateOnAnimation(); }
    public boolean isPlayerView(){ return playerView; }
    public void setDk64Raw(boolean v){
        if (dk64Raw == v) return;
        dk64Raw = v;
        refreshRetroSr();
    }

    public void refreshRetroSr() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (!dk64Raw && RetroPreferences.retroSrEnabled(getContext())) {
                Api33Impl.apply(this, RetroPreferences.retroSrSharpness(getContext()));
            } else {
                Api33Impl.clear(this);
            }
        }
        postInvalidateOnAnimation();
    }

    public void setFrame(Bitmap b, int count, boolean alreadyCropped) {
        post(() -> {
            Bitmap older = previous;
            previous = frame;
            previousPreCropped = preCropped;
            frame = b;
            playerCount = Math.max(1, Math.min(4, count));
            preCropped = alreadyCropped;
            motionScore = estimateMotion(previous, frame);
            if (motionScore < 0.012f) stillFrameStreak++; else stillFrameStreak = 0;
            postInvalidateOnAnimation();
            if (older != null && older != previous && older != frame && !older.isRecycled()) older.recycle();
        });
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.BLACK);
        if (frame == null || frame.isRecycled()) {
            paint.setColor(Color.rgb(170,205,230));
            paint.setTextSize(26f);
            c.drawText("Esperando video RetroLink…", 30, getHeight()/2f, paint);
            return;
        }

        Rect src = sourceFor(frame, preCropped);
        // v0.6.5: la vista PLAYER debe tener la misma geometría visual 4:3 que P1.
        // En 2P el recorte ocupa media altura (8:3); se expande verticalmente a 4:3,
        // igual que hace IntegratedN64Activity con RACE P1. En 3P/4P el recorte ya es 4:3.
        Rect dst = (playerView && playerCount > 1)
                ? fitRect(4, 3, getWidth(), getHeight())
                : fitRect(src.width(), src.height(), getWidth(), getHeight());

        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        c.drawBitmap(frame,src,dst,paint);

        // RetroSR 2.2 Clear Motion. Sin motion vectors no es seguro mezclar frames
        // durante desplazamientos de cámara. AUTO usa historia solo cuando la escena
        // permanece prácticamente estática varios frames; al detectar movimiento corta
        // la historia completamente para evitar ghosting.
        int srMode = RetroPreferences.retroSrMode(getContext());
        // DK64 usa sus propios efectos de framebuffer. Nunca agregamos historia
        // temporal de RetroLink sobre ese juego: solo reconstrucción espacial.
        if (dk64Raw || SessionState.isDk64Profile())
            srMode = RetroPreferences.RETRO_SR_OFF;
        boolean historyAvailable = srMode != RetroPreferences.RETRO_SR_OFF
                && previous != null && !previous.isRecycled()
                && previous.getWidth() == frame.getWidth()
                && previous.getHeight() == frame.getHeight()
                && previousPreCropped == preCropped
                && System.currentTimeMillis() - lastModeChangeMs > 220;
        if (historyAvailable) {
            int alpha = 0;
            if (srMode == RetroPreferences.RETRO_SR_AUTO) {
                // ~4.7% solo tras varios frames casi inmóviles. Cualquier giro => 0%.
                if (stillFrameStreak >= 3 && motionScore < 0.010f) alpha = 12;
            } else if (srMode == RetroPreferences.RETRO_SR_TEMPORAL) {
                // Modo experimental deliberadamente conservador.
                if (motionScore < 0.010f) alpha = 20;
                else if (motionScore < 0.025f) alpha = 8;
            }
            if (alpha > 0) {
                paint.setAlpha(alpha);
                Rect prevSrc = sourceFor(previous, previousPreCropped);
                c.drawBitmap(previous, prevSrc, dst, paint);
                paint.setAlpha(255);
            }
        }
    }

    private Rect sourceFor(Bitmap bitmap, boolean cropped) {
        if (cropped || !playerView) return new Rect(0,0,bitmap.getWidth(),bitmap.getHeight());
        return SplitScreenProfile.sourceRect(bitmap.getWidth(), bitmap.getHeight(), playerCount, player);
    }

    private static float estimateMotion(Bitmap a, Bitmap b) {
        if (a == null || b == null || a.isRecycled() || b.isRecycled()) return 1f;
        if (a.getWidth()!=b.getWidth() || a.getHeight()!=b.getHeight()) return 1f;
        try {
            int w=b.getWidth(), h=b.getHeight();
            long diff=0; int samples=0;
            // Más muestras que v0.4.6: detecta antes paneos/giros para cortar historia.
            for (int gy=1; gy<=7; gy++) {
                int y=Math.min(h-1, gy*h/8);
                for (int gx=1; gx<=12; gx++) {
                    int x=Math.min(w-1, gx*w/13);
                    int ca=a.getPixel(x,y), cb=b.getPixel(x,y);
                    diff += Math.abs(Color.red(ca)-Color.red(cb));
                    diff += Math.abs(Color.green(ca)-Color.green(cb));
                    diff += Math.abs(Color.blue(ca)-Color.blue(cb));
                    samples += 3;
                }
            }
            return Math.min(1f, diff / (samples * 255f));
        } catch (Throwable ignored) { return 1f; }
    }

    private static Rect fitRect(int sw,int sh,int dw,int dh){
        float s=Math.min(dw/(float)sw,dh/(float)sh);
        int w=Math.max(1,Math.round(sw*s)), h=Math.max(1,Math.round(sh*s));
        int l=(dw-w)/2,t=(dh-h)/2;
        return new Rect(l,t,l+w,t+h);
    }

    @Override protected void onDetachedFromWindow() {
        Bitmap a=frame, b=previous;
        frame=null; previous=null;
        if(a!=null&&!a.isRecycled())a.recycle();
        if(b!=null&&b!=a&&!b.isRecycled())b.recycle();
        super.onDetachedFromWindow();
    }

    private static final class Api33Impl {
        // RetroSR 2.2 Spatial: edge-adaptive sharpen + local clamp.
        // Postproceso propio de RetroLink; no usa ni incorpora DLSS.
        private static final String SHADER =
                "uniform shader content;\n" +
                "uniform float amount;\n" +
                "float lum(half3 c){ return dot(float3(c), float3(0.299,0.587,0.114)); }\n" +
                "half4 main(float2 p) {\n" +
                "  half4 c  = content.eval(p);\n" +
                "  half4 n  = content.eval(p + float2(0.0,-1.0));\n" +
                "  half4 s  = content.eval(p + float2(0.0, 1.0));\n" +
                "  half4 e  = content.eval(p + float2(1.0, 0.0));\n" +
                "  half4 w  = content.eval(p + float2(-1.0,0.0));\n" +
                "  half4 ne = content.eval(p + float2(1.0,-1.0));\n" +
                "  half4 nw = content.eval(p + float2(-1.0,-1.0));\n" +
                "  half4 se = content.eval(p + float2(1.0, 1.0));\n" +
                "  half4 sw = content.eval(p + float2(-1.0,1.0));\n" +
                "  half3 cross = (n.rgb+s.rgb+e.rgb+w.rgb)*0.25;\n" +
                "  half3 diag  = (ne.rgb+nw.rgb+se.rgb+sw.rgb)*0.25;\n" +
                "  half3 blur  = cross*0.72 + diag*0.28;\n" +
                "  half3 mn=min(c.rgb,min(min(n.rgb,s.rgb),min(e.rgb,w.rgb)));\n" +
                "  half3 mx=max(c.rgb,max(max(n.rgb,s.rgb),max(e.rgb,w.rgb)));\n" +
                "  float contrast=clamp(lum(mx)-lum(mn),0.0,1.0);\n" +
                "  float adaptive=mix(1.0,0.55,smoothstep(0.28,0.75,contrast));\n" +
                "  half3 sharpened=c.rgb+(c.rgb-blur)*(amount*1.35*adaptive);\n" +
                "  half3 margin=half3(0.035);\n" +
                "  sharpened=clamp(sharpened,mn-margin,mx+margin);\n" +
                "  return half4(clamp(sharpened,0.0,1.0),c.a);\n" +
                "}";

        static void apply(View view, float strength) {
            try {
                android.graphics.RuntimeShader shader = new android.graphics.RuntimeShader(SHADER);
                shader.setFloatUniform("amount", Math.max(0f, Math.min(0.75f, strength)));
                view.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"));
            } catch (Throwable ignored) { view.setRenderEffect(null); }
        }
        static void clear(View view) {
            try { view.setRenderEffect(null); } catch (Throwable ignored) {}
        }
    }
}
