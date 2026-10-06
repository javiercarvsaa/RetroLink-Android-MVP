package cl.retrolink.app.net;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FrameStreamClient {
    public interface Listener { void onVideoStatus(String s); void onFrame(Bitmap b,int players,boolean preCropped,boolean dk64Raw); }

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean deliveryScheduled = new AtomicBoolean();
    private final AtomicReference<DecodedFrame> latest = new AtomicReference<>();
    private Socket socket;
    private Thread thread;
    private DataOutputStream controlOut;
    private volatile int player = 1;
    private volatile boolean playerView = false;
    private long statsStartMs;
    private int statsFrames;

    public FrameStreamClient(Listener l){ listener=l; }

    public void start(String host, int player, boolean playerView){
        stop();
        if(host==null||host.trim().isEmpty()){status("Sin IP Wi‑Fi del Host");return;}
        this.player = Math.max(1, Math.min(4, player));
        this.playerView = playerView;
        running.set(true);
        thread=new Thread(()->loop(host.trim()),"RetroLinkVideoClientLL");
        try { thread.setPriority(Thread.NORM_PRIORITY + 1); } catch (Throwable ignored) {}
        thread.start();
    }

    public synchronized void setPlayerView(boolean enabled) {
        playerView = enabled;
        DataOutputStream out = controlOut;
        if (out == null || !running.get()) return;
        try {
            out.writeInt(StreamProtocol.CMD_VIEW);
            out.writeInt(enabled ? 1 : 0);
            out.flush();
        } catch (Exception ignored) {}
    }

    public void stop(){
        running.set(false);
        synchronized (this) { controlOut = null; }
        try{if(socket!=null)socket.close();}catch(Exception ignored){}
        socket=null;
        DecodedFrame p = latest.getAndSet(null);
        if (p != null && p.bitmap != null && !p.bitmap.isRecycled()) p.bitmap.recycle();
    }

    private void loop(String host){
        status("Conectando video Low Latency a "+host+"…");
        try{
            socket=new Socket();
            socket.connect(new InetSocketAddress(host,FrameStreamServer.PORT),5000);
            socket.setTcpNoDelay(true);
            socket.setReceiveBufferSize(512 * 1024);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            synchronized (this) { controlOut = out; }
            out.writeInt(StreamProtocol.HELLO);
            out.writeInt(player);
            out.writeInt(playerView ? 1 : 0);
            out.flush();

            status("Video LL conectado · "+host);
            statsStartMs = System.currentTimeMillis();
            statsFrames = 0;
            DataInputStream in=new DataInputStream(socket.getInputStream());
            while(running.get()){
                int magic=in.readInt();
                if (magic != StreamProtocol.FRAME) throw new IllegalStateException("Protocolo de video incompatible");
                int flags=in.readInt();
                int players=in.readInt();
                int len=in.readInt();
                if(len<100||len>4_000_000)throw new IllegalStateException("Frame inválido");
                byte[] data=new byte[len];
                in.readFully(data);
                Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length);
                if(b!=null) {
                    boolean pre = (flags & StreamProtocol.FLAG_PRE_CROPPED) != 0;
                    boolean dk64Raw = (flags & StreamProtocol.FLAG_DK64_RAW) != 0;
                    offerDecoded(new DecodedFrame(b, players, pre, dk64Raw));
                    reportStats(pre, dk64Raw);
                }
            }
        }catch(Exception e){
            if(running.get())status("Video desconectado: "+e.getMessage());
        }finally{
            running.set(false);
            synchronized (this) { controlOut = null; }
            try{if(socket!=null)socket.close();}catch(Exception ignored){}
        }
    }

    private void reportStats(boolean preCropped, boolean dk64Raw) {
        statsFrames++;
        long now = System.currentTimeMillis();
        long elapsed = now - statsStartMs;
        if (elapsed < 1000) return;
        int fps = Math.round(statsFrames * 1000f / Math.max(1L, elapsed));
        statsFrames = 0;
        statsStartMs = now;
        status("Video LL · " + fps + " FPS · P" + player + (preCropped ? " PLAYER" : " FULL")
                + (dk64Raw ? " · DK64 RAW" : " · RetroSR 2.2"));
    }

    private void offerDecoded(DecodedFrame frame) {
        DecodedFrame old = latest.getAndSet(frame);
        if (old != null && old.bitmap != null && !old.bitmap.isRecycled()) old.bitmap.recycle();
        scheduleDelivery();
    }

    private void scheduleDelivery() {
        if (!deliveryScheduled.compareAndSet(false, true)) return;
        main.post(() -> {
            while (true) {
                DecodedFrame f = latest.getAndSet(null);
                if (f != null && listener != null) listener.onFrame(f.bitmap, f.players, f.preCropped, f.dk64Raw);
                deliveryScheduled.set(false);
                if (latest.get() != null && deliveryScheduled.compareAndSet(false, true)) continue;
                break;
            }
        });
    }

    private void status(String s){if(listener!=null)main.post(()->listener.onVideoStatus(s));}

    private static final class DecodedFrame {
        final Bitmap bitmap; final int players; final boolean preCropped; final boolean dk64Raw;
        DecodedFrame(Bitmap bitmap, int players, boolean preCropped, boolean dk64Raw) {
            this.bitmap = bitmap; this.players = players; this.preCropped = preCropped; this.dk64Raw = dk64Raw;
        }
    }
}
