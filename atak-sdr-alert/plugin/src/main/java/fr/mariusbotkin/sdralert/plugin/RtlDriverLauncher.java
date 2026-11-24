package fr.mariusbotkin.sdralert.plugin;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.Locale;

/**
 * Lance l'application « RTL2832U driver » (marto.rtl_tcp_andro, Play Store / F-Droid)
 * via son intent {@code iqsrc://}. Elle demande la permission USB, ouvre la clé et
 * démarre un serveur rtl_tcp local auquel le plugin se connecte.
 *
 * C'est le même pilote que celui utilisé par RF Analyzer ou SDR Touch :
 * une seule application à la fois peut utiliser la clé, il faut donc fermer
 * RF Analyzer avant de lancer la surveillance.
 */
public final class RtlDriverLauncher {
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 14423;
    private static final int REQUEST_CODE = 0x5D12;

    private RtlDriverLauncher() {}

    /** @return null si le lancement a réussi, sinon un message d'erreur. */
    public static String launch(Context hostContext, long tuneHz, int sampleRateHz) {
        String args = String.format(Locale.ROOT, "-a %s -p %d -n 1 -f %d -s %d",
                HOST, PORT, tuneHz, sampleRateHz);
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("iqsrc://" + args));
        try {
            if (hostContext instanceof Activity) {
                ((Activity) hostContext).startActivityForResult(i, REQUEST_CODE);
            } else {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                hostContext.startActivity(i);
            }
            return null;
        } catch (ActivityNotFoundException e) {
            return "Application « RTL2832U driver » introuvable : installez-la (Play Store ou F-Droid).";
        } catch (RuntimeException e) {
            return "Impossible de lancer le pilote : " + e.getMessage();
        }
    }
}
