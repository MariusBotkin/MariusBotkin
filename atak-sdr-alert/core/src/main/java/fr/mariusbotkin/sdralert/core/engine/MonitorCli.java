package fr.mariusbotkin.sdralert.core.engine;

import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.detect.Clock;
import fr.mariusbotkin.sdralert.core.source.IqSource;
import fr.mariusbotkin.sdralert.core.source.RtlTcpSource;

import java.util.Locale;

/**
 * Banc d'essai sur PC, sans ATAK : lancer {@code rtl_tcp -a 127.0.0.1} puis
 * {@code gradle :core:runCli -PcliArgs="pmr446 127.0.0.1 1234 10"}.
 * Arguments : bande, hôte, port, seuil en dB.
 */
public final class MonitorCli {
    public static void main(String[] args) throws Exception {
        final MonitorConfig cfg = new MonitorConfig();
        cfg.band = Bands.byId(args.length > 0 ? args[0] : "pmr446");
        final String host = args.length > 1 ? args[1] : "127.0.0.1";
        final int port = args.length > 2 ? Integer.parseInt(args[2]) : 1234;
        if (args.length > 3) cfg.detector.thresholdDb = Float.parseFloat(args[3]);
        cfg.spectraPerSecond = 10;

        final MonitorEngine engine = new MonitorEngine(new MonitorEngine.SourceFactory() {
            @Override
            public IqSource create() {
                return new RtlTcpSource(host, port, 3_000);
            }
        }, cfg, Clock.SYSTEM, new MonitorEngine.Listener() {
            int frames;

            @Override
            public void onStatus(String status, boolean running) {
                System.out.println("[état] " + status);
            }

            @Override
            public void onSpectrum(MonitorEngine.SpectrumFrame f) {
                if (++frames % 10 != 0) return;
                StringBuilder sb = new StringBuilder("[niveaux] ");
                for (int i = 0; i < f.channelLevelsDb.length && i < 16; i++) {
                    float d = f.channelLevelsDb[i] - f.channelFloorsDb[i];
                    sb.append(f.channelActive[i] ? '#' : Float.isNaN(d) ? '?' : d > 3 ? '+' : '.');
                }
                System.out.println(sb);
            }

            @Override
            public void onAlert(AlertEvent e) {
                System.out.println(String.format(Locale.ROOT, "[ALERTE] %s", e.summary()));
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                engine.stop();
            }
        }));
        engine.start();
        System.out.println("Entrée pour arrêter.");
        System.in.read();
        engine.stop();
    }
}
