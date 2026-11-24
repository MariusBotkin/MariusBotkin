package fr.mariusbotkin.sdralert.core.engine;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.detect.DetectorConfig;

/** Réglages de la surveillance. */
public final class MonitorConfig {
    public volatile Band band = Bands.pmr446();
    /** 0 = taux conseillé pour la bande. */
    public volatile int sampleRateHz = 0;
    /** 0 = fréquence d'accord conseillée (bande décalée du pic DC). */
    public volatile long tuneHz = 0;
    public volatile int fftSize = 4096;
    /** Nombre de spectres par seconde (résolution temporelle de la cascade et de la détection). */
    public volatile int spectraPerSecond = 20;
    /** Gain en dixièmes de dB ; négatif = automatique. 30 dB est un bon départ en PMR. */
    public volatile int gainTenthsDb = 300;
    public volatile int ppm = 0;
    public final DetectorConfig detector = new DetectorConfig();

    public int effectiveSampleRate() {
        return sampleRateHz > 0 ? sampleRateHz : band.recommendedSampleRateHz();
    }

    public long effectiveTuneHz() {
        return tuneHz > 0 ? tuneHz : band.suggestedTuneHz(effectiveSampleRate());
    }

    public int framesPerSpectrum() {
        int framesPerSecond = effectiveSampleRate() / fftSize;
        return Math.max(1, framesPerSecond / Math.max(1, spectraPerSecond));
    }
}
