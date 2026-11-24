package fr.mariusbotkin.sdralert.core.band;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Une bande surveillée : une liste de canaux qui doit tenir dans la bande
 * instantanée du récepteur (≈ 2 MHz pour une RTL-SDR).
 */
public final class Band {
    public final String id;
    public final String label;
    public final List<Channel> channels;

    public Band(String id, String label, List<Channel> channels) {
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("Une bande doit contenir au moins un canal");
        }
        this.id = id;
        this.label = label;
        this.channels = Collections.unmodifiableList(new ArrayList<Channel>(channels));
    }

    public long lowestHz() {
        long lo = Long.MAX_VALUE;
        for (Channel c : channels) lo = Math.min(lo, c.centerHz - c.measureBandwidthHz / 2);
        return lo;
    }

    public long highestHz() {
        long hi = Long.MIN_VALUE;
        for (Channel c : channels) hi = Math.max(hi, c.centerHz + c.measureBandwidthHz / 2);
        return hi;
    }

    public long spanHz() {
        return highestHz() - lowestHz();
    }

    public long centerHz() {
        return (lowestHz() + highestHz()) / 2;
    }

    /**
     * Fréquence d'accord conseillée : on décale l'accord pour que la bande ne
     * tombe pas sur le pic DC au centre du spectre RTL-SDR.
     */
    public long suggestedTuneHz(int sampleRateHz) {
        long span = spanHz();
        long usable = sampleRateHz * 8L / 10; // les bords (filtre anti-repliement) sont ignorés
        if (span > usable) {
            throw new IllegalArgumentException("Bande " + id + " trop large (" + span
                    + " Hz) pour une bande instantanée de " + sampleRateHz + " Hz");
        }
        long guard = 20_000; // marge au-dessus du DC
        // Décalage idéal : toute la bande au-dessus du DC. Sinon, on décale autant
        // que la bande instantanée le permet.
        long offset = Math.min(span / 2 + guard, usable / 2 - span / 2);
        return centerHz() - Math.max(0, offset);
    }

    /** Taux d'échantillonnage RTL-SDR le plus bas qui couvre la bande (moins de CPU). */
    public int recommendedSampleRateHz() {
        int[] rates = {1_024_000, 2_048_000, 2_400_000};
        for (int r : rates) {
            // Idéal : la bande tient entièrement d'un côté du DC (span*2 + marges).
            if (spanHz() * 2 + 40_000 <= r * 8L / 10) {
                return r;
            }
        }
        for (int r : rates) {
            if (spanHz() <= r * 8L / 10) return r;
        }
        throw new IllegalArgumentException("Bande " + id + " trop large pour une RTL-SDR");
    }

    @Override
    public String toString() {
        return label;
    }
}
