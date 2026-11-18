package com.mihir.remap;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.KeyEvent;
import java.util.Map;
import java.util.TreeMap;

/** The saved button -> app mappings, shared by the settings screen and the service. */
final class Remaps {
    private Remaps() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("remaps", Context.MODE_PRIVATE);
    }

    /** Special targets that aren't apps. Stored as the mapping value. */
    static final String ALL_APPS = "action:all_apps";
    static final String HOME_PKG = "com.mihir.tvhome";

    /** First run: copy the defaults from res/values/config.xml. Later versions add new defaults once. */
    static void seed(Context c) {
        SharedPreferences p = prefs(c);
        int v = p.getInt("_seedv", p.getBoolean("_seeded", false) ? 1 : 0);
        SharedPreferences.Editor e = p.edit();
        if (v < 1) {
            for (String item : c.getResources().getStringArray(R.array.remaps)) {
                String[] kv = item.split("=", 2);
                if (kv.length != 2) continue;
                int code = KeyEvent.keyCodeFromString(kv[0].trim());
                if (code != KeyEvent.KEYCODE_UNKNOWN) e.putString(String.valueOf(code), kv[1].trim());
            }
        }
        if (v < 2 && !p.contains(String.valueOf(KeyEvent.KEYCODE_ALL_APPS))) {
            e.putString(String.valueOf(KeyEvent.KEYCODE_ALL_APPS), ALL_APPS);
        }
        e.putInt("_seedv", 2).apply();
    }

    /** What a mapping opens, ready to start, or null if its app is gone. */
    static android.content.Intent targetIntent(Context c, String target) {
        android.content.Intent i;
        if (ALL_APPS.equals(target)) {
            i = new android.content.Intent(android.content.Intent.ACTION_ALL_APPS)
                    .setComponent(new android.content.ComponentName(HOME_PKG, HOME_PKG + ".MainActivity"));
            if (c.getPackageManager().resolveActivity(i, 0) == null) return null;
        } else {
            PackageManager pm = c.getPackageManager();
            i = pm.getLeanbackLaunchIntentForPackage(target);
            if (i == null) i = pm.getLaunchIntentForPackage(target);
            if (i == null) return null;
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        return i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static String get(Context c, int code) {
        return prefs(c).getString(String.valueOf(code), null);
    }

    static void put(Context c, int code, String pkg) {
        prefs(c).edit().putString(String.valueOf(code), pkg).apply();
    }

    static void remove(Context c, int code) {
        prefs(c).edit().remove(String.valueOf(code)).apply();
    }

    static Map<Integer, String> all(Context c) {
        Map<Integer, String> m = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (e.getKey().startsWith("_") || !(e.getValue() instanceof String)) continue;
            try { m.put(Integer.parseInt(e.getKey()), (String) e.getValue()); } catch (NumberFormatException ignored) { }
        }
        return m;
    }

    /** Buttons that must keep working normally, or you couldn't navigate the TV. */
    static boolean isBlocked(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_UNKNOWN:
                return true;
            default:
                return false;
        }
    }

    /** Friendly names for the Xiaomi TV Box S remote (from Vendor_2717_Product_32b9.kl). */
    static String keyLabel(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_4: case KeyEvent.KEYCODE_BUTTON_5: return "Mi TV+ button";
            case KeyEvent.KEYCODE_BUTTON_3: return "YouTube button";
            case KeyEvent.KEYCODE_BUTTON_6: return "Netflix button";
            case KeyEvent.KEYCODE_BUTTON_7: return "Prime Video button";
            case KeyEvent.KEYCODE_ALL_APPS: return "All Apps button";
            // This remote's mic button sends Linux KEY_SEARCH (217), which its .kl file maps to PROG_BLUE.
            case KeyEvent.KEYCODE_PROG_BLUE:
            case KeyEvent.KEYCODE_ASSIST: case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_VOICE_ASSIST: return "Google voice search button";
            case KeyEvent.KEYCODE_SETTINGS: return "Settings button";
            case KeyEvent.KEYCODE_TV_INPUT: return "Input button";
            case KeyEvent.KEYCODE_GUIDE: return "Guide button";
            case KeyEvent.KEYCODE_MENU: return "Menu button";
            case KeyEvent.KEYCODE_INFO: return "Info button";
            case KeyEvent.KEYCODE_VOLUME_MUTE: return "Mute button";
            default:
                String s = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "").replace('_', ' ').toLowerCase();
                return Character.toUpperCase(s.charAt(0)) + s.substring(1) + " button";
        }
    }

    static String appLabel(Context c, String pkg) {
        if (ALL_APPS.equals(pkg)) return "All apps (home screen)";
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg + " (not installed)";
        }
    }

    static boolean serviceEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }
}
