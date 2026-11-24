package fr.mariusbotkin.sdralert.plugin;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import fr.mariusbotkin.sdralert.core.detect.AlertEvent;

import java.util.Locale;

/**
 * Pose un marqueur CoT à la position de l'opérateur au moment de l'alerte.
 * Type « a-u-G » (inconnu) : détecter une émission n'est pas identifier son origine.
 */
public final class CotAlertMarker {
    private static final int STALE_MINUTES = 15;

    private CotAlertMarker() {}

    /** @return false si la position de l'opérateur n'est pas connue. */
    public static boolean dispatch(MapView mapView, AlertEvent e, boolean shareWithTeam) {
        Marker self = mapView.getSelfMarker();
        if (self == null) return false;
        GeoPoint p = self.getPoint();
        if (p == null || !p.isValid()) return false;

        CoordinatedTime now = new CoordinatedTime();
        CotEvent ev = new CotEvent();
        ev.setUID("sdralert-" + mapView.getSelfMarker().getUID() + "-" + e.band.id + "-" + e.channel.name.replace(' ', '_'));
        ev.setType("a-u-G");
        ev.setHow("h-e");
        ev.setTime(now);
        ev.setStart(now);
        ev.setStale(now.addMinutes(STALE_MINUTES));
        ev.setPoint(new CotPoint(p.getLatitude(), p.getLongitude(), CotPoint.UNKNOWN, CotPoint.UNKNOWN, CotPoint.UNKNOWN));

        CotDetail detail = new CotDetail("detail");
        CotDetail contact = new CotDetail("contact");
        contact.setAttribute("callsign", "RF " + e.channel.name);
        detail.addChild(contact);
        CotDetail remarks = new CotDetail("remarks");
        remarks.setInnerText(String.format(Locale.ROOT,
                "Activité radio détectée (non identifiée) : %s, %.5f MHz, +%.1f dB. Position = opérateur, pas l'émetteur.",
                e.band.label, e.channel.centerHz / 1e6, e.snrDb()));
        detail.addChild(remarks);
        ev.setDetail(detail);

        CotMapComponent.getInternalDispatcher().dispatch(ev);
        if (shareWithTeam) {
            CotMapComponent.getExternalDispatcher().dispatch(ev);
        }
        return true;
    }
}
