package fr.mariusbotkin.sdralert.core.engine;

import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.detect.ChannelDetector;
import fr.mariusbotkin.sdralert.core.detect.ChannelLevelExtractor;
import fr.mariusbotkin.sdralert.core.detect.Clock;
import fr.mariusbotkin.sdralert.core.dsp.SpectrumAnalyzer;
import fr.mariusbotkin.sdralert.core.source.IqSource;

import java.io.IOException;

/**
 * Boucle de surveillance : lit la source IQ dans un thread dédié, calcule les
 * spectres, alimente le détecteur et publie les spectres pour la cascade.
 * Les callbacks sont appelés depuis le thread de lecture : l'interface doit
 * repasser sur son propre thread (runOnUiThread / Handler).
 */
public final class MonitorEngine {

    public interface Listener {
        /** Nouvel état (texte lisible). */
        void onStatus(String status, boolean running);

        /** Spectre moyenné, en dB (copie : peut être conservée). */
        void onSpectrum(SpectrumFrame frame);

        void onAlert(AlertEvent event);
    }

    /** Un spectre et le contexte nécessaire pour l'afficher. */
    public static final class SpectrumFrame {
        public final float[] powerDb;
        public final float[] channelLevelsDb;
        public final float[] channelFloorsDb;
        public final boolean[] channelActive;
        public final long tuneHz;
        public final int sampleRateHz;

        SpectrumFrame(float[] powerDb, float[] levels, float[] floors, boolean[] active, long tuneHz, int sr) {
            this.powerDb = powerDb;
            this.channelLevelsDb = levels;
            this.channelFloorsDb = floors;
            this.channelActive = active;
            this.tuneHz = tuneHz;
            this.sampleRateHz = sr;
        }
    }

    /** Fabrique de source : permet de recréer la connexion après une coupure. */
    public interface SourceFactory {
        IqSource create();
    }

    private final SourceFactory factory;
    private final MonitorConfig config;
    private final Listener listener;
    private final Clock clock;
    private volatile Thread thread;
    private volatile boolean stopRequested;
    private volatile IqSource current;

    public MonitorEngine(SourceFactory factory, MonitorConfig config, Clock clock, Listener listener) {
        this.factory = factory;
        this.config = config;
        this.clock = clock;
        this.listener = listener;
    }

    public synchronized void start() {
        if (thread != null) return;
        stopRequested = false;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "sdr-monitor");
        t.setPriority(Thread.MAX_PRIORITY - 1);
        thread = t;
        t.start();
    }

    public void stop() {
        Thread t;
        synchronized (this) {
            t = thread;
            stopRequested = true;
        }
        IqSource s = current;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return thread != null;
    }

    private void loop() {
        int attempt = 0;
        try {
            while (!stopRequested) {
                try {
                    runOnce();
                    attempt = 0;
                } catch (IOException e) {
                    if (stopRequested) break;
                    attempt++;
                    listener.onStatus("Récepteur indisponible (" + e.getMessage() + "), nouvelle tentative…", true);
                    sleepQuietly(Math.min(5_000, 500L * attempt));
                }
            }
        } catch (RuntimeException e) {
            listener.onStatus("Erreur : " + e, false);
        } finally {
            synchronized (this) {
                thread = null;
            }
            listener.onStatus("Arrêté", false);
        }
    }

    /**
     * Demande d'appliquer les changements de bande / gain / ppm sans couper la
     * connexion (le pilote Android s'arrête souvent quand le client se déconnecte).
     */
    public void requestReconfigure() {
        reconfigure = true;
    }

    private volatile boolean reconfigure;

    /** Chaîne de traitement pour une configuration donnée. */
    private final class Pipeline {
        final int sr;
        final long tune;
        final SpectrumAnalyzer analyzer;

        Pipeline() {
            sr = config.effectiveSampleRate();
            tune = config.effectiveTuneHz();
            final ChannelLevelExtractor extractor = new ChannelLevelExtractor(config.band, tune, sr, config.fftSize);
            final ChannelDetector detector = new ChannelDetector(config.band, config.detector, clock,
                    new ChannelDetector.Listener() {
                        @Override
                        public void onAlert(AlertEvent event) {
                            listener.onAlert(event);
                        }
                    });
            final int nCh = config.band.channels.size();
            analyzer = new SpectrumAnalyzer(config.fftSize, config.framesPerSpectrum(),
                    new SpectrumAnalyzer.Listener() {
                        @Override
                        public void onSpectrum(float[] powerDb) {
                            float[] levels = extractor.extract(powerDb);
                            detector.update(levels);
                            float[] floors = new float[nCh];
                            boolean[] active = new boolean[nCh];
                            for (int i = 0; i < nCh; i++) {
                                ChannelDetector.ChannelState st = detector.state(i);
                                floors[i] = st.floorDb;
                                active[i] = st.active;
                            }
                            listener.onSpectrum(new SpectrumFrame(powerDb.clone(), levels.clone(), floors, active, tune, sr));
                        }
                    });
        }

        void apply(IqSource src) throws IOException {
            src.setSampleRate(sr);
            src.setCenterFrequency(tune);
            src.setPpmCorrection(config.ppm);
            src.setGain(config.gainTenthsDb);
            listener.onStatus(String.format(java.util.Locale.ROOT, "Surveillance %s — %s, accord %.4f MHz",
                    config.band.label, src.describe(), tune / 1e6), true);
        }
    }

    private void runOnce() throws IOException {
        IqSource src = factory.create();
        current = src;
        try {
            src.open();
            if (stopRequested) return;
            reconfigure = false;
            Pipeline pipe = new Pipeline();
            pipe.apply(src);
            byte[] buf = new byte[1 << 16];
            boolean signedSamples = src.format() == IqSource.Format.S8_IQ;
            // Après un changement d'accord, on jette ~100 ms d'échantillons encore en vol.
            long discardBytes = 0;
            while (!stopRequested) {
                if (reconfigure) {
                    reconfigure = false;
                    pipe = new Pipeline();
                    pipe.apply(src);
                    discardBytes = pipe.sr / 5L; // 0,1 s × 2 octets par échantillon
                }
                int r = src.read(buf, 0, buf.length);
                if (r < 0) throw new IOException("flux interrompu");
                if (discardBytes > 0) {
                    discardBytes -= r;
                    continue;
                }
                if (signedSamples) {
                    for (int i = 0; i < r; i++) buf[i] = (byte) (buf[i] + 128);
                }
                pipe.analyzer.pushRtlU8(buf, 0, r);
            }
        } finally {
            current = null;
            try {
                src.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
