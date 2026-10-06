package cl.retrolink.app.net;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import cl.retrolink.app.DemoGameEngine;
import cl.retrolink.app.EmulatorFrameHub;
import cl.retrolink.app.RetroPreferences;
import cl.retrolink.app.SessionState;
import cl.retrolink.app.SplitScreenProfile;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

public class FrameStreamServer {
    public static final int PORT = 46721;
    public interface Listener { void onVideoServerStatus(String s); void onClientCount(int count); }

    private final Context context;
    private final DemoGameEngine engine;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private static final AtomicInteger ACTIVE_CLIENTS = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private ServerSocket serverSocket;
    private Thread acceptThread, frameThread;
    private double encodeWorkMsEma;

    public FrameStreamServer(Context context, DemoGameEngine e, Listener l) {
        this.context = context.getApplicationContext();
        engine = e;
        listener = l;
    }

    public static boolean hasRemoteClients() { return ACTIVE_CLIENTS.get() > 0; }

    public void start() {
        if (running.getAndSet(true)) return;
        try {
            serverSocket = new ServerSocket(PORT);
            serverSocket.setReuseAddress(true);
        } catch (Exception e) {
            running.set(false);
            status("No se pudo abrir video local: " + e.getMessage());
            return;
        }
        acceptThread = new Thread(this::acceptLoop, "RetroLinkAccept");
        frameThread = new Thread(this::frameLoop, "RetroLinkFramesLL");
        try { frameThread.setPriority(Thread.NORM_PRIORITY + 1); } catch (Throwable ignored) {}
        acceptThread.start();
        frameThread.start();
        status("Video Low Latency activo · puerto " + PORT);
    }

