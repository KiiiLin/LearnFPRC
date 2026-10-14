package io.github.autotaskrun;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

final class AccessibilityGrant {
    private AccessibilityGrant() {}

    static boolean enabled(Context context) {
        ComponentName expected = new ComponentName(context, AutoService.class);
        try {
            String services = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services != null) {
                for (String service : services.split(":")) {
                    if (expected.equals(ComponentName.unflattenFromString(service))) return true;
                }
                return false;
            }
        } catch (SecurityException ignored) {
            // Consult the system's list if a vendor restricts the settings read.
        }
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        if (manager != null) {
            for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                    android.content.pm.ServiceInfo service = info.getResolveInfo().serviceInfo;
                    if (expected.equals(new ComponentName(service.packageName, service.name))) return true;
                }
            }
        }
        return false;
    }
}
