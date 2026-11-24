package fr.mariusbotkin.sdralert.core.detect;

/** Réglages de la détection (modifiables depuis le panneau ATAK). */
public final class DetectorConfig {
    /** Dépassement du plancher de bruit, en dB, qui déclenche la détection. */
    public volatile float thresholdDb = 10f;
    /** Hystérésis : le canal redevient libre sous (seuil - hystérésis). */
    public volatile float hysteresisDb = 3f;
    /** Durée minimale au-dessus du seuil avant alerte (anti-rebond). */
    public volatile long minDurationMs = 300;
    /** Délai de repos entre deux alertes sur le même canal. */
    public volatile long cooldownMs = 30_000;
    /** Fenêtre du plancher de bruit glissant. */
    public volatile long noiseWindowMs = 5_000;
    /** Temps de chauffe : pas d'alerte tant que le plancher n'est pas établi. */
    public volatile long warmupMs = 2_000;

    public DetectorConfig copy() {
        DetectorConfig c = new DetectorConfig();
        c.thresholdDb = thresholdDb;
        c.hysteresisDb = hysteresisDb;
        c.minDurationMs = minDurationMs;
        c.cooldownMs = cooldownMs;
        c.noiseWindowMs = noiseWindowMs;
        c.warmupMs = warmupMs;
        return c;
    }
}
