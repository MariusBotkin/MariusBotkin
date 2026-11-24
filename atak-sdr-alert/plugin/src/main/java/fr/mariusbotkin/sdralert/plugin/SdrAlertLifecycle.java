package fr.mariusbotkin.sdralert.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import transapps.maps.plugin.lifecycle.Lifecycle;

/** Point d'entrée du plugin (déclaré dans assets/plugin.xml). Calqué sur le plugintemplate du SDK. */
public class SdrAlertLifecycle implements Lifecycle {
    private static final String TAG = "SdrAlertLifecycle";

    private final Context pluginContext;
    private final Collection<MapComponent> overlays = new LinkedList<>();
    private MapView mapView;

    public SdrAlertLifecycle(Context ctx) {
        this.pluginContext = ctx;
    }

    @Override
    public void onCreate(final Activity activity, final transapps.mapi.MapView transappsMapView) {
        if (transappsMapView == null || !(transappsMapView.getView() instanceof MapView)) {
            Log.w(TAG, "Ce plugin ne fonctionne qu'avec la MapView d'ATAK");
            return;
        }
        mapView = (MapView) transappsMapView.getView();
        overlays.add(new SdrAlertMapComponent());
        Iterator<MapComponent> it = overlays.iterator();
        while (it.hasNext()) {
            MapComponent c = it.next();
            try {
                c.onCreate(pluginContext, activity.getIntent(), mapView);
            } catch (Exception e) {
                Log.w(TAG, "Échec de création du composant", e);
                it.remove();
            }
        }
    }

    @Override
    public void onDestroy() {
        for (MapComponent c : overlays) c.onDestroy(pluginContext, mapView);
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        for (MapComponent c : overlays) c.onConfigurationChanged(cfg);
    }

    @Override
    public void onStart() {
        for (MapComponent c : overlays) c.onStart(pluginContext, mapView);
    }

    @Override
    public void onPause() {
        for (MapComponent c : overlays) c.onPause(pluginContext, mapView);
    }

    @Override
    public void onResume() {
        for (MapComponent c : overlays) c.onResume(pluginContext, mapView);
    }

    @Override
    public void onStop() {
        for (MapComponent c : overlays) c.onStop(pluginContext, mapView);
    }

    @Override
    public void onFinish() {
    }
}
