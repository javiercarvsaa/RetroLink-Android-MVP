package cl.retrolink.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Canal de sesión RetroLink para Game Boy / Game Boy Color.
 *
 * BLE sólo descubre P1 y entrega IP + token.
 * Este canal TCP transfiere la ROM de P1 a P2, verifica SHA-256 y mantiene
 * a P2 esperando hasta que el core Gambatte de P1 confirme que está listo.
 *
 * El Game Link real sigue usando el puerto 56400 dentro de Gambatte.
 */
public final class GameBoyRomTransfer {
    public static final int PORT = 56399;
    private static final String MAGIC = "RETROLINK_GBLINK_ROM_V1";
    private static final String META = "META_V1";
    private static final long MAX_ROM_BYTES = 32L * 1024L * 1024L;

    public static final class SessionRom {
        public final File file;
        public final String title;
        public final String systemLabel;
        public final String sha256;
        public final long size;

        SessionRom(File file, String title, String systemLabel, String sha256, long size) {
            this.file = file;
            this.title = title == null ? "Game Boy" : title;
            this.systemLabel = systemLabel == null ? "Game Boy / Game Boy Color" : systemLabel;
            this.sha256 = sha256 == null ? "" : sha256;
            this.size = size;
        }
    }

    public interface Listener {
        void onTransferStatus(String status);
        void onHostRomAccepted();
        void onClientRomReady(SessionRom rom);
        void onClientStart();
        void onTransferError(String error);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean stopped;
    private volatile String hostCoreError = "";
    private volatile CountDownLatch hostCoreReady = new CountDownLatch(1);
    private Thread worker;
    private ServerSocket serverSocket;
    private Socket socket;

    public GameBoyRomTransfer(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void startHost(File rom, String title, String systemLabel,
                                       Listener listener) {
        stop();
        stopped = false;
        hostCoreError = "";
        hostCoreReady = new CountDownLatch(1);
        worker = new Thread(() -> runHost(rom, title, systemLabel, listener),
                "RetroLinkGbRomHost");
        worker.start();
    }

    public synchronized void startClient(String hostIp, Listener listener) {
        stop();
        stopped = false;
        worker = new Thread(() -> runClient(hostIp, listener),
                "RetroLinkGbRomClient");
        worker.start();
    }

    public void signalHostCoreReady() {
        hostCoreError = "";
        hostCoreReady.countDown();
    }

    public void signalHostCoreError(String error) {
        hostCoreError = error == null ? "No se pudo iniciar Gambatte en P1" : error.trim();
        hostCoreReady.countDown();
    }

    public synchronized void stop() {
        stopped = true;
        closeSockets();
        Thread t = worker;
        worker = null;
        if (t != null) t.interrupt();
    }

    private void runHost(File rom, String title, String systemLabel,
                         Listener listener) {
        try {
            validateRom(rom);
            final long size = rom.length();
            final String digest = sha256(rom);
            final String ext = extension(rom);

            postStatus(listener, "Sala P1 preparada · esperando Player 2…");

            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
            server.setSoTimeout(30000);
            serverSocket = server;

            Socket peer = server.accept();
            socket = peer;
            peer.setSoTimeout(30000);
            peer.setTcpNoDelay(true);

            DataInputStream in = new DataInputStream(new BufferedInputStream(peer.getInputStream(), 64 * 1024));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(peer.getOutputStream(), 64 * 1024));

            String hello = in.readUTF();
            if (!MAGIC.equals(hello))
                throw new IllegalStateException("P2 usa un protocolo de sala incompatible");

            out.writeUTF(META);
            out.writeUTF(safeText(title, "Game Boy"));
            out.writeUTF(safeText(systemLabel, "Game Boy / Game Boy Color"));
            out.writeUTF(ext);
            out.writeLong(size);
            out.writeUTF(digest);
            out.flush();

            String request = in.readUTF();
            if (("HAVE|" + digest).equals(request)) {
                postStatus(listener, "P2 ya posee esta ROM en caché · verificando…");
            } else if ("SEND".equals(request)) {
                postStatus(listener, "Enviando ROM de P1 a P2 · 0%");
                sendFile(rom, out, size, listener);
                out.flush();
            } else {
                throw new IllegalStateException("P2 respondió con una solicitud de ROM inválida");
            }

            String ready = in.readUTF();
            if (!("READY|" + digest).equals(ready))
                throw new IllegalStateException("P2 no pudo verificar la ROM recibida");

            postStatus(listener, "✓ ROM sincronizada y verificada en P2");
            postHostAccepted(listener);

            if (!hostCoreReady.await(20, TimeUnit.SECONDS))
                throw new IllegalStateException("P1 no alcanzó a preparar Game Link");
            if (!hostCoreError.isEmpty()) {
                out.writeUTF("ERROR|" + hostCoreError);
                out.flush();
                throw new IllegalStateException(hostCoreError);
            }

            out.writeUTF("GO");
            out.flush();
            postStatus(listener, "✓ P1 listo · autorizando inicio de P2");
        } catch (Throwable t) {
            if (!stopped) postError(listener, friendly(t));
        } finally {
            closeSockets();
        }
    }

