package fr.mariusbotkin.sdralert.core.detect;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Channel;

import java.util.List;

/**
 * Détecteur d'activité par canal. Reçoit à chaque mise à jour le niveau (dB)
 * de chaque canal de la bande et émet une {@link AlertEvent} quand un canal
 * reste au-dessus de (plancher + seuil) pendant la durée minimale.
 *
 * Aucune classification : on signale seulement qu'il y a de l'énergie.
 */
public final class ChannelDetector {

    public interface Listener {
        void onAlert(AlertEvent event);
    }

    /** État publié pour l'interface (lecture seule). */
    public static final class ChannelState {
        public float levelDb = Float.NaN;
        public float floorDb = Float.NaN;
        public boolean active;
        long aboveSinceMs = -1;
        long lastAlertMs = Long.MIN_VALUE / 2;
    }

    private final Band band;
    private final DetectorConfig config;
    private final Clock clock;
    private final Listener listener;
    private final NoiseFloorEstimator[] floors;
    private final ChannelState[] states;
    private long startMs = -1;

    public ChannelDetector(Band band, DetectorConfig config, Clock clock, Listener listener) {
        this.band = band;
        this.config = config;
        this.clock = clock;
        this.listener = listener;
        int n = band.channels.size();
        floors = new NoiseFloorEstimator[n];
        states = new ChannelState[n];
        for (int i = 0; i < n; i++) {
            // Capacité large : ~50 mises à jour/s sur 10 s maximum.
            floors[i] = new NoiseFloorEstimator(512);
            states[i] = new ChannelState();
        }
    }

    public Band band() {
        return band;
    }

    public ChannelState state(int channelIndex) {
        return states[channelIndex];
    }

    /** @param levelsDb niveau de chaque canal, dans l'ordre de {@code band.channels}. */
    public void update(float[] levelsDb) {
        long now = clock.nowMs();
        if (startMs < 0) startMs = now;
        boolean warm = now - startMs >= config.warmupMs;
        List<Channel> channels = band.channels;

        for (int i = 0; i < channels.size(); i++) {
            ChannelState st = states[i];
            NoiseFloorEstimator nf = floors[i];
            float level = levelsDb[i];
            // La fenêtre n'est vieillie qu'au moment d'ajouter une mesure (canal calme) :
            // pendant une émission longue, le plancher reste celui d'avant l'émission.
            float floor = nf.floorDb();
            st.levelDb = level;
            st.floorDb = floor;

            if (Float.isNaN(floor) || !warm) {
                nf.expire(now, config.noiseWindowMs);
                nf.add(now, level);
                continue;
            }

            float excess = level - floor;
            boolean above = excess >= config.thresholdDb;
            boolean stillActive = st.active && excess >= config.thresholdDb - config.hysteresisDb;

            if (above || stillActive) {
                if (st.aboveSinceMs < 0) st.aboveSinceMs = now;
                if (!st.active && now - st.aboveSinceMs >= config.minDurationMs) {
                    st.active = true;
                    if (now - st.lastAlertMs >= config.cooldownMs) {
                        st.lastAlertMs = now;
                        if (listener != null) {
                            listener.onAlert(new AlertEvent(now, band, channels.get(i), level, floor));
                        }
                    }
                }
                // Pendant une activité (même pas encore confirmée) on gèle le plancher.
            } else {
                st.active = false;
                st.aboveSinceMs = -1;
                nf.expire(now, config.noiseWindowMs);
                nf.add(now, level);
            }
        }
    }

    public void reset() {
        startMs = -1;
        for (int i = 0; i < floors.length; i++) {
            floors[i].clear();
            states[i] = new ChannelState();
        }
    }
}
