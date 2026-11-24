package fr.mariusbotkin.sdralert.plugin;

import android.content.Context;

import com.atak.plugins.impl.AbstractPluginTool;

/** Bouton du plugin dans la barre d'outils ATAK : ouvre le panneau. */
public class SdrAlertTool extends AbstractPluginTool {
    public SdrAlertTool(Context context) {
        super(context,
                context.getString(R.string.app_name),
                context.getString(R.string.app_name),
                context.getResources().getDrawable(R.drawable.ic_launcher),
                SdrAlertDropDownReceiver.SHOW_PLUGIN);
    }
}
