package fr.mariusbotkin.sdralert.core.detect;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Channel;

/**
 * Convertit un spectre (dB, fftshifté) en un niveau par canal : moyenne de la
 * puissance linéaire des cases FFT couvertes par la largeur utile du canal.
 */
public final class ChannelLevelExtractor {
    private final int[] firstBin;
    private final int[] lastBin;
    private final float[] levels;

    public ChannelLevelExtractor(Band band, long tuneHz, int sampleRateHz, int fftSize) {
        int n = band.channels.size();
        firstBin = new int[n];
        lastBin = new int[n];
        levels = new float[n];
        double binHz = (double) sampleRateHz / fftSize;
        for (int i = 0; i < n; i++) {
            Channel c = band.channels.get(i);
            double lo = c.centerHz - c.measureBandwidthHz / 2.0 - tuneHz;
            double hi = c.centerHz + c.measureBandwidthHz / 2.0 - tuneHz;
            int a = (int) Math.ceil(lo / binHz) + fftSize / 2;
            int b = (int) Math.floor(hi / binHz) + fftSize / 2;
            if (a < 0 || b >= fftSize || b < a) {
                throw new IllegalArgumentException("Canal " + c + " hors de la bande instantanée");
            }
            firstBin[i] = a;
            lastBin[i] = b;
        }
    }

    public static int binForFrequency(long freqHz, long tuneHz, int sampleRateHz, int fftSize) {
        return (int) Math.round((freqHz - tuneHz) / ((double) sampleRateHz / fftSize)) + fftSize / 2;
    }

    public int firstBin(int channel) { return firstBin[channel]; }

    public int lastBin(int channel) { return lastBin[channel]; }

    /** Renvoie un tableau réutilisé contenant le niveau (dB) de chaque canal. */
    public float[] extract(float[] spectrumDb) {
        for (int i = 0; i < levels.length; i++) {
            double sum = 0;
            for (int k = firstBin[i]; k <= lastBin[i]; k++) {
                sum += Math.pow(10.0, spectrumDb[k] / 10.0);
            }
            levels[i] = (float) (10.0 * Math.log10(sum / (lastBin[i] - firstBin[i] + 1) + 1e-20));
        }
        return levels;
    }
}
