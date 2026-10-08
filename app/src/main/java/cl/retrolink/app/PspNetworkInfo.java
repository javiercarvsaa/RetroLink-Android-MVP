package cl.retrolink.app;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public final class PspNetworkInfo {
    private PspNetworkInfo() {}

    public static String localIpv4(Context c) {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && address.isSiteLocalAddress()) return address.getHostAddress();
                }
            }
        } catch (Throwable ignored) {}
        try {
            WifiManager wifi = (WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
            int ip = info == null ? 0 : info.getIpAddress();
            if (ip != 0) return String.format(java.util.Locale.US, "%d.%d.%d.%d",
                    ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
        } catch (Throwable ignored) {}
        return "No detectada";
    }

    public static boolean isValidIpv4(String value) {
        if (value == null) return false;
        String[] parts = value.trim().split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String p : parts) {
                if (p.isEmpty() || p.length() > 3) return false;
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return false;
            }
            return !"0.0.0.0".equals(value.trim()) && !"255.255.255.255".equals(value.trim());
        } catch (NumberFormatException e) { return false; }
    }
}
