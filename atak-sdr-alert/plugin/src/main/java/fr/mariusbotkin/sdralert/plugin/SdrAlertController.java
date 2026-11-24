package fr.mariusbotkin.sdralert.plugin;

import android.content.Context;
import android.util.Log;

import com.atakmap.android.maps.MapView;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.detect.Clock;
import fr.mariusbotkin.sdralert.core.engine.MonitorConfig;
import fr.mariusbotkin.sdralert.core.engine.MonitorEngine;
import fr.mariusbotkin.sdralert.core.source.IqSource;
import fr.mariusbotkin.sdralert.core.source.RtlTcpSource;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cœur du plugin côté Android : possède le moteur de surveillance, les réglages,
 * l'historique des alertes, et relaie les événements vers l'interface (thread UI).
 * La surveillance continue quand le panneau est fermé.
 */
public final class SdrAlertController {
    private static final String TAG = "SdrAlertController";
    private static final int HISTORY_MAX = 50;

    /** Événements pour le panneau ; toujours appelés sur le thread UI. */
    public interface UiListener {
        void onStatus(String status, boolean running);

        void onSpectrum(MonitorEngine.SpectrumFrame frame);

        void onAlert(AlertEvent event);
    }

    private final MapView mapView;
    private final MonitorConfig config = new MonitorConfig();
    private final SdrAlertSettings settings;
    private final AlertNotifier notifier;
    private final LinkedList<AlertEvent> history = new LinkedList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private MonitorEngine engine;
    private volatile UiListener ui;
    private volatile String lastStatus = "Arrêté";
    private volatile boolean running;

    public SdrAlertController(MapView mapView, Context pluginContext) {
        this.mapView = mapView;
        this.settings = new SdrAlertSettings(mapView.getContext());
        this.settings.load(config);
        this.notifier = new AlertNotifier(mapView.getContext());
    }

    public MonitorConfig config() {
        return config;
    }

    public SdrAlertSettings settings() {
        return settings;
    }

    public boolean isRunning() {
        return running;
    }

    public String lastStatus() {
        return lastStatus;
    }

    public synchronized List<AlertEvent> history() {
        return new ArrayList<>(history);
    }

    public synchronized void clearHistory() {
        history.clear();
    }

    public void setUiListener(UiListener l) {
        ui = l;
    }

    /** Sauvegarde les réglages et applique un changement de bande/gain à chaud. */
    public void settingsChanged(boolean retune) {
        settings.save(config);
        MonitorEngine e = engine;
        if (retune && e != null) e.requestReconfigure();
    }

    public void setBand(Band band) {
        if (band.id.equals(config.band.id)) return;
        config.band = band;
        settingsChanged(true);
    }

    /** Démarre : lance le pilote RTL-SDR puis se connecte à son serveur rtl_tcp. */
    public void start() {
        if (engine != null && engine.isRunning()) return;
        engine = null; // moteur arrêté sur erreur : on en recrée un
        String err = launchDriver();
        if (err != null) {
            publishStatus(err, false);
            return;
        }
        engine = new MonitorEngine(new MonitorEngine.SourceFactory() {
            @Override
            public IqSource create() {
                return new RtlTcpSource(RtlDriverLauncher.HOST, RtlDriverLauncher.PORT, 2_000);
            }
        }, config, Clock.SYSTEM, engineListener);
        running = true;
        engine.start();
    }

    /** Relance l'application pilote (après débranchement de la clé, par exemple). */
    public String launchDriver() {
        int sr = config.effectiveSampleRate();
        return RtlDriverLauncher.launch(mapView.getContext(), config.effectiveTuneHz(), sr);
    }

    public void stop() {
        final MonitorEngine e = engine;
        engine = null;
        running = false;
        if (e != null) {
            // stop() attend la fin du thread : jamais sur le thread UI.
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    e.stop();
                }
            });
        }
    }

    public void shutdown() {
        stop();
        worker.shutdown();
        notifier.release();
        ui = null;
    }

    private void publishStatus(final String status, final boolean isRunning) {
        lastStatus = status;
        mapView.post(new Runnable() {
            @Override
            public void run() {
                UiListener l = ui;
                if (l != null) l.onStatus(status, isRunning);
            }
        });
    }

    private final MonitorEngine.Listener engineListener = new MonitorEngine.Listener() {
        @Override
        public void onStatus(String status, boolean isRunning) {
            Log.d(TAG, status);
            if (!isRunning) running = engine != null && engine.isRunning();
            publishStatus(status, isRunning);
        }

        @Override
        public void onSpectrum(final MonitorEngine.SpectrumFrame frame) {
            if (ui == null) return; // panneau fermé : pas de rendu
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    UiListener l = ui;
                    if (l != null) l.onSpectrum(frame);
                }
            });
        }

        @Override
        public void onAlert(final AlertEvent event) {
            synchronized (SdrAlertController.this) {
                history.addFirst(event);
                while (history.size() > HISTORY_MAX) history.removeLast();
            }
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    notifier.notify(event, settings.sound, settings.vibrate);
                    if (settings.cotMarker) {
                        try {
                            if (!CotAlertMarker.dispatch(mapView, event, settings.shareCot)) {
                                Log.w(TAG, "Position inconnue : marqueur non posé");
                            }
                        } catch (RuntimeException e) {
                            Log.w(TAG, "Échec du marqueur CoT", e);
                        }
                    }
                    UiListener l = ui;
                    if (l != null) l.onAlert(event);
                }
            });
        }
    };
}
