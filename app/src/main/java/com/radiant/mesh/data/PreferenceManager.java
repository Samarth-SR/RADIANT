package com.radiant.mesh.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages simple key-value storage for app settings.
 * Primarily used for configuring BLE Transmission Power.
 */
public class PreferenceManager {

    private static final String PREF_NAME = "RadiantConfig";
    private static final String KEY_LONG_RANGE = "long_range_mode";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Checks if Long Range Mode is enabled.
     * Default: True (Better for disaster scenarios).
     */
    public boolean isLongRangeMode() {
        return prefs.getBoolean(KEY_LONG_RANGE, true);
    }

    /**
     * Toggles the Long Range setting.
     */
    public void setLongRangeMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_LONG_RANGE, enabled).apply();
    }
}