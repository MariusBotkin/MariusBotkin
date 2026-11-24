package fr.mariusbotkin.sdralert.core.cot;

import fr.mariusbotkin.sdralert.core.detect.AlertEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Construit un message CoT (Cursor on Target) XML pour une alerte. Type « a-u-G »
 * (inconnu, sol) : détecter une émission n'est pas identifier son origine.
 * La position est celle de l'opérateur, pas celle de l'émetteur.
 */
public final class CotMessage {
    public static final double UNKNOWN = 9999999.0;

    private CotMessage() {}

    public static String uidFor(String deviceId, AlertEvent e) {
        return "sdralert-" + deviceId + "-" + e.band.id + "-" + e.channel.name.replace(' ', '_');
    }

    public static String forAlert(AlertEvent e, String deviceId, double lat, double lon,
                                  double accuracyM, long nowMs, int staleMinutes) {
        String remarks = String.format(Locale.ROOT,
                "Activité radio détectée (non identifiée) : %s, %s, %.5f MHz, +%.1f dB. "
                        + "Position = opérateur, pas l'émetteur.",
                e.band.label, e.channel.name, e.channel.centerHz / 1e6, e.snrDb());
        return build(uidFor(deviceId, e), "a-u-G", lat, lon,
                accuracyM > 0 ? accuracyM : UNKNOWN, nowMs, nowMs + staleMinutes * 60_000L,
                "RF " + e.channel.name, remarks);
    }

    public static String build(String uid, String type, double lat, double lon, double ce,
                               long timeMs, long staleMs, String callsign, String remarks) {
        String t = iso(timeMs);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<event version=\"2.0\" uid=\"" + esc(uid) + "\" type=\"" + esc(type) + "\" how=\"h-e\""
                + " time=\"" + t + "\" start=\"" + t + "\" stale=\"" + iso(staleMs) + "\">"
                + String.format(Locale.ROOT, "<point lat=\"%.7f\" lon=\"%.7f\" hae=\"%.1f\" ce=\"%.1f\" le=\"%.1f\"/>",
                lat, lon, UNKNOWN, ce, UNKNOWN)
                + "<detail><contact callsign=\"" + esc(callsign) + "\"/>"
                + "<remarks>" + esc(remarks) + "</remarks></detail></event>";
    }

    static String iso(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '&': b.append("&amp;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }
}
