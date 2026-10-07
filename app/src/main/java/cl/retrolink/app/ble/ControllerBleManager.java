package cl.retrolink.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;

public class ControllerBleManager {
    public interface Listener {
        void onStatus(String status);
        void onConnected(int player);
        default void onSessionInfo(String sessionType, String ip) {}
        void onHostInfo(String ip);
        void onDisconnected();
        void onLog(String line);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothDevice hostDevice;
    private BluetoothGattCharacteristic inputCharacteristic, playerCharacteristic, hostInfoCharacteristic;

    private boolean scanning;
    private boolean connecting;
    private boolean writeInFlight;
    private boolean hostInfoReadInFlight;
    private boolean hostInfoNeeded;
    private boolean hostInfoReady;
    private String activeSessionType = "N64";
    private int sequence;
    private InputState inFlightState;
    private final ArrayDeque<InputState> queue = new ArrayDeque<>();

    private final Runnable scanTimeout = () -> {
        if (scanning && !connecting) {
            stopScan();
            status("No se encontró un Host RetroLink.");
        }
    };

    private final Runnable retryHostInfo = this::requestHostInfo;

    private final Runnable inputWriteWatchdog = () -> {
        synchronized (ControllerBleManager.this) {
            if (!writeInFlight) return;
            writeInFlight = false;
            inFlightState = null;
        }
        log("BLE input watchdog: liberando escritura sin callback.");
        pumpOperations();
    };

    public ControllerBleManager(Context c, Listener l) {
        context = c.getApplicationContext();
        listener = l;
        BluetoothManager m = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = m != null ? m.getAdapter() : null;
    }

