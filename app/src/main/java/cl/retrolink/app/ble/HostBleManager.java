package cl.retrolink.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import cl.retrolink.app.NetworkUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class HostBleManager {
    public interface Listener {
        void onStatus(String status);
        void onPlayerConnected(int player, String deviceLabel);
        void onPlayerDisconnected(int player);
        void onInput(int player, int mask, int axisX, int axisY);
        void onLog(String line);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothManager btManager;
    private final BluetoothAdapter adapter;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private final Map<BluetoothDevice, Integer> players = Collections.synchronizedMap(new LinkedHashMap<>());
    private boolean advertising;
    private int maxPlayerNumber = 4;
    private String sessionType = "N64";

    public HostBleManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = btManager != null ? btManager.getAdapter() : null;
    }

    public void setMaxPlayerNumber(int maxPlayerNumber) {
        this.maxPlayerNumber = Math.max(2, Math.min(4, maxPlayerNumber));
    }

    public void setSessionType(String sessionType) {
        String value = sessionType == null ? "" : sessionType.trim().toUpperCase(java.util.Locale.US);
        this.sessionType = "GBLINK".equals(value) ? "GBLINK" : "N64";
    }

    @SuppressLint("MissingPermission")
    public void start() {
        stop();
        if (adapter == null || !adapter.isEnabled()) { status("Bluetooth no está disponible o está apagado."); return; }
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) { status("Este teléfono no soporta publicidad BLE como periférico."); return; }
        gattServer = btManager.openGattServer(context, callback);
        if (gattServer == null) { status("No se pudo abrir el servidor GATT."); return; }

        BluetoothGattService service = new BluetoothGattService(BleProtocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(new BluetoothGattCharacteristic(BleProtocol.INPUT_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(BleProtocol.PLAYER_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(BleProtocol.HOST_INFO_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
        if (!gattServer.addService(service)) status("No se pudo registrar el servicio RetroLink."); else status("Preparando Host…");
    }

    @SuppressLint("MissingPermission")
    private void beginAdvertising() {
        if (advertiser == null || advertising) return;
        AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).setConnectable(true).setTimeout(0).build();
        AdvertiseData data = new AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(new ParcelUuid(BleProtocol.SERVICE_UUID)).build();
        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        try { if (advertiser != null && advertising) advertiser.stopAdvertising(advertiseCallback); } catch (Exception ignored) {}
        advertising = false;
        try { if (gattServer != null) gattServer.close(); } catch (Exception ignored) {}
        gattServer = null; advertiser = null;
        int[] oldPlayers;
        synchronized (players) { oldPlayers = players.values().stream().mapToInt(Integer::intValue).toArray(); players.clear(); }
        for (int p : oldPlayers) main.post(() -> listener.onPlayerDisconnected(p));
        status("Host detenido.");
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) { advertising = true; status("GBLINK".equals(sessionType) ? "Host activo · Game Boy Link P1" : "Host activo · BLE + pantalla distribuida"); log("Publicidad BLE iniciada."); }
        @Override public void onStartFailure(int errorCode) { advertising = false; status("Error al anunciar Host BLE (" + errorCode + ")."); log("Advertise failure=" + errorCode); }
    };

    private final BluetoothGattServerCallback callback = new BluetoothGattServerCallback() {
        @Override @SuppressLint("MissingPermission") public void onServiceAdded(int status, BluetoothGattService service) { if (status == BluetoothGatt.GATT_SUCCESS) { log("Servicio RetroLink v0.2 registrado."); beginAdvertising(); } else status("Falló el registro GATT: " + status); }
        @Override @SuppressLint("MissingPermission") public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                int player = allocatePlayer(device); if (player == 0) { if (gattServer != null) gattServer.cancelConnection(device); return; }
                String label = safeDeviceLabel(device); main.post(() -> listener.onPlayerConnected(player, label)); log("Conectado " + label + " → P" + player);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Integer player = players.remove(device); if (player != null) { main.post(() -> listener.onPlayerDisconnected(player)); log("P" + player + " desconectado."); }
            }
        }
        @Override @SuppressLint("MissingPermission") public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            byte[] value;
            if (BleProtocol.PLAYER_UUID.equals(characteristic.getUuid())) {
                Integer player = players.get(device); value = new byte[]{(byte)(player == null ? 0 : player)};
            } else if (BleProtocol.HOST_INFO_UUID.equals(characteristic.getUuid())) {
                String ip = NetworkUtils.localIpv4();
                String payload = sessionType + "|" + ip;
                value = payload.getBytes(StandardCharsets.UTF_8);
            } else { if (gattServer != null) gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null); return; }
            if (gattServer != null) gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
        }
        @Override @SuppressLint("MissingPermission") public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            int result = BluetoothGatt.GATT_SUCCESS;
            if (BleProtocol.INPUT_UUID.equals(characteristic.getUuid()) && value != null && value.length >= 3) {
                Integer player = players.get(device); if (player != null) {
                    int mask=BleProtocol.maskFromPacket(value), x=BleProtocol.axisXFromPacket(value), y=BleProtocol.axisYFromPacket(value);
                    main.post(() -> listener.onInput(player, mask, x, y));
                }
            } else result = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
            if (responseNeeded && gattServer != null) gattServer.sendResponse(device, requestId, result, 0, null);
        }
    };

    private synchronized int allocatePlayer(BluetoothDevice device) { Integer existing=players.get(device); if(existing!=null)return existing; for(int p=2;p<=maxPlayerNumber;p++) if(!players.containsValue(p)){players.put(device,p);return p;} return 0; }
    @SuppressLint("MissingPermission") private String safeDeviceLabel(BluetoothDevice device) { try{String n=device.getName(); if(n!=null&&!n.trim().isEmpty())return n;}catch(Exception ignored){} String a=device.getAddress(); return a==null?"Android":"Android …"+a.substring(Math.max(0,a.length()-5)); }
    private void status(String s){main.post(()->listener.onStatus(s));} private void log(String s){main.post(()->listener.onLog(s));}
}
