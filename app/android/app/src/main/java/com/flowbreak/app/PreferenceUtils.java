package com.flowbreak.app;

import android.content.SharedPreferences;
import android.util.Log;
import java.util.HashSet;
import java.util.Set;

public class PreferenceUtils {
    private static final String TAG = "PreferenceUtils";
    public static final String PREF_TARGET_APPS = "targetApps";

    /**
     * Reads targetApps from SharedPreferences with automatic migration from legacy String format to StringSet.
     */
    public static Set<String> getMigratedTargetApps(SharedPreferences prefs) {
        Set<String> appSet = null;
        try {
            appSet = prefs.getStringSet(PREF_TARGET_APPS, null);
        } catch (ClassCastException e) {
            Log.d(TAG, "targetApps is not a StringSet, trying legacy String format");
        }

        if (appSet != null) {
            return appSet;
        }

        // Try legacy String format and migrate
        String appsStr = "";
        try {
            appsStr = prefs.getString(PREF_TARGET_APPS, "");
        } catch (ClassCastException e) {
            // Should not happen if it wasn't a StringSet
        }

        if (!appsStr.isEmpty()) {
            try {
                // Parse JSON array like ["com.xx","com.yy"]
                String[] parts = appsStr.replace("[", "").replace("]", "").replace("\"", "").split(",");
                HashSet<String> migratedApps = new HashSet<>();
                for (String p : parts) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) migratedApps.add(trimmed);
                }
                if (!migratedApps.isEmpty()) {
                    // Migrate to new format
                    prefs.edit().putStringSet(PREF_TARGET_APPS, migratedApps).apply();
                    Log.i(TAG, "Migrated targetApps from legacy String to StringSet");
                    return migratedApps;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing legacy targetApps string", e);
            }
        }

        return new HashSet<>();
    }
}