    private void runClient(String hostIp, Listener listener) {
        File tmp = null;
        try {
            String ip = hostIp == null ? "" : hostIp.trim();
            if (ip.isEmpty()) throw new IllegalArgumentException("No se recibió la IP de P1");

            postStatus(listener, "Conectando con P1 para recibir el juego…");

            Socket peer = connectWithRetry(ip, PORT, 18000);
            socket = peer;
            peer.setSoTimeout(30000);
            peer.setTcpNoDelay(true);

            DataInputStream in = new DataInputStream(new BufferedInputStream(peer.getInputStream(), 64 * 1024));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(peer.getOutputStream(), 64 * 1024));

            out.writeUTF(MAGIC);
            out.flush();

            if (!META.equals(in.readUTF()))
                throw new IllegalStateException("P1 envió metadatos incompatibles");

            String title = in.readUTF();
            String systemLabel = in.readUTF();
            String ext = sanitizeExtension(in.readUTF());
            long size = in.readLong();
            String expectedSha = in.readUTF().trim().toLowerCase(Locale.US);

            if (size < 0x150 || size > MAX_ROM_BYTES)
                throw new IllegalStateException("Tamaño de ROM inválido: " + size);
            if (expectedSha.length() != 64)
                throw new IllegalStateException("SHA-256 de P1 inválido");

            File cacheDir = new File(context.getFilesDir(), "roms/gameboy/link_cache");
            if (!cacheDir.exists() && !cacheDir.mkdirs())
                throw new IllegalStateException("No se pudo crear la caché de Game Link");

            File cached = new File(cacheDir, expectedSha + ext);
            boolean cacheHit = cached.isFile()
                    && cached.length() == size
                    && expectedSha.equalsIgnoreCase(sha256(cached));

            if (cacheHit) {
                out.writeUTF("HAVE|" + expectedSha);
                out.flush();
                postStatus(listener, "ROM encontrada en caché · SHA-256 correcto");
            } else {
                out.writeUTF("SEND");
                out.flush();

                tmp = new File(cacheDir, expectedSha + ".part");
                postStatus(listener, "Recibiendo juego desde P1 · 0%");
                receiveFile(in, tmp, size, listener);

                String actual = sha256(tmp);
                if (!expectedSha.equalsIgnoreCase(actual))
                    throw new IllegalStateException("La ROM recibida no superó la verificación SHA-256");

                if (cached.exists() && !cached.delete())
                    throw new IllegalStateException("No se pudo reemplazar la ROM temporal");
                if (!tmp.renameTo(cached)) {
                    copyFile(tmp, cached);
                    if (!tmp.delete()) tmp.deleteOnExit();
                }
                tmp = null;
            }

            if (!cached.isFile() || cached.length() != size
                    || !expectedSha.equalsIgnoreCase(sha256(cached)))
                throw new IllegalStateException("La ROM local de P2 no coincide con P1");

            SessionRom sessionRom = new SessionRom(cached, title, systemLabel, expectedSha, size);

            out.writeUTF("READY|" + expectedSha);
            out.flush();
            postClientRomReady(listener, sessionRom);
            postStatus(listener, "✓ Juego recibido · esperando que P1 abra Game Link…");

            String command = in.readUTF();
            if (command.startsWith("ERROR|"))
                throw new IllegalStateException(command.substring(6));
            if (!"GO".equals(command))
                throw new IllegalStateException("P1 no autorizó el inicio de Game Link");

            postStatus(listener, "✓ P1 listo · iniciando P2");
            postClientStart(listener);
        } catch (EOFException eof) {
            if (!stopped) postError(listener, "P1 cerró la sesión antes de completar el enlace");
        } catch (Throwable t) {
            if (!stopped) postError(listener, friendly(t));
        } finally {
            if (tmp != null && tmp.exists()) tmp.delete();
            closeSockets();
        }
    }

