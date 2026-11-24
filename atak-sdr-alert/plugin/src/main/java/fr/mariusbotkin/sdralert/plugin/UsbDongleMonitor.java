package fr.mariusbotkin.sdralert.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Détecte la présence d'une clé RTL2832U sur le port USB OTG (API USB Host).
 * Le plugin n'ouvre pas la clé lui-même : c'est l'application « RTL2832U driver »
 * qui demande la permission USB et sert les échantillons via rtl_tcp.
 */
public class UsbDongleMonitor {

    public interface Listener {
        void onDongleChanged(UsbDevice deviceOrNull);
    }

    /** Couples VID/PID connus pour les clés RTL2832U (NESDR Smart, RTL-SDR Blog, etc.). */
    private static final int[][] KNOWN = {
            {0x0bda, 0x2838}, // RTL2838 générique, NooElec NESDR Smart, RTL-SDR Blog V3/V4
            {0x0bda, 0x2832}, // RTL2832U générique
            {0x0ccd, 0x00a9}, // Terratec Cinergy T Stick Black
            {0x0ccd, 0x00b3}, // Terratec NOXON DAB/DAB+
            {0x0ccd, 0x00d3}, // Terratec Cinergy T Stick RC
            {0x185b, 0x0620}, // Compro Videomate U620F
            {0x1d19, 0x1101}, // Dexatek DK DVB-T
            {0x1f4d, 0xb803}, // GTek T803
            {0x1b80, 0xd3a4}, // Twintech UT-40
    };

    private final Context context;
    private final UsbManager usb;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile UsbDevice current;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            refresh();
        }
    };

    public UsbDongleMonitor(Context hostContext) {
        this.context = hostContext;
        this.usb = (UsbManager) hostContext.getSystemService(Context.USB_SERVICE);
    }

    public static boolean isRtlSdr(UsbDevice d) {
        for (int[] k : KNOWN) {
            if (d.getVendorId() == k[0] && d.getProductId() == k[1]) return true;
        }
        return false;
    }

    public void start() {
        IntentFilter f = new IntentFilter();
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, f, 0x2 /* Context.RECEIVER_EXPORTED */);
        } else {
            context.registerReceiver(receiver, f);
        }
        registered = true;
        refresh();
    }

    public void stop() {
        if (registered) {
            try {
                context.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
            }
            registered = false;
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
        l.onDongleChanged(current);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public UsbDevice current() {
        return current;
    }

    public boolean hasPermission() {
        UsbDevice d = current;
        return d != null && usb != null && usb.hasPermission(d);
    }

    public void refresh() {
        UsbDevice found = null;
        if (usb != null) {
            for (UsbDevice d : usb.getDeviceList().values()) {
                if (isRtlSdr(d)) {
                    found = d;
                    break;
                }
            }
        }
        current = found;
        for (Listener l : listeners) l.onDongleChanged(found);
    }

    public static String describe(UsbDevice d) {
        if (d == null) return "Clé RTL-SDR : non branchée";
        String name = d.getProductName() != null ? d.getProductName() : "RTL2832U";
        return String.format(Locale.ROOT, "Clé RTL-SDR : %s (%04x:%04x)", name, d.getVendorId(), d.getProductId());
    }
}
