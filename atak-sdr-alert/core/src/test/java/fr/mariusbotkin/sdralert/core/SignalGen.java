package fr.mariusbotkin.sdralert.core;

import java.util.Random;

/** Génère des échantillons RTL-SDR (u8 I/Q) : bruit gaussien + porteuse optionnelle. */
public final class SignalGen {
    private final Random rnd = new Random(42);
    private final int sampleRate;
    private double phase;

    public SignalGen(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /** @param toneOffsetHz décalage de la porteuse par rapport à l'accord, ou NaN pour aucune. */
    public void fill(byte[] out, double noiseRms, double toneOffsetHz, double toneAmp) {
        double dphi = Double.isNaN(toneOffsetHz) ? 0 : 2 * Math.PI * toneOffsetHz / sampleRate;
        for (int i = 0; i < out.length; i += 2) {
            double iv = rnd.nextGaussian() * noiseRms;
            double qv = rnd.nextGaussian() * noiseRms;
            if (!Double.isNaN(toneOffsetHz)) {
                iv += toneAmp * Math.cos(phase);
                qv += toneAmp * Math.sin(phase);
                phase += dphi;
            }
            out[i] = toU8(iv);
            out[i + 1] = toU8(qv);
        }
    }

    private static byte toU8(double v) {
        long x = Math.round(v * 127.5 + 127.5);
        return (byte) Math.max(0, Math.min(255, x));
    }
}
