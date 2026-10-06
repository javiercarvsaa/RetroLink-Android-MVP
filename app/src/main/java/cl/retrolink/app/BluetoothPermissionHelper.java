package cl.retrolink.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public final class BluetoothPermissionHelper {
    private BluetoothPermissionHelper() {}

    public static String[] requiredPermissions() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return list.toArray(new String[0]);
    }

    public static boolean hasAll(Activity activity) {
        for (String permission : requiredPermissions()) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
