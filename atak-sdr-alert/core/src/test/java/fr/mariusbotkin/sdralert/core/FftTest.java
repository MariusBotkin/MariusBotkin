package fr.mariusbotkin.sdralert.core;

import fr.mariusbotkin.sdralert.core.dsp.Fft;
import fr.mariusbotkin.sdralert.core.dsp.SpectrumAnalyzer;
import org.junit.Test;

import static org.junit.Assert.*;

public class FftTest {
    @Test
    public void singleToneLandsInExpectedBin() {
        int n = 1024;
        Fft fft = new Fft(n);
        float[] re = new float[n], im = new float[n];
        int k = 37;
        for (int i = 0; i < n; i++) {
            re[i] = (float) Math.cos(2 * Math.PI * k * i / n);
            im[i] = (float) Math.sin(2 * Math.PI * k * i / n);
        }
        fft.transform(re, im);
        int best = 0;
        for (int i = 1; i < n; i++) {
            if (re[i] * re[i] + im[i] * im[i] > re[best] * re[best] + im[best] * im[best]) best = i;
        }
        assertEquals(k, best);
        assertEquals(n, Math.hypot(re[k], im[k]), 1e-2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPowerOfTwo() {
        new Fft(1000);
    }

    @Test
    public void analyzerShiftsSpectrumAroundCenter() {
        final int sr = 1_024_000, n = 4096;
        final float[][] last = new float[1][];
        SpectrumAnalyzer a = new SpectrumAnalyzer(n, 4, new SpectrumAnalyzer.Listener() {
            @Override
            public void onSpectrum(float[] db) {
                last[0] = db.clone();
            }
        });
        SignalGen gen = new SignalGen(sr);
        byte[] buf = new byte[n * 2 * 4];
        gen.fill(buf, 0.02, -100_000, 0.5); // porteuse à -100 kHz
        a.pushRtlU8(buf, 0, buf.length);
        assertNotNull(last[0]);
        int best = 0;
        for (int i = 0; i < n; i++) if (last[0][i] > last[0][best]) best = i;
        int expected = n / 2 - (int) Math.round(100_000.0 / sr * n);
        assertTrue("pic en " + best + ", attendu ~" + expected, Math.abs(best - expected) <= 1);
    }
}
