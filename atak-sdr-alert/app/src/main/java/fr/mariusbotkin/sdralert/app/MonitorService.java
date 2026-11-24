package fr.mariusbotkin.sdralert.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import fr.mariusbotkin.sdralert.core.cot.CotMessage;
import fr.mariusbotkin.sdralert.core.cot.CotUdpSender;
import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.detect.Clock;
import fr.mariusbotkin.sdralert.core.engine.MonitorEngine;
import fr.mariusbotkin.sdralert.core.source.IqSource;
import fr.mariusbotkin.sdralert.core.source.RtlTcpSource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service au premier plan : la surveillance continue pendant qu'ATAK est à l'écran
 * ou que le téléphone est en veille. Notification permanente avec bouton « Arrêter ».
 */
public class MonitorService extends Service {
    private static final String TAG = "SdrMonitorService";
    public static final String ACTION_START = "fr.mariusbotkin.sdralert.START";
    public static final String ACTION_STOP = "fr.mariusbotkin.sdralert.STOP";
    private static final int ONGOING_ID = 0x5D1100;
    private static final int COT_STALE_MINUTES = 15;

    private AppState state;
    private AlertNotifier notifier;
    private LocationTracker location;
    private MonitorEngine engine;
    private PowerManager.WakeLock wakeLock;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private String deviceId;

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, MonitorService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
    }

    public static void stop(Context ctx) {
        ctx.startService(new Intent(ctx, MonitorService.class).setAction(ACTION_STOP));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        state = AppState.get(this);
        notifier = new AlertNotifier(this);
        location = new LocationTracker(this);
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        deviceId = id != null ? id : "inconnu";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            startMonitoring();
        } else {
            stopMonitoring();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification ongoingNotification() {
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, MonitorService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return AlertNotifier.builder(this, AlertNotifier.CHANNEL_SERVICE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Surveillance " + state.config.band.label)
                .setContentText("Appuyer pour ouvrir SDR Alerte")
                .setContentIntent(AlertNotifier.openApp(this))
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Arrêter", stop).build())
                .build();
    }

    private void startMonitoring() {
        boolean withLocation = LocationTracker.hasPermission(this);
        if (Build.VERSION.SDK_INT >= 29) {
            int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            if (withLocation) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            startForeground(ONGOING_ID, ongoingNotification(), types);
        } else {
            startForeground(ONGOING_ID, ongoingNotification());
        }
        if (engine != null && engine.isRunning()) return;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sdralert:monitor");
            wakeLock.acquire();
        }
        if (withLocation) location.start();

        engine = new MonitorEngine(new MonitorEngine.SourceFactory() {
            @Override
            public IqSource create() {
                return new RtlTcpSource(RtlDriverLauncher.HOST, RtlDriverLauncher.PORT, 2_000);
            }
        }, state.config, Clock.SYSTEM, engineListener);
        state.setEngine(engine);
        engine.start();
    }

    private void stopMonitoring() {
        final MonitorEngine e = engine;
        engine = null;
        state.setEngine(null);
        if (e != null) {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    e.stop();
                }
            });
        }
        location.stop();
        if (wakeLock != null) {
            if (wakeLock.isHeld()) wakeLock.release();
            wakeLock = null;
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        state.publishStatus("Arrêté", false);
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        io.shutdown();
        notifier.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendCot(final AlertEvent e) {
        final AppSettings s = state.settings;
        if (!s.cotLocal && !s.cotShare) return;
        Location loc = location.current();
        if (loc == null) {
            state.publishStatus("Alerte " + e.channel.name + " : position GPS inconnue, marqueur non envoyé", true);
            return;
        }
        final String xml = CotMessage.forAlert(e, deviceId, loc.getLatitude(), loc.getLongitude(),
                loc.hasAccuracy() ? loc.getAccuracy() : 0, System.currentTimeMillis(), COT_STALE_MINUTES);
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (s.cotLocal) CotUdpSender.LOCAL_ATAK.send(xml);
                    if (s.cotShare) CotUdpSender.SA_MULTICAST.send(xml);
                } catch (Exception ex) {
                    Log.w(TAG, "Envoi CoT impossible", ex);
                    state.publishStatus("Envoi du marqueur à ATAK impossible : " + ex.getMessage(), true);
                }
            }
        });
    }

    private final MonitorEngine.Listener engineListener = new MonitorEngine.Listener() {
        @Override
        public void onStatus(String status, boolean running) {
            state.publishStatus(status, running);
        }

        @Override
        public void onSpectrum(MonitorEngine.SpectrumFrame frame) {
            state.publishSpectrum(frame);
        }

        @Override
        public void onAlert(AlertEvent event) {
            state.addAlert(event);
            notifier.notify(event, state.settings.sound, state.settings.vibrate);
            sendCot(event);
            state.publishAlert(event);
        }
    };
}
