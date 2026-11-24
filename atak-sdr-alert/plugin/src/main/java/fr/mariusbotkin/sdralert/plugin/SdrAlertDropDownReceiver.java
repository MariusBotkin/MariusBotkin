package fr.mariusbotkin.sdralert.plugin;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.engine.MonitorConfig;
import fr.mariusbotkin.sdralert.core.engine.MonitorEngine;

import java.util.List;
import java.util.Locale;

/** Panneau latéral : état de la clé, marche/arrêt, cascade, réglages, historique. */
public class SdrAlertDropDownReceiver extends DropDownReceiver implements OnStateListener {
    public static final String SHOW_PLUGIN = "fr.mariusbotkin.sdralert.SHOW_PLUGIN";
    private static final int MAX_ALERT_ROWS = 30;

    private final Context pluginContext;
    private final SdrAlertController controller;
    private final UsbDongleMonitor usb;
    private final View root;
    private final WaterfallView waterfall;
    private final TextView usbStatus, engineStatus;
    private final TextView thresholdLabel, durationLabel, cooldownLabel, gainLabel;
    private final Button startButton;
    private final LinearLayout alertList;
    private final List<Band> bands = Bands.all();

    public SdrAlertDropDownReceiver(MapView mapView, Context context,
                                    SdrAlertController controller, UsbDongleMonitor usb) {
        super(mapView);
        this.pluginContext = context;
        this.controller = controller;
        this.usb = usb;
        root = PluginLayoutInflater.inflate(context, R.layout.sdr_alert_panel, null);

        usbStatus = root.findViewById(R.id.usb_status);
        engineStatus = root.findViewById(R.id.engine_status);
        thresholdLabel = root.findViewById(R.id.threshold_label);
        durationLabel = root.findViewById(R.id.duration_label);
        cooldownLabel = root.findViewById(R.id.cooldown_label);
        gainLabel = root.findViewById(R.id.gain_label);
        startButton = root.findViewById(R.id.btn_start);
        alertList = root.findViewById(R.id.alert_list);

        waterfall = new WaterfallView(context);
        ((FrameLayout) root.findViewById(R.id.waterfall_container)).addView(waterfall,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        waterfall.setBand(controller.config().band);

        setupBandSpinner();
        setupSeekBars();
        setupCheckBoxes();

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (controller.isRunning()) controller.stop(); else controller.start();
                refreshButton(controller.isRunning());
            }
        });
        root.findViewById(R.id.btn_driver).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String err = controller.launchDriver();
                if (err != null) engineStatus.setText(err);
            }
        });
        root.findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.clearHistory();
                alertList.removeAllViews();
            }
        });

        usb.addListener(new UsbDongleMonitor.Listener() {
            @Override
            public void onDongleChanged(final UsbDevice d) {
                getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        usbStatus.setText(UsbDongleMonitor.describe(d));
                        usbStatus.setTextColor(d != null ? Color.rgb(120, 230, 120) : Color.rgb(255, 170, 60));
                    }
                });
            }
        });
    }

    private void setupBandSpinner() {
        Spinner sp = root.findViewById(R.id.band_spinner);
        ArrayAdapter<Band> adapter = new ArrayAdapter<>(pluginContext,
                android.R.layout.simple_spinner_item, bands);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        for (int i = 0; i < bands.size(); i++) {
            if (bands.get(i).id.equals(controller.config().band.id)) sp.setSelection(i);
        }
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                controller.setBand(bands.get(position));
                waterfall.setBand(bands.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupSeekBars() {
        final MonitorConfig cfg = controller.config();
        bindSeek(R.id.threshold_seek, (int) cfg.detector.thresholdDb - 3, new SeekHandler() {
            @Override
            public void apply(int p, boolean fromUser) {
                cfg.detector.thresholdDb = 3 + p;
                thresholdLabel.setText(String.format(Locale.ROOT, "Seuil : %d dB au-dessus du bruit", 3 + p));
                if (fromUser) controller.settingsChanged(false);
            }
        });
        bindSeek(R.id.duration_seek, (int) (cfg.detector.minDurationMs / 100) - 1, new SeekHandler() {
            @Override
            public void apply(int p, boolean fromUser) {
                cfg.detector.minDurationMs = (p + 1) * 100L;
                durationLabel.setText(String.format(Locale.ROOT, "Durée minimale : %d ms", (p + 1) * 100));
                if (fromUser) controller.settingsChanged(false);
            }
        });
        bindSeek(R.id.cooldown_seek, (int) (cfg.detector.cooldownMs / 5000) - 1, new SeekHandler() {
            @Override
            public void apply(int p, boolean fromUser) {
                cfg.detector.cooldownMs = (p + 1) * 5000L;
                cooldownLabel.setText(String.format(Locale.ROOT, "Repos entre alertes : %d s", (p + 1) * 5));
                if (fromUser) controller.settingsChanged(false);
            }
        });
        bindSeek(R.id.gain_seek, cfg.gainTenthsDb < 0 ? 0 : cfg.gainTenthsDb / 10, new SeekHandler() {
            @Override
            public void apply(int p, boolean fromUser) {
                cfg.gainTenthsDb = p == 0 ? -1 : p * 10;
                gainLabel.setText(p == 0 ? "Gain : automatique" : String.format(Locale.ROOT, "Gain : %d dB", p));
                if (fromUser) controller.settingsChanged(true);
            }
        });
    }

    private interface SeekHandler {
        void apply(int progress, boolean fromUser);
    }

    private void bindSeek(int id, int initial, final SeekHandler h) {
        SeekBar sb = root.findViewById(id);
        sb.setProgress(Math.max(0, Math.min(sb.getMax(), initial)));
        h.apply(sb.getProgress(), false);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                h.apply(progress, false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                // Sauvegarde / ré-accord une seule fois, au relâchement.
                h.apply(s.getProgress(), true);
            }
        });
    }

    private void setupCheckBoxes() {
        final SdrAlertSettings s = controller.settings();
        bindCheck(R.id.cb_sound, s.sound, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                s.sound = v;
                controller.settingsChanged(false);
            }
        });
        bindCheck(R.id.cb_vibrate, s.vibrate, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                s.vibrate = v;
                controller.settingsChanged(false);
            }
        });
        bindCheck(R.id.cb_cot, s.cotMarker, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                s.cotMarker = v;
                controller.settingsChanged(false);
            }
        });
        bindCheck(R.id.cb_share, s.shareCot, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                s.shareCot = v;
                controller.settingsChanged(false);
            }
        });
    }

    private void bindCheck(int id, boolean initial, CompoundButton.OnCheckedChangeListener l) {
        CheckBox cb = root.findViewById(id);
        cb.setChecked(initial);
        cb.setOnCheckedChangeListener(l);
    }

    private void refreshButton(boolean running) {
        startButton.setText(running ? "Arrêter" : "Démarrer");
    }

    private void addAlertRow(AlertEvent e, boolean atTop) {
        TextView tv = new TextView(pluginContext);
        tv.setText(e.summary());
        tv.setTextColor(Color.rgb(255, 120, 120));
        tv.setPadding(0, 4, 0, 4);
        alertList.addView(tv, atTop ? 0 : alertList.getChildCount());
        while (alertList.getChildCount() > MAX_ALERT_ROWS) alertList.removeViewAt(alertList.getChildCount() - 1);
    }

    private final SdrAlertController.UiListener uiListener = new SdrAlertController.UiListener() {
        @Override
        public void onStatus(String status, boolean running) {
            engineStatus.setText(status);
            refreshButton(controller.isRunning());
        }

        @Override
        public void onSpectrum(MonitorEngine.SpectrumFrame frame) {
            waterfall.addFrame(frame, controller.config().band);
        }

        @Override
        public void onAlert(AlertEvent event) {
            addAlertRow(event, true);
        }
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SHOW_PLUGIN.equals(intent.getAction())) return;
        usb.refresh();
        engineStatus.setText(controller.lastStatus());
        refreshButton(controller.isRunning());
        alertList.removeAllViews();
        for (AlertEvent e : controller.history()) addAlertRow(e, false);
        showDropDown(root, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
    }

    @Override
    public void onDropDownVisible(boolean visible) {
        // La cascade n'est alimentée que si le panneau est visible ; les alertes, toujours.
        controller.setUiListener(visible ? uiListener : null);
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        controller.setUiListener(null);
    }

    @Override
    public void disposeImpl() {
        controller.setUiListener(null);
    }
}
