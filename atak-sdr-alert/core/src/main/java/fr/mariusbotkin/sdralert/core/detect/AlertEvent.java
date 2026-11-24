package fr.mariusbotkin.sdralert.core.detect;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Channel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Une alerte : un canal a dépassé le seuil pendant la durée minimale. */
public final class AlertEvent {
    public final long timeMs;
    public final Band band;
    public final Channel channel;
    public final float levelDb;
    public final float floorDb;

    public AlertEvent(long timeMs, Band band, Channel channel, float levelDb, float floorDb) {
        this.timeMs = timeMs;
        this.band = band;
        this.channel = channel;
        this.levelDb = levelDb;
        this.floorDb = floorDb;
    }

    /** Dépassement du plancher en dB. */
    public float snrDb() {
        return levelDb - floorDb;
    }

    public String summary() {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(timeMs));
        return String.format(Locale.ROOT, "%s  %s  %s  +%.1f dB", t, band.id.toUpperCase(Locale.ROOT),
                channel, snrDb());
    }

    @Override
    public String toString() {
        return summary();
    }
}
