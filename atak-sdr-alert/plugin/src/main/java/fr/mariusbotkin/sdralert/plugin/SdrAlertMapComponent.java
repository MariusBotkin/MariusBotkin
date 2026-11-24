package fr.mariusbotkin.sdralert.plugin;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;

/** Crée le contrôleur SDR et enregistre le panneau latéral. */
public class SdrAlertMapComponent extends DropDownMapComponent {
    private SdrAlertController controller;
    private UsbDongleMonitor usbMonitor;
    private SdrAlertDropDownReceiver dropDown;

    @Override
    public void onCreate(final Context context, Intent intent, final MapView view) {
        context.setTheme(R.style.ATAKPluginTheme);
        super.onCreate(context, intent, view);

        usbMonitor = new UsbDongleMonitor(view.getContext());
        usbMonitor.start();
        controller = new SdrAlertController(view, context);
        dropDown = new SdrAlertDropDownReceiver(view, context, controller, usbMonitor);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(SdrAlertDropDownReceiver.SHOW_PLUGIN,
                "Ouvre le panneau Alerte SDR");
        registerDropDownReceiver(dropDown, filter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (controller != null) controller.shutdown();
        if (usbMonitor != null) usbMonitor.stop();
        super.onDestroyImpl(context, view);
    }
}
