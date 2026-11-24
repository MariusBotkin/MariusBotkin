package fr.mariusbotkin.sdralert.core;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.detect.Clock;
import fr.mariusbotkin.sdralert.core.engine.MonitorConfig;
import fr.mariusbotkin.sdralert.core.engine.MonitorEngine;
import fr.mariusbotkin.sdralert.core.source.IqSource;
import fr.mariusbotkin.sdralert.core.source.RtlTcpSource;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * Faux serveur rtl_tcp qui envoie du bruit, puis une porteuse sur le canal PMR 9.
 * Vérifie les commandes reçues (fréquence, taux) et l'alerte produite.
 */
public class EndToEndTest {

    @Test
    public void fakeRtlTcpServerProducesAlertOnPmr9() throws Exception {
        final Band band = Bands.pmr446();
        final int sr = band.recommendedSampleRateHz();
        final long tune = band.suggestedTuneHz(sr);
        final long targetHz = band.channels.get(8).centerHz;
        final AtomicLong receivedFreq = new AtomicLong();
        final AtomicLong receivedRate = new AtomicLong();
        // Temps simulé : avancé au rythme des échantillons envoyés.
        final AtomicLong samplesSent = new AtomicLong();

        final ServerSocket server = new ServerSocket(0);
        Thread srv = new Thread(new Runnable() {
            @Override
            public void run() {
                try (Socket s = server.accept()) {
                    OutputStream out = s.getOutputStream();
                    final InputStream in = s.getInputStream();
                    out.write(new byte[]{'R', 'T', 'L', '0', 0, 0, 0, 5, 0, 0, 0, 29});
                    Thread cmd = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            DataInputStream d = new DataInputStream(in);
                            try {
                                while (true) {
                                    int c = d.readUnsignedByte();
                                    long p = d.readInt() & 0xFFFFFFFFL;
                                    if (c == 1) receivedFreq.set(p);
                                    if (c == 2) receivedRate.set(p);
                                }
                            } catch (IOException ignored) {
                            }
                        }
                    });
                    cmd.setDaemon(true);
                    cmd.start();
                    SignalGen gen = new SignalGen(sr);
                    byte[] buf = new byte[8192 * 2];
                    long total = 0;
                    while (total < sr * 8L) { // 8 s de signal simulé
                        boolean on = total > sr * 4L; // porteuse à partir de 4 s
                        gen.fill(buf, 0.05, on ? targetHz - tune : Double.NaN, 0.1);
                        out.write(buf);
                        total += buf.length / 2;
                        samplesSent.set(total);
                    }
                    Thread.sleep(200);
                } catch (Exception ignored) {
                }
            }
        });
        srv.setDaemon(true);
        srv.start();

        MonitorConfig cfg = new MonitorConfig();
        cfg.band = band;
        cfg.detector.minDurationMs = 300;
        final CopyOnWriteArrayList<AlertEvent> alerts = new CopyOnWriteArrayList<AlertEvent>();
        final CountDownLatch gotAlert = new CountDownLatch(1);
        final int port = server.getLocalPort();
        MonitorEngine engine = new MonitorEngine(new MonitorEngine.SourceFactory() {
            @Override
            public IqSource create() {
                return new RtlTcpSource("127.0.0.1", port, 2_000);
            }
        }, cfg, new Clock() {
            @Override
            public long nowMs() {
                return samplesSent.get() * 1000L / sr;
            }
        }, new MonitorEngine.Listener() {
            @Override
            public void onStatus(String status, boolean running) {
            }

            @Override
            public void onSpectrum(MonitorEngine.SpectrumFrame frame) {
            }

            @Override
            public void onAlert(AlertEvent event) {
                alerts.add(event);
                gotAlert.countDown();
            }
        });
        engine.start();
        boolean ok = gotAlert.await(30, TimeUnit.SECONDS);
        engine.stop();
        server.close();

        assertTrue("aucune alerte reçue", ok);
        assertEquals(tune, receivedFreq.get());
        assertEquals(sr, receivedRate.get());
        assertEquals(1, alerts.size());
        assertEquals("PMR 9", alerts.get(0).channel.name);
        assertTrue(alerts.get(0).snrDb() > 10);
    }
}
