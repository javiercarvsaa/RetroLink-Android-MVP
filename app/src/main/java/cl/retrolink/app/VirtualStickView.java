package cl.retrolink.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class VirtualStickView extends View {
    public interface Listener { void onStick(int x, int y); }
    private Listener listener;
    private float knobX, knobY;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public VirtualStickView(Context c, AttributeSet a){ super(c,a); setAlpha(0.55f); }
    public void setListener(Listener l){ listener=l; }
    @Override protected void onDraw(Canvas c){
        float cx=getWidth()/2f, cy=getHeight()/2f, r=Math.min(getWidth(),getHeight())*0.43f;
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(100,255,255,255)); c.drawCircle(cx,cy,r,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4f); p.setColor(Color.argb(180,255,255,255)); c.drawCircle(cx,cy,r,p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(210,86,211,100));
        c.drawCircle(cx+knobX*r,cy+knobY*r,r*0.34f,p);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){ knobX=knobY=0; fire(); invalidate(); performClick(); return true; }
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*0.43f;
            float dx=(e.getX()-cx)/r,dy=(e.getY()-cy)/r; float len=(float)Math.sqrt(dx*dx+dy*dy); if(len>1f){dx/=len;dy/=len;}
            knobX=dx; knobY=dy; fire(); invalidate(); return true;
        }
        return true;
    }
    private void fire(){ if(listener!=null) listener.onStick(Math.round(knobX*127f),Math.round(knobY*127f)); }
    @Override public boolean performClick(){ super.performClick(); return true; }
}
