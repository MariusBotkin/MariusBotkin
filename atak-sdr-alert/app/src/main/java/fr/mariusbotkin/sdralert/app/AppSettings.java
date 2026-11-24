package fr.mariusbotkin.sdralert.app;

import android.content.Context;
import android.content.SharedPreferences;

import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.engine.MonitorConfig;

/** Réglages persistants de l'application. */
public final class AppSettings {
    private final SharedPreferences prefs;

    public volatile boolean sound = true;
    public volatile boolean vibrate = true;
    /** Envoyer le marqueur à l'ATAK de ce téléphone (UDP 127.0.0.1:4242). */
    public volatile boolean cotLocal = true;
    /** Envoyer aussi le marqueur en multicast SA (ATAK du même réseau local). */
    public volatile boolean cotShare = false;

    public AppSettings(Context ctx) {
        prefs = ctx.getSharedPreferences("sdralert", Context.MODE_PRIVATE);
    }

    public void load(MonitorConfig cfg) {
        try {
            cfg.band = Bands.byId(prefs.getString("band", "pmr446"));
        } catch (IllegalArgumentException e) {
            cfg.band = Bands.pmr446();
        }
        cfg.detector.thresholdDb = prefs.getFloat("threshold", 10f);
        cfg.detector.minDurationMs = prefs.getLong("min_duration", 300);
        cfg.detector.cooldownMs = prefs.getLong("cooldown", 30_000);
        cfg.gainTenthsDb = prefs.getInt("gain", 300);
        cfg.ppm = prefs.getInt("ppm", 0);
        sound = prefs.getBoolean("sound", true);
        vibrate = prefs.getBoolean("vibrate", true);
        cotLocal = prefs.getBoolean("cot_local", true);
        cotShare = prefs.getBoolean("cot_share", false);
    }

    public void save(MonitorConfig cfg) {
        prefs.edit()
                .putString("band", cfg.band.id)
                .putFloat("threshold", cfg.detector.thresholdDb)
                .putLong("min_duration", cfg.detector.minDurationMs)
                .putLong("cooldown", cfg.detector.cooldownMs)
                .putInt("gain", cfg.gainTenthsDb)
                .putInt("ppm", cfg.ppm)
                .putBoolean("sound", sound)
                .putBoolean("vibrate", vibrate)
                .putBoolean("cot_local", cotLocal)
                .putBoolean("cot_share", cotShare)
                .apply();
    }
}
