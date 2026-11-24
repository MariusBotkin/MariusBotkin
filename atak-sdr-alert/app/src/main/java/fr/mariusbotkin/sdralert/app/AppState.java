package fr.mariusbotkin.sdralert.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.engine.MonitorConfig;
import fr.mariusbotkin.sdralert.core.engine.MonitorEngine;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * État partagé entre l'écran et le service : réglages, historique, moteur en cours,
 * et relais des événements vers l'écran (toujours sur le thread principal).
 */
public final class AppState {
    private static final int HISTORY_MAX = 100;
    private static AppState instance;

    public interface UiListener {
        void onStatus(String status, boolean running);

        void onSpectrum(MonitorEngine.SpectrumFrame frame);

        void onAlert(AlertEvent event);
    }

    public final MonitorConfig config = new MonitorConfig();
    public final AppSettings settings;
    private final LinkedList<AlertEvent> history = new LinkedList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile UiListener ui;
    private volatile MonitorEngine engine;
    private volatile String lastStatus = "Arrêté";
    private volatile boolean running;

    private AppState(Context app) {
        settings = new AppSettings(app);
        settings.load(config);
    }

    public static synchronized AppState get(Context ctx) {
        if (instance == null) instance = new AppState(ctx.getApplicationContext());
        return instance;
    }

    public void setUiListener(UiListener l) {
        ui = l;
    }

    public boolean hasUi() {
        return ui != null;
    }

    public boolean isRunning() {
        return running;
    }

    public String lastStatus() {
        return lastStatus;
    }

    void setEngine(MonitorEngine e) {
        engine = e;
        running = e != null;
    }

    /** Sauvegarde ; {@code retune} applique bande/gain au récepteur sans couper. */
    public void settingsChanged(boolean retune) {
        settings.save(config);
        MonitorEngine e = engine;
        if (retune && e != null) e.requestReconfigure();
    }

    public synchronized List<AlertEvent> history() {
        return new ArrayList<>(history);
    }

    public synchronized void clearHistory() {
        history.clear();
    }

    synchronized void addAlert(AlertEvent e) {
        history.addFirst(e);
        while (history.size() > HISTORY_MAX) history.removeLast();
    }

    void publishStatus(final String status, final boolean isRunning) {
        lastStatus = status;
        main.post(new Runnable() {
            @Override
            public void run() {
                UiListener l = ui;
                if (l != null) l.onStatus(status, isRunning);
            }
        });
    }

    void publishSpectrum(final MonitorEngine.SpectrumFrame f) {
        if (ui == null) return;
        main.post(new Runnable() {
            @Override
            public void run() {
                UiListener l = ui;
                if (l != null) l.onSpectrum(f);
            }
        });
    }

    void publishAlert(final AlertEvent e) {
        main.post(new Runnable() {
            @Override
            public void run() {
                UiListener l = ui;
                if (l != null) l.onAlert(e);
            }
        });
    }
}
