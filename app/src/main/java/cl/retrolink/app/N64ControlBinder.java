package cl.retrolink.app;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import cl.retrolink.app.ble.BleProtocol;

public class N64ControlBinder {
    public interface Listener { void onInput(int mask, int axisX, int axisY); }
    private final Listener listener;
    private int touchMask, hardwareMask, axisX, axisY;

    public N64ControlBinder(android.app.Activity a, Listener l) {
        listener=l;
        bind(a,R.id.btnA,BleProtocol.A); bind(a,R.id.btnB,BleProtocol.B); bind(a,R.id.btnZ,BleProtocol.Z);
        bind(a,R.id.btnL,BleProtocol.L); bind(a,R.id.btnR,BleProtocol.R); bind(a,R.id.btnStart,BleProtocol.START);
        bind(a,R.id.btnCUp,BleProtocol.C_UP); bind(a,R.id.btnCDown,BleProtocol.C_DOWN); bind(a,R.id.btnCLeft,BleProtocol.C_LEFT); bind(a,R.id.btnCRight,BleProtocol.C_RIGHT);
        View stick=a.findViewById(R.id.virtualStick);
        if(stick instanceof VirtualStickView) ((VirtualStickView)stick).setListener((x,y)->{axisX=x;axisY=y;fire();});
    }

    private void bind(android.app.Activity a,int id,int bit){
        View v=a.findViewById(id); if(v==null)return;
        v.setOnTouchListener((view,e)->{
            int act=e.getActionMasked();
            if(act==MotionEvent.ACTION_DOWN){
                touchMask|=bit;
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                fire();
                return true;
            }
            if(act==MotionEvent.ACTION_UP||act==MotionEvent.ACTION_CANCEL){
                touchMask&=~bit;
                fire();
                view.performClick();
                return true;
            }
            return true;
        });
    }

    public void setHardwareButton(int bit, boolean down) {
        if (bit == 0) return;
        if (down) hardwareMask |= bit; else hardwareMask &= ~bit;
        fire();
    }

    public int currentMask() { return touchMask | hardwareMask; }

    public void release(){ touchMask=0;hardwareMask=0;axisX=axisY=0;fire(); }
    private void fire(){ if(listener!=null) listener.onInput(touchMask|hardwareMask,axisX,axisY); }
}
