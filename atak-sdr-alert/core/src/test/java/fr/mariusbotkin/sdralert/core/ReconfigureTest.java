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
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Changement de bande en cours de route : même connexion, nouvel accord envoyé. */
public class ReconfigureTest {
    @Test
    public void bandChangeRetunesWithoutReconnecting() throws Exception {
        final CopyOnWriteArrayList<Long> freqs = new CopyOnWriteArrayList<Long>();
        final AtomicInteger connections = new AtomicInteger();
        final ServerSocket server = new ServerSocket(0);
        Thread srv = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        final Socket s = server.accept();
                        connections.incrementAndGet();
                        final OutputStream out = s.getOutputStream();
                        out.write(new byte[]{'R', 'T', 'L', '0', 0, 0, 0, 5, 0, 0, 0, 29});
                        Thread cmd = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    DataInputStream d = new DataInputStream(s.getInputStream());
                                    while (true) {
                                        int c = d.readUnsignedByte();
                                        long p = d.readInt() & 0xFFFFFFFFL;
                                        if (c == 1) freqs.add(p);
                                    }
                                } catch (IOException ignored) {
                                }
                            }
                        });
                        cmd.setDaemon(true);
                        cmd.start();
                        SignalGen gen = new SignalGen(2_048_000);
                        byte[] buf = new byte[16384];
                        try {
                            while (true) {
                                gen.fill(buf, 0.05, Double.NaN, 0);
                                out.write(buf);
                                Thread.sleep(2);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        });
        srv.setDaemon(true);
        srv.start();

        final MonitorConfig cfg = new MonitorConfig();
        cfg.band = Bands.pmr446();
        final int port = server.getLocalPort();
        MonitorEngine engine = new MonitorEngine(new MonitorEngine.SourceFactory() {
            @Override
            public IqSource create() {
                return new RtlTcpSource("127.0.0.1", port, 2_000);
            }
        }, cfg, Clock.SYSTEM, new MonitorEngine.Listener() {
            @Override
            public void onStatus(String status, boolean running) {
            }

            @Override
            public void onSpectrum(MonitorEngine.SpectrumFrame frame) {
            }

            @Override
            public void onAlert(AlertEvent event) {
            }
        });
        engine.start();
        waitFor(freqs, 1);
        Band lpd = Bands.lpd433();
        cfg.band = lpd;
        engine.requestReconfigure();
        waitFor(freqs, 2);
        engine.stop();
        server.close();

        assertEquals(1, connections.get());
        long pmrTune = Bands.pmr446().suggestedTuneHz(Bands.pmr446().recommendedSampleRateHz());
        assertEquals(pmrTune, (long) freqs.get(0));
        assertEquals(lpd.suggestedTuneHz(lpd.recommendedSampleRateHz()), (long) freqs.get(1));
    }

    private static void waitFor(CopyOnWriteArrayList<Long> list, int n) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (list.size() < n && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertTrue("commande de fréquence non reçue", list.size() >= n);
    }
}
