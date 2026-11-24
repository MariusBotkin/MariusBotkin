package fr.mariusbotkin.sdralert.plugin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import fr.mariusbotkin.sdralert.core.detect.AlertEvent;

import java.util.Locale;

/** Notification Android + son + vibration pour une alerte. */
public final class AlertNotifier {
    private static final String CHANNEL_ID = "sdralert_alerts";
    private static final int BASE_ID = 0x5D1200;

    private final Context context;
    private final NotificationManager nm;
    private ToneGenerator tone;

    public AlertNotifier(Context hostContext) {
        this.context = hostContext;
        this.nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Alertes SDR",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Activité détectée sur une bande surveillée");
            ch.enableVibration(false); // la vibration est gérée par le plugin (réglable)
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
    }

    public void notify(AlertEvent e, boolean sound, boolean vibrate) {
        String title = "Activité " + e.band.id.toUpperCase(Locale.ROOT) + " — " + e.channel.name;
        String text = String.format(Locale.ROOT, "%.5f MHz, +%.1f dB au-dessus du bruit",
                e.channel.centerHz / 1e6, e.snrDb());
        if (nm != null) {
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);
            // Icône système : les ressources du plugin ne sont pas accessibles au
            // gestionnaire de notifications, qui résout l'icône dans le paquet d'ATAK.
            b.setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setWhen(e.timeMs)
                    .setShowWhen(true);
            try {
                nm.notify(BASE_ID + Math.abs(e.channel.name.hashCode() % 1000), b.build());
            } catch (SecurityException ignored) {
                // Android 13+ sans permission de notification : son/vibration restent actifs.
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
