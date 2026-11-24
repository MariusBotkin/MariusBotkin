package fr.mariusbotkin.sdralert.core.detect;

import java.util.Arrays;

/**
 * Plancher de bruit glissant : percentile bas (20 %) des niveaux observés sur
 * une fenêtre temporelle. Un percentile bas résiste mieux qu'une moyenne aux
 * émissions courtes, et on cesse d'alimenter l'estimateur pendant qu'un canal
 * est actif pour qu'une émission longue ne fasse pas monter le plancher.
 */
public final class NoiseFloorEstimator {
    private static final float PERCENTILE = 0.2f;

    private final float[] values;
    private final long[] times;
    private int head;
    private int count;
    private final float[] scratch;

    public NoiseFloorEstimator(int capacity) {
        values = new float[capacity];
        times = new long[capacity];
        scratch = new float[capacity];
    }

    public void add(long nowMs, float levelDb) {
        values[head] = levelDb;
        times[head] = nowMs;
        head = (head + 1) % values.length;
        if (count < values.length) count++;
    }

    /** Retire les mesures plus anciennes que la fenêtre. */
    public void expire(long nowMs, long windowMs) {
        while (count > 0) {
            int oldest = (head - count + values.length) % values.length;
            if (nowMs - times[oldest] <= windowMs) break;
            count--;
        }
    }

    public int count() {
        return count;
    }

    /** Plancher estimé, ou NaN si aucune mesure. */
    public float floorDb() {
        if (count == 0) return Float.NaN;
        int start = (head - count + values.length) % values.length;
        for (int i = 0; i < count; i++) {
            scratch[i] = values[(start + i) % values.length];
        }
        Arrays.sort(scratch, 0, count);
        return scratch[Math.min(count - 1, (int) (count * PERCENTILE))];
    }

    public void clear() {
        count = 0;
        head = 0;
    }
}
