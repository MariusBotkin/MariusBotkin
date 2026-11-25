package fr.mariusbotkin.sdralert.plugin;

import android.content.Context;
import android.util.Log;
import android.view.View;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.maps.MapView;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/**
 * Point d'entrée du plugin (assets/plugin.xml), sur le modèle du plugintemplate
 * officiel ATAK-CIV : un bouton dans la barre d'outils ouvre le panneau.
 * La surveillance continue panneau fermé, jusqu'à l'arrêt du plugin.
 */
public class SdrAlertPlugin implements IPlugin {
    private static final String TAG = "SdrAlertPlugin";

    private final Context pluginContext;
    private final IHostUIService uiService;
    private final ToolbarItem toolbarItem;
    private Pane pane;
    private SdrAlertPanel panel;
    private SdrAlertController controller;
    private UsbDongleMonitor usbMonitor;

    public SdrAlertPlugin(IServiceController serviceController) {
        final PluginContextProvider ctxProvider = serviceController.getService(PluginContextProvider.class);
        pluginContext = ctxProvider != null ? ctxProvider.getPluginContext() : null;
        if (pluginContext != null) pluginContext.setTheme(R.style.ATAKPluginTheme);
        uiService = serviceController.getService(IHostUIService.class);

        // L'identifiant fixe permet à ATAK de retrouver le bouton si l'utilisateur le déplace.
        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                })
                .setIdentifier(pluginContext.getPackageName())
                .build();
    }

    @Override
    public void onStart() {
        if (uiService == null) return;
        uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        if (uiService != null) uiService.removeToolbarItem(toolbarItem);
        if (controller != null) controller.shutdown();
        if (usbMonitor != null) usbMonitor.stop();
        controller = null;
        usbMonitor = null;
        panel = null;
        pane = null;
    }

    /** Création paresseuse : la carte ATAK est prête quand l'utilisateur touche le bouton. */
    private boolean ensureCreated() {
        if (controller != null) return true;
        MapView mapView = MapView.getMapView();
        if (mapView == null) {
            Log.w(TAG, "Carte ATAK indisponible");
            return false;
        }
        usbMonitor = new UsbDongleMonitor(mapView.getContext());
        usbMonitor.start();
        controller = new SdrAlertController(mapView, pluginContext);
        panel = new SdrAlertPanel(pluginContext, mapView, controller, usbMonitor);
        return true;
    }

    private void showPane() {
        if (!ensureCreated()) return;
        if (pane == null) {
            View root = panel.view();
            pane = new PaneBuilder(root)
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }
        panel.onShown();
        if (!uiService.isPaneVisible(pane)) {
            uiService.showPane(pane, null);
        }
    }
}
