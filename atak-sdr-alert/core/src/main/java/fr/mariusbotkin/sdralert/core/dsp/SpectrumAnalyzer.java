package fr.mariusbotkin.sdralert.core.dsp;

/**
 * Transforme des blocs IQ en spectre de puissance (dB, échelle relative),
 * moyenné sur plusieurs trames FFT pour lisser le bruit.
 *
 * Le spectre renvoyé est "fftshifté" : l'indice 0 correspond à
 * (fréquence centrale - fs/2) et l'indice n-1 à (fréquence centrale + fs/2).
 */
public final class SpectrumAnalyzer {
    private final Fft fft;
    private final int n;
    private final float[] window;
    private final float[] re;
    private final float[] im;
    private final double[] accum;
    private final int framesPerSpectrum;
    private int framesAccumulated;
    private int fill;
    private final float windowPowerGain;

    public interface Listener {
        /** Appelé avec un spectre moyenné en dB (tableau réutilisé : copier si besoin). */
        void onSpectrum(float[] powerDb);
    }

    private final Listener listener;
    private final float[] outDb;

    public SpectrumAnalyzer(int fftSize, int framesPerSpectrum, Listener listener) {
        this.fft = new Fft(fftSize);
        this.n = fftSize;
        this.framesPerSpectrum = Math.max(1, framesPerSpectrum);
        this.listener = listener;
        this.window = new float[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            // Fenêtre de Hann : bon compromis fuite spectrale / résolution.
            window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)));
            sum += window[i] * window[i];
        }
        this.windowPowerGain = (float) sum;
        this.re = new float[n];
        this.im = new float[n];
        this.accum = new double[n];
        this.outDb = new float[n];
    }

    public int fftSize() {
        return n;
    }

    /**
     * Ajoute des échantillons IQ entrelacés au format RTL-SDR (octets non signés,
     * I puis Q). Peut être appelé avec des blocs de taille quelconque.
     */
    public void pushRtlU8(byte[] buf, int offset, int length) {
        int end = offset + (length & ~1);
        for (int p = offset; p < end; p += 2) {
            re[fill] = ((buf[p] & 0xFF) - 127.5f) / 127.5f;
            im[fill] = ((buf[p + 1] & 0xFF) - 127.5f) / 127.5f;
            if (++fill == n) {
                processFrame();
            }
        }
    }

    /** Ajoute des échantillons IQ flottants entrelacés (I, Q, I, Q...). */
    public void pushFloat(float[] iq, int offset, int lengthFloats) {
        int end = offset + (lengthFloats & ~1);
        for (int p = offset; p < end; p += 2) {
            re[fill] = iq[p];
            im[fill] = iq[p + 1];
            if (++fill == n) {
                processFrame();
            }
        }
    }

    private void processFrame() {
        fill = 0;
        // Retrait de la composante continue (pic DC typique des RTL-SDR).
        double mr = 0, mi = 0;
        for (int i = 0; i < n; i++) { mr += re[i]; mi += im[i]; }
        float dr = (float) (mr / n), di = (float) (mi / n);
        for (int i = 0; i < n; i++) {
            re[i] = (re[i] - dr) * window[i];
            im[i] = (im[i] - di) * window[i];
        }
        fft.transform(re, im);
        int half = n / 2;
        for (int i = 0; i < n; i++) {
            int src = (i + half) % n; // fftshift
            accum[i] += (double) re[src] * re[src] + (double) im[src] * im[src];
        }
        if (++framesAccumulated >= framesPerSpectrum) {
            double norm = 1.0 / (framesAccumulated * (double) windowPowerGain);
            for (int i = 0; i < n; i++) {
                double p = accum[i] * norm;
                outDb[i] = (float) (10.0 * Math.log10(p + 1e-20));
                accum[i] = 0;
            }
            framesAccumulated = 0;
            if (listener != null) {
                listener.onSpectrum(outDb);
            }
        }
    }
}
