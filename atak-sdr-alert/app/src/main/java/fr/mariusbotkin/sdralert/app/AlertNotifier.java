package fr.mariusbotkin.sdralert.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import fr.mariusbotkin.sdralert.core.detect.AlertEvent;

import java.util.Locale;

/** Notification + son + vibration pour une alerte. */
public final class AlertNotifier {
    static final String CHANNEL_ALERTS = "sdralert_alerts";
    static final String CHANNEL_SERVICE = "sdralert_service";
    private static final int BASE_ID = 0x5D1200;

    private final Context context;
    private final NotificationManager nm;
    private ToneGenerator tone;

    public AlertNotifier(Context ctx) {
        this.context = ctx;
        this.nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels(ctx);
    }

    static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS, "Alertes radio",
                NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Activité détectée sur une bande surveillée");
        alerts.enableVibration(false); // vibration gérée par l'appli (réglable)
        alerts.setSound(null, null);
        nm.createNotificationChannel(alerts);
        NotificationChannel svc = new NotificationChannel(CHANNEL_SERVICE, "Surveillance en cours",
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(svc);
    }

    static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("deprecation")
    static Notification.Builder builder(Context ctx, String channel) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, channel) : new Notification.Builder(ctx);
    }

    public void notify(AlertEvent e, boolean sound, boolean vibrate) {
        String title = "Activité " + e.band.id.toUpperCase(Locale.ROOT) + " — " + e.channel.name;
        String text = String.format(Locale.ROOT, "%.5f MHz, +%.1f dB au-dessus du bruit",
                e.channel.centerHz / 1e6, e.snrDb());
        if (nm != null) {
            Notification.Builder b = builder(context, CHANNEL_ALERTS)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(openApp(context))
                    .setAutoCancel(true)
                    .setWhen(e.timeMs)
                    .setShowWhen(true);
            try {
                nm.notify(BASE_ID + Math.abs(e.channel.name.hashCode() % 1000), b.build());
            } catch (SecurityException ignored) {
                // Notifications refusées : son et vibration restent actifs.
            }
        }
        if (sound) beep();
        if (vibrate) vibrate();
    }

    private void beep() {
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400);
        } catch (RuntimeException ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate() {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        long[] pattern = {0, 250, 120, 250};
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            v.vibrate(pattern, -1);
        }
    }

    public void release() {
        if (tone != null) {
            tone.release();
            tone = null;
        }
    }
}