    private Socket connectWithRetry(String ip, int port, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Throwable last = null;
        while (!stopped && System.currentTimeMillis() < deadline) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(ip, port), 1400);
                return s;
            } catch (Throwable t) {
                last = t;
                try { s.close(); } catch (Exception ignored) {}
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
        }
        String suffix = last == null ? "" : " · " + last.getClass().getSimpleName();
        throw new IllegalStateException("No se pudo conectar con P1 " + ip + ":" + port + suffix);
    }

    private void sendFile(File rom, DataOutputStream out, long total, Listener listener) throws Exception {
        try (FileInputStream fin = new FileInputStream(rom)) {
            byte[] buffer = new byte[64 * 1024];
            long sent = 0;
            int lastPercent = -10;
            int n;
            while ((n = fin.read(buffer)) > 0) {
                if (stopped) throw new InterruptedException("Sesión detenida");
                out.write(buffer, 0, n);
                sent += n;
                int percent = (int)Math.min(100, (sent * 100L) / Math.max(1L, total));
                if (percent >= lastPercent + 10 || percent == 100) {
                    lastPercent = percent;
                    postStatus(listener, "Enviando ROM de P1 a P2 · " + percent + "%");
                }
            }
        }
    }

    private void receiveFile(DataInputStream in, File dst, long total, Listener listener) throws Exception {
        try (FileOutputStream fout = new FileOutputStream(dst, false)) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = total;
            long received = 0;
            int lastPercent = -10;
            while (remaining > 0) {
                if (stopped) throw new InterruptedException("Sesión detenida");
                int n = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (n < 0) throw new EOFException("Transferencia de ROM incompleta");
                fout.write(buffer, 0, n);
                remaining -= n;
                received += n;
                int percent = (int)Math.min(100, (received * 100L) / Math.max(1L, total));
                if (percent >= lastPercent + 10 || percent == 100) {
                    lastPercent = percent;
                    postStatus(listener, "Recibiendo juego desde P1 · " + percent + "%");
                }
            }
            fout.getFD().sync();
        }
    }

    private void validateRom(File rom) {
        if (rom == null || !rom.isFile()) throw new IllegalArgumentException("P1 no tiene una ROM seleccionada");
        long size = rom.length();
        if (size < 0x150 || size > MAX_ROM_BYTES)
            throw new IllegalArgumentException("ROM Game Boy inválida o demasiado grande");
        String n = rom.getName().toLowerCase(Locale.US);
        if (!n.endsWith(".gb") && !n.endsWith(".gbc"))
            throw new IllegalArgumentException("El juego de P1 no es .gb/.gbc");
    }

    private static String extension(File file) {
        return file.getName().toLowerCase(Locale.US).endsWith(".gbc") ? ".gbc" : ".gb";
    }

    private static String sanitizeExtension(String ext) {
        return ".gbc".equalsIgnoreCase(ext) ? ".gbc" : ".gb";
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String clean = value.trim();
        return clean.length() > 96 ? clean.substring(0, 96) : clean;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) md.update(buffer, 0, n);
        }
        StringBuilder sb = new StringBuilder(64);
        for (byte b : md.digest()) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    private static void copyFile(File from, File to) throws Exception {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to, false)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            out.getFD().sync();
        }
    }

    private synchronized void closeSockets() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        socket = null;
        serverSocket = null;
    }

    private void postStatus(Listener l, String s) {
        if (l != null) main.post(() -> l.onTransferStatus(s));
    }

    private void postHostAccepted(Listener l) {
        if (l != null) main.post(l::onHostRomAccepted);
    }

    private void postClientRomReady(Listener l, SessionRom rom) {
        if (l != null) main.post(() -> l.onClientRomReady(rom));
    }

    private void postClientStart(Listener l) {
        if (l != null) main.post(l::onClientStart);
    }

    private void postError(Listener l, String s) {
        if (l != null) main.post(() -> l.onTransferError(s));
    }

    private static String friendly(Throwable t) {
        if (t == null) return "Error desconocido";
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) return t.getClass().getSimpleName();
        return m.trim();
    }
}
