package fr.mariusbotkin.sdralert.core;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.detect.ChannelDetector;
import fr.mariusbotkin.sdralert.core.detect.Clock;
import fr.mariusbotkin.sdralert.core.detect.DetectorConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ChannelDetectorTest {
    private long now;
    private final List<AlertEvent> alerts = new ArrayList<AlertEvent>();
    private ChannelDetector det;
    private DetectorConfig cfg;
    private final Band band = Bands.pmr446();

    @Before
    public void setUp() {
        now = 1_000_000;
        cfg = new DetectorConfig();
        cfg.thresholdDb = 10;
        cfg.minDurationMs = 300;
        cfg.cooldownMs = 10_000;
        cfg.warmupMs = 1_000;
        det = new ChannelDetector(band, cfg, new Clock() {
            @Override
            public long nowMs() {
                return now;
            }
        }, new ChannelDetector.Listener() {
            @Override
            public void onAlert(AlertEvent e) {
                alerts.add(e);
            }
        });
    }

    /** Avance de {@code ms} par pas de 50 ms avec le canal {@code ch} à {@code chLevel} dB. */
    private void run(long ms, int ch, float chLevel) {
        for (long t = 0; t < ms; t += 50) {
            float[] l = new float[band.channels.size()];
            Arrays.fill(l, -60f);
            if (ch >= 0) l[ch] = chLevel;
            det.update(l);
            now += 50;
        }
    }

    @Test
    public void noAlertDuringWarmup() {
        run(500, 3, -20);
        assertTrue(alerts.isEmpty());
    }

    @Test
    public void shortBurstIsIgnored() {
        run(2_000, -1, 0);
        run(200, 3, -40); // 200 ms < durée minimale
        run(500, -1, 0);
        assertTrue(alerts.isEmpty());
    }

    @Test
    public void sustainedSignalRaisesOneAlertOnRightChannel() {
        run(2_000, -1, 0);
        run(2_000, 4, -40);
        assertEquals(1, alerts.size());
        AlertEvent e = alerts.get(0);
        assertEquals("PMR 5", e.channel.name);
        assertEquals(20f, e.snrDb(), 0.5f);
    }

    @Test
    public void cooldownSuppressesRepeatsThenAllowsAgain() {
        run(2_000, -1, 0);
        run(1_000, 0, -40);
        run(1_000, -1, 0);
        run(1_000, 0, -40); // dans le délai de repos
        assertEquals(1, alerts.size());
        run(10_000, -1, 0);
        run(1_000, 0, -40);
        assertEquals(2, alerts.size());
    }

    @Test
    public void longTransmissionDoesNotRaiseNoiseFloor() {
        run(2_000, -1, 0);
        run(20_000, 7, -40);
        assertEquals(-60f, det.state(7).floorDb, 0.01f);
        assertTrue(det.state(7).active);
    }

    @Test
    public void belowThresholdNeverAlerts() {
        run(2_000, -1, 0);
        run(3_000, 2, -55); // +5 dB seulement
        assertTrue(alerts.isEmpty());
    }
}