    @SuppressLint("MissingPermission")
    public void startScan() {
        disconnect();
        if (adapter == null || !adapter.isEnabled()) {
            status("Bluetooth apagado.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            status("Escáner BLE no disponible.");
            return;
        }
        ScanFilter f = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BleProtocol.SERVICE_UUID)).build();
        ScanSettings s = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanning = true;
        connecting = false;
        status("Buscando Host RetroLink…");
        scanner.startScan(Collections.singletonList(f), s, scanCallback);
        main.postDelayed(scanTimeout, 12000);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        main.removeCallbacks(scanTimeout);
        if (scanner != null && scanning) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        }
        scanning = false;
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        stopScan();
        main.removeCallbacks(retryHostInfo);
        main.removeCallbacks(inputWriteWatchdog);
        inputCharacteristic = playerCharacteristic = hostInfoCharacteristic = null;
        hostDevice = null;
        connecting = false;
        synchronized (this) {
            queue.clear();
            writeInFlight = false;
            inFlightState = null;
            hostInfoReadInFlight = false;
            hostInfoNeeded = false;
            hostInfoReady = false;
            activeSessionType = "N64";
        }
        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) {}
            try { gatt.close(); } catch (Exception ignored) {}
            gatt = null;
        }
    }

    public synchronized boolean sendInput(int mask, int x, int y) {
        if (gatt == null || inputCharacteristic == null) return false;
        InputState st = new InputState(mask, x, y);
        if (inFlightState != null && inFlightState.same(st) && queue.isEmpty()) return true;
        InputState pending = queue.peekLast();
        if (pending != null && pending.same(st)) return true;
        // Inputs de juego no deben formar una cola histórica: conservamos solo el estado más nuevo.
        queue.clear();
        queue.addLast(st);
        pumpOperations();
        return true;
    }

    public synchronized void requestHostInfo() {
        if (gatt == null || hostInfoCharacteristic == null) return;
        hostInfoNeeded = true;
        pumpOperations();
    }

    @SuppressLint("MissingPermission")
    private synchronized void pumpOperations() {
        if (gatt == null) return;
        if (writeInFlight || hostInfoReadInFlight) return;

        if (hostInfoNeeded && hostInfoCharacteristic != null) {
            hostInfoNeeded = false;
            boolean started;
            try { started = gatt.readCharacteristic(hostInfoCharacteristic); }
            catch (Exception e) { started = false; }
            if (started) {
                hostInfoReadInFlight = true;
                log("Solicitando IP del Host por BLE…");
            } else {
                hostInfoNeeded = true;
                main.removeCallbacks(retryHostInfo);
                main.postDelayed(retryHostInfo, 500);
            }
            return;
        }

        if (!hostInfoReady || queue.isEmpty() || inputCharacteristic == null) return;

        InputState st = queue.removeFirst();
        byte[] v = BleProtocol.packet(sequence++, st.mask, st.x, st.y);

        boolean reliable = "ATARI2600".equals(activeSessionType);
        int writeType = reliable
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

        boolean started;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            started = gatt.writeCharacteristic(inputCharacteristic, v, writeType) == 0;
        } else {
            inputCharacteristic.setWriteType(writeType);
            inputCharacteristic.setValue(v);
            started = gatt.writeCharacteristic(inputCharacteristic);
        }

        if (started) {
            writeInFlight = true;
            inFlightState = st;
            main.removeCallbacks(inputWriteWatchdog);
            main.postDelayed(inputWriteWatchdog, reliable ? 250 : 90);
        } else {
            if (queue.isEmpty()) queue.addFirst(st);
            main.postDelayed(this::retryOperations, 6);
        }
    }

    private synchronized void retryOperations() {
        if (!writeInFlight && !hostInfoReadInFlight) pumpOperations();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            if (connecting) return;
            connecting = true;
            stopScan();
            status("Host encontrado. Conectando…");
            hostDevice = result.getDevice();
            gatt = hostDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        }

        @Override public void onScanFailed(int errorCode) {
            scanning = false;
            connecting = false;
            status("Falló escaneo BLE (" + errorCode + ").");
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                status("Conectado. Detectando servicio…");
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                inputCharacteristic = playerCharacteristic = hostInfoCharacteristic = null;
                connecting = false;
                synchronized (ControllerBleManager.this) {
                    queue.clear();
                    writeInFlight = false;
                    inFlightState = null;
                    hostInfoReadInFlight = false;
                    hostInfoNeeded = false;
                    hostInfoReady = false;
                }
                main.removeCallbacks(retryHostInfo);
                main.removeCallbacks(inputWriteWatchdog);
                status("Desconectado del Host.");
                main.post(listener::onDisconnected);
                try { g.close(); } catch (Exception ignored) {}
                if (gatt == g) gatt = null;
            }
        }

        @Override @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                status("No se pudieron leer servicios.");
                return;
            }
            BluetoothGattService s = g.getService(BleProtocol.SERVICE_UUID);
            if (s == null) {
                status("El equipo no expone RetroLink.");
                return;
            }
            inputCharacteristic = s.getCharacteristic(BleProtocol.INPUT_UUID);
            playerCharacteristic = s.getCharacteristic(BleProtocol.PLAYER_UUID);
            hostInfoCharacteristic = s.getCharacteristic(BleProtocol.HOST_INFO_UUID);
            if (inputCharacteristic == null || playerCharacteristic == null || hostInfoCharacteristic == null) {
                status("Servicio RetroLink incompleto.");
                return;
            }
            if (!g.readCharacteristic(playerCharacteristic)) status("No se pudo solicitar el número de jugador.");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode) {
            main.removeCallbacks(inputWriteWatchdog);
            synchronized (ControllerBleManager.this) {
                writeInFlight = false;
                inFlightState = null;
            }
            pumpOperations();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int statusCode) {
            handleReadResult(g, c, value, statusCode);
        }

        @Override @SuppressWarnings("deprecation")
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleReadResult(g, c, c.getValue(), statusCode);
            }
        }
    };

    private void handleReadResult(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int statusCode) {
        if (BleProtocol.HOST_INFO_UUID.equals(c.getUuid())) {
            synchronized (this) { hostInfoReadInFlight = false; }
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                synchronized (this) { hostInfoNeeded = true; }
                main.removeCallbacks(retryHostInfo);
                main.postDelayed(retryHostInfo, 800);
                return;
            }
        } else if (statusCode != BluetoothGatt.GATT_SUCCESS) {
            return;
        }
        handleRead(g, c, value);
    }

    @SuppressLint("MissingPermission")
    private void handleRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
        if (BleProtocol.PLAYER_UUID.equals(c.getUuid())) {
            int p = value != null && value.length > 0 ? (value[0] & 0xff) : 0;
            if (p >= 1 && p <= 4) {
                connecting = false;
                main.post(() -> listener.onConnected(p));
                status("JUGADOR " + p + " · BLE LISTO");
                synchronized (this) {
                    hostInfoReady = false;
                    hostInfoNeeded = true;
                }
                main.postDelayed(this::requestHostInfo, 120);
            } else {
                status("El Host no pudo asignar jugador.");
            }
        } else if (BleProtocol.HOST_INFO_UUID.equals(c.getUuid())) {
            String raw = value == null ? "" : new String(value, StandardCharsets.UTF_8).trim();
            String sessionType = "N64";
            String ip = raw;

            int split = raw.indexOf('|');
            if (split > 0) {
                sessionType = raw.substring(0, split).trim().toUpperCase(java.util.Locale.US);
                ip = raw.substring(split + 1).trim();
            }

            ip = extractIpv4(ip);

            if (ip.isEmpty()) {
                synchronized (this) {
                    hostInfoReady = false;
                    hostInfoNeeded = true;
                }
                final String mode = sessionType;
                main.post(() -> {
                    listener.onSessionInfo(mode, "");
                    listener.onHostInfo("");
                });
                main.removeCallbacks(retryHostInfo);
                main.postDelayed(retryHostInfo, 1200);
            } else {
                synchronized (this) {
                    hostInfoReady = true;
                    hostInfoNeeded = false;
                    activeSessionType = sessionType;
                }
                final String mode = sessionType;
                final String hostIp = ip;
                log("Host " + mode + " recibido: " + hostIp);
                main.post(() -> {
                    listener.onSessionInfo(mode, hostIp);
                    listener.onHostInfo(hostIp);
                });
                if (!"GBLINK".equals(mode)) sendInput(0, 0, 0);
                pumpOperations();
            }
        }
    }


    private static String extractIpv4(String text) {
        if (text == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])")
                .matcher(text);
        while (m.find()) {
            String candidate = m.group();
            String[] parts = candidate.split("\\.");
            boolean ok = parts.length == 4;
            for (String part : parts) {
                try {
                    int v = Integer.parseInt(part);
                    if (v < 0 || v > 255) ok = false;
                } catch (Exception e) {
                    ok = false;
                }
            }
            if (ok) return candidate;
        }
        return "";
    }

    public BluetoothDevice getHostDevice() { return hostDevice; }
    private void status(String s) { main.post(() -> listener.onStatus(s)); }
    private void log(String s) { main.post(() -> listener.onLog(s)); }

    private static class InputState {
        final int mask, x, y;
        InputState(int m, int x, int y) {
            mask = m;
            this.x = Math.max(-127, Math.min(127, x));
            this.y = Math.max(-127, Math.min(127, y));
        }
        boolean same(InputState o) { return o != null && o.mask == mask && o.x == x && o.y == y; }
    }
}