    public void stop() {
        running.set(false);
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        for (Client c : clients) c.close();
        clients.clear();
        count();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket s = serverSocket.accept();
                s.setTcpNoDelay(true);
                s.setSendBufferSize(512 * 1024);
                Client c = new Client(s);
                clients.add(c);
                c.start();
                count();
            } catch (Exception e) {
                if (running.get()) status("Video: " + e.getMessage());
            }
        }
    }

    private void frameLoop() {
        long lastFrameId = -1;
        while (running.get()) {
            if (clients.isEmpty()) {
                try { Thread.sleep(160); } catch (InterruptedException ignored) {}
                continue;
            }

            int desiredFps = Math.max(25, RetroPreferences.resolvedStreamFps(context));
            int targetFps = desiredFps;
            if (encodeWorkMsEma > 0) {
                double budget = 1000.0 / desiredFps;
                if (encodeWorkMsEma > budget * 0.88) targetFps = Math.max(30, desiredFps - 10);
            }
            long framePeriod = Math.max(16, 1000L / Math.max(1, targetFps));
            long started = System.currentTimeMillis();

            Bitmap b = null;
            try {
                long id = EmulatorFrameHub.frameId();
                if (id != lastFrameId) {
                    b = EmulatorFrameHub.copyLatest();
                    lastFrameId = id;
                }
                if (b == null && EmulatorFrameHub.frameId() == 0) b = engine.renderFrame();
                if (b != null) {
                    int pc = Math.max(1, Math.min(4, SessionState.getPlayerCount()));
                    Map<String, byte[]> encoded = new HashMap<>();
                    for (Client c : clients) {
                        boolean crop = c.playerView && pc > 1 && c.player >= 1 && c.player <= pc;
                        String key = crop ? "P" + c.player : "FULL";
                        byte[] frame = encoded.get(key);
                        if (frame == null) {
                            Bitmap payload = b;
                            Bitmap cropped = null;
                            Bitmap focused = null;
                            Bitmap normalized = null;
                            try {
                                if (crop) {
                                    // v0.6.6: extrae exactamente el viewport del jugador usando el
                                    // mismo overscan configurado para P1 y elimina la unión central.
                                    Rect r = SplitScreenProfile.focusedSourceRect(
                                            b.getWidth(), b.getHeight(), pc, c.player,
                                            RetroPreferences.splitOverscan(context));
                                    cropped = Bitmap.createBitmap(b, r.left, r.top, r.width(), r.height());

                                    // Algunos juegos dejan una banda negra dentro del viewport P2.
                                    // Se detecta solo si el borde es prácticamente negro de extremo
                                    // a extremo; así evitamos confundir una escena oscura con letterbox.
                                    Rect content = trimUniformBlackBands(cropped);
                                    if (content.top != 0 || content.bottom != cropped.getHeight()) {
                                        focused = Bitmap.createBitmap(cropped, 0, content.top, cropped.getWidth(), content.height());
                                    } else {
                                        focused = cropped;
                                    }

                                    // El split 2P nativo es ancho (p.ej. 640x240). Antes se enviaba
                                    // así y el teléfono P2 lo estiraba después de comprimir, perdiendo
                                    // nitidez y haciendo visible la deformación. Normalizamos a 4:3
                                    // ANTES del JPEG, a la resolución completa de streaming.
                                    int targetW = b.getWidth();
                                    int targetH = Math.max(1, Math.round(targetW * 3f / 4f));
                                    if (targetH > b.getHeight()) {
                                        targetH = b.getHeight();
                                        targetW = Math.max(1, Math.round(targetH * 4f / 3f));
                                    }
                                    normalized = Bitmap.createScaledBitmap(focused, targetW, targetH, true);
                                    payload = normalized;
                                }
                                ByteArrayOutputStream baos = new ByteArrayOutputStream(crop ? 360000 : 320000);
                                // PLAYER prioriza claridad: un único JPEG de calidad alta después de
                                // normalizar el viewport. FULL mantiene el perfil de red elegido.
                                int q = crop
                                        ? Math.min(94, Math.max(90, RetroPreferences.streamQuality(context) + 8))
                                        : RetroPreferences.streamQuality(context);
                                payload.compress(Bitmap.CompressFormat.JPEG, q, baos);
                                frame = baos.toByteArray();
                                encoded.put(key, frame);
                            } finally {
                                if (normalized != null && normalized != focused && normalized != cropped && normalized != b && !normalized.isRecycled()) normalized.recycle();
                                if (focused != null && focused != cropped && focused != b && !focused.isRecycled()) focused.recycle();
                                if (cropped != null && cropped != b && !cropped.isRecycled()) cropped.recycle();
                            }
                        }
                        c.offer(frame, pc, crop, SessionState.isDk64Profile());
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (b != null && !b.isRecycled()) b.recycle();
            }

            long work = System.currentTimeMillis() - started;
            encodeWorkMsEma = encodeWorkMsEma == 0 ? work : (encodeWorkMsEma * 0.88 + work * 0.12);
            long wait = framePeriod - work;
            if (wait > 0) try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Recorta únicamente bandas horizontales casi negras pegadas al borde.
     * v0.6.7 hace la detección algo más tolerante antes del JPEG para reducir
     * letterbox residual; el cliente vuelve a verificar después de decodificar.
     */
    private static Rect trimUniformBlackBands(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return new Rect(0, 0, 1, 1);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w < 32 || h < 32) return new Rect(0, 0, w, h);
        int maxTrim = Math.max(1, Math.round(h * 0.26f));
        int top = 0;
        int bottom = h;
        while (top < maxTrim && rowMostlyBlack(bitmap, top)) top++;
        while (bottom > h - maxTrim && bottom - 1 > top && rowMostlyBlack(bitmap, bottom - 1)) bottom--;
        if (bottom - top < Math.round(h * 0.66f)) return new Rect(0, 0, w, h);
        return new Rect(0, top, w, bottom);
    }

    private static boolean rowMostlyBlack(Bitmap bitmap, int y) {
        int w = bitmap.getWidth();
        int samples = Math.min(48, Math.max(16, w / 12));
        int dark = 0;
        for (int i = 0; i < samples; i++) {
            int x = Math.min(w - 1, Math.round((w - 1) * (i + 0.5f) / samples));
            int color = bitmap.getPixel(x, y);
            int r = (color >> 16) & 0xff;
            int g = (color >> 8) & 0xff;
            int b = color & 0xff;
            if (Math.max(r, Math.max(g, b)) <= 28 && (r + g + b) <= 62) dark++;
        }
        return dark >= Math.ceil(samples * 0.92);
    }

    private class Client {
        final Socket socket;
        final AtomicReference<Packet> latest = new AtomicReference<>();
        final AtomicBoolean alive = new AtomicBoolean(true);
        volatile int player;
        volatile boolean playerView = true;
        Thread writer, reader;
        Client(Socket s) { socket = s; }
        void start() {
            reader = new Thread(this::controlLoop, "RetroLinkClientControl");
            writer = new Thread(this::writeLoop, "RetroLinkClientWriterLL");
            reader.start(); writer.start();
        }
        void offer(byte[] b, int pc, boolean preCropped, boolean dk64Raw) {
            int flags = (preCropped ? StreamProtocol.FLAG_PRE_CROPPED : 0)
                    | (dk64Raw ? StreamProtocol.FLAG_DK64_RAW : 0);
            latest.set(new Packet(b, pc, flags));
            synchronized (latest) { latest.notifyAll(); }
        }
        void controlLoop() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
                int magic = in.readInt();
                if (magic != StreamProtocol.HELLO) throw new IllegalStateException("Cliente incompatible");
                player = Math.max(1, Math.min(4, in.readInt()));
                playerView = in.readInt() != 0;
                while (running.get() && alive.get()) {
                    int cmd = in.readInt();
                    if (cmd == StreamProtocol.CMD_VIEW) playerView = in.readInt() != 0;
                }
            } catch (Exception ignored) {
            } finally {
                close(); clients.remove(this); count();
            }
        }
        void writeLoop() {
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                while (running.get() && alive.get()) {
                    Packet p = latest.getAndSet(null);
                    if (p == null) { synchronized (latest) { latest.wait(180); } continue; }
                    out.writeInt(StreamProtocol.FRAME);
                    out.writeInt(p.flags);
                    out.writeInt(p.players);
                    out.writeInt(p.bytes.length);
                    out.write(p.bytes);
                    out.flush();
                }
            } catch (Exception ignored) {
            } finally {
                close(); clients.remove(this); count();
            }
        }
        void close() { alive.set(false); try { socket.close(); } catch (Exception ignored) {} }
    }

    private static class Packet {
        final byte[] bytes; final int players; final int flags;
        Packet(byte[] b, int p, int f) { bytes = b; players = p; flags = f; }
    }
    private void status(String s) { if (listener != null) main.post(() -> listener.onVideoServerStatus(s)); }
    private void count() { int n = clients.size(); ACTIVE_CLIENTS.set(n); if (listener != null) main.post(() -> listener.onClientCount(n)); }
}
