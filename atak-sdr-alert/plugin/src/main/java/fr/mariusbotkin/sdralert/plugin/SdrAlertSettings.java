package fr.mariusbotkin.sdralert.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.engine.MonitorConfig;

/** Réglages persistants (préférences ATAK, clés préfixées). */
public final class SdrAlertSettings {
    private static final String P = "sdralert_";
    private final SharedPreferences prefs;

    public boolean sound = true;
    public boolean vibrate = true;
    public boolean cotMarker = false;
    public boolean shareCot = false;

    public SdrAlertSettings(Context hostContext) {
        prefs = PreferenceManager.getDefaultSharedPreferences(hostContext);
    }

    public void load(MonitorConfig cfg) {
        try {
            cfg.band = Bands.byId(prefs.getString(P + "band", "pmr446"));
        } catch (IllegalArgumentException e) {
            cfg.band = Bands.pmr446();
        }
        cfg.detector.thresholdDb = prefs.getFloat(P + "threshold", 10f);
        cfg.detector.minDurationMs = prefs.getLong(P + "min_duration", 300);
        cfg.detector.cooldownMs = prefs.getLong(P + "cooldown", 30_000);
        cfg.gainTenthsDb = prefs.getInt(P + "gain", 300);
        cfg.ppm = prefs.getInt(P + "ppm", 0);
        sound = prefs.getBoolean(P + "sound", true);
        vibrate = prefs.getBoolean(P + "vibrate", true);
        cotMarker = prefs.getBoolean(P + "cot", false);
        shareCot = prefs.getBoolean(P + "share_cot", false);
    }

    public void save(MonitorConfig cfg) {
        prefs.edit()
                .putString(P + "band", cfg.band.id)
                .putFloat(P + "threshold", cfg.detector.thresholdDb)
                .putLong(P + "min_duration", cfg.detector.minDurationMs)
                .putLong(P + "cooldown", cfg.detector.cooldownMs)
                .putInt(P + "gain", cfg.gainTenthsDb)
                .putInt(P + "ppm", cfg.ppm)
                .putBoolean(P + "sound", sound)
                .putBoolean(P + "vibrate", vibrate)
                .putBoolean(P + "cot", cotMarker)
                .putBoolean(P + "share_cot", shareCot)
                .apply();
    }
}
