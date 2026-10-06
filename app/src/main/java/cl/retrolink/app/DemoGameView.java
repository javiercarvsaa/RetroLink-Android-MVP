package cl.retrolink.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class DemoGameView extends View {
    private DemoGameEngine engine;
    private boolean playerView = true;
    private int player = 1;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public DemoGameView(Context c, AttributeSet a){ super(c,a); }
    public void setEngine(DemoGameEngine e){ engine=e; invalidate(); }
    public void setPlayerView(boolean v){ playerView=v; invalidate(); }
    public boolean isPlayerView(){ return playerView; }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c); c.drawColor(Color.BLACK);
        if(engine==null){ postInvalidateDelayed(100); return; }
        Bitmap b=engine.renderFrame();
        int count=engine.getPlayerCount();
        Rect src=playerView?SplitScreenProfile.sourceRect(b.getWidth(),b.getHeight(),count,player):new Rect(0,0,b.getWidth(),b.getHeight());
        Rect dst=fitRect(src.width(),src.height(),getWidth(),getHeight());
        c.drawBitmap(b,src,dst,paint); b.recycle();
        postInvalidateDelayed(33);
    }
    private static Rect fitRect(int sw,int sh,int dw,int dh){ float s=Math.min(dw/(float)sw,dh/(float)sh); int w=Math.max(1,Math.round(sw*s)),h=Math.max(1,Math.round(sh*s)); int l=(dw-w)/2,t=(dh-h)/2; return new Rect(l,t,l+w,t+h); }
}
