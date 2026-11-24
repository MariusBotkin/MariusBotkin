package fr.mariusbotkin.sdralert.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

/** Dernière position connue de l'opérateur (GPS + réseau), pour le marqueur CoT. */
public final class LocationTracker implements LocationListener {
    private static final long MAX_AGE_MS = 5 * 60_000L;

    private final Context context;
    private final LocationManager lm;
    private volatile Location best;
    private boolean started;

    public LocationTracker(Context ctx) {
        this.context = ctx;
        this.lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    public static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void start() {
        if (started || lm == null || !hasPermission(context)) return;
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.getAllProviders().contains(p)) {
                    lm.requestLocationUpdates(p, 5_000, 0, this, Looper.getMainLooper());
                    consider(lm.getLastKnownLocation(p));
                }
            }
            started = true;
        } catch (SecurityException ignored) {
        }
    }

    public void stop() {
        if (lm != null && started) lm.removeUpdates(this);
        started = false;
    }

    /** Position récente (moins de 5 min), ou null. */
    public Location current() {
        Location l = best;
        if (l == null) return null;
        if (System.currentTimeMillis() - l.getTime() > MAX_AGE_MS) return null;
        return l;
    }

    private void consider(Location l) {
        if (l == null) return;
        Location b = best;
        if (b == null || l.getTime() > b.getTime() + 10_000
                || (l.getTime() >= b.getTime() - 10_000 && l.getAccuracy() <= b.getAccuracy())) {
            best = l;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        consider(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
