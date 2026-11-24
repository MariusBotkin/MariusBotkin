package fr.mariusbotkin.sdralert.core;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.detect.ChannelLevelExtractor;
import org.junit.Test;

import static org.junit.Assert.*;

public class BandsTest {
    @Test
    public void pmr446Channels() {
        Band b = Bands.pmr446();
        assertEquals(16, b.channels.size());
        assertEquals(446_006_250L, b.channels.get(0).centerHz);
        assertEquals(446_193_750L, b.channels.get(15).centerHz);
        assertEquals(1_024_000, b.recommendedSampleRateHz());
    }

    @Test
    public void pmrTuningKeepsBandAwayFromDc() {
        Band b = Bands.pmr446();
        long tune = b.suggestedTuneHz(1_024_000);
        assertTrue("le DC doit être sous la bande", tune < b.lowestHz());
        assertTrue(b.highestHz() - tune < 1_024_000 * 0.4);
    }

    @Test
    public void everyBandFitsItsRecommendedRate() {
        for (Band b : Bands.all()) {
            int sr = b.recommendedSampleRateHz();
            long tune = b.suggestedTuneHz(sr);
            // Lève une exception si un canal sort de la bande instantanée.
            new ChannelLevelExtractor(b, tune, sr, 4096);
        }
    }
}
