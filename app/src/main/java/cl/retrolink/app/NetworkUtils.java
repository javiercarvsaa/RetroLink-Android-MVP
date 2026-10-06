package cl.retrolink.app;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public final class NetworkUtils {
    private NetworkUtils() {}

    public static String localIpv4() {
        String bestIp = "";
        int bestScore = Integer.MIN_VALUE;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName() == null ? "" : ni.getName().toLowerCase();
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) continue;
                    String ip = addr.getHostAddress();
                    if (ip == null || ip.startsWith("169.254.")) continue;
                    int score = score(name, ip);
                    if (score > bestScore) {
                        bestScore = score;
                        bestIp = ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return bestIp;
    }

    private static int score(String name, String ip) {
        int s = 0;
        if (name.contains("wlan") || name.contains("wifi") || name.contains("swlan")) s += 120;
        if (name.contains("ap") || name.contains("softap") || name.contains("hotspot")) s += 110;
        if (name.contains("p2p")) s += 90;
        if (name.contains("eth")) s += 50;
        if (name.contains("rmnet") || name.contains("ccmni") || name.contains("pdp") || name.contains("wwan")) s -= 150;
        if (name.contains("tun") || name.contains("vpn")) s -= 120;
        if (ip.startsWith("192.168.")) s += 60;
        else if (ip.startsWith("10.")) s += 50;
        else if (is172Private(ip)) s += 50;
        else s -= 20;
        return s;
    }

    private static boolean is172Private(String ip) {
        if (!ip.startsWith("172.")) return false;
        String[] parts = ip.split("\\.");
        if (parts.length < 2) return false;
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
        }
    }
}
