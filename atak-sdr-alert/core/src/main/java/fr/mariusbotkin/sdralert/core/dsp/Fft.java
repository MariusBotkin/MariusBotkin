package fr.mariusbotkin.sdralert.core.dsp;

/**
 * FFT complexe radix-2, en place, taille fixe (puissance de 2).
 * Les tables de twiddles et de bit-reversal sont précalculées une seule fois.
 */
public final class Fft {
    private final int n;
    private final int[] rev;
    private final float[] cos;
    private final float[] sin;

    public Fft(int n) {
        if (n < 2 || Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("La taille FFT doit être une puissance de 2 : " + n);
        }
        this.n = n;
        int bits = Integer.numberOfTrailingZeros(n);
        rev = new int[n];
        for (int i = 0; i < n; i++) {
            rev[i] = Integer.reverse(i) >>> (32 - bits);
        }
        cos = new float[n / 2];
        sin = new float[n / 2];
        for (int i = 0; i < n / 2; i++) {
            double a = -2.0 * Math.PI * i / n;
            cos[i] = (float) Math.cos(a);
            sin[i] = (float) Math.sin(a);
        }
    }

    public int size() {
        return n;
    }

    /** Transforme re/im en place. */
    public void transform(float[] re, float[] im) {
        for (int i = 0; i < n; i++) {
            int j = rev[i];
            if (j > i) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            int half = len >> 1;
            int step = n / len;
            for (int i = 0; i < n; i += len) {
                for (int k = 0; k < half; k++) {
                    float wr = cos[k * step];
                    float wi = sin[k * step];
                    int a = i + k;
                    int b = a + half;
                    float xr = re[b] * wr - im[b] * wi;
                    float xi = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                }
            }
        }
    }
}
