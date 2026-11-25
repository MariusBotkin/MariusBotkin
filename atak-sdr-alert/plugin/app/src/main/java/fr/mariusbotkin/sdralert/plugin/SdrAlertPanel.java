package fr.mariusbotkin.sdralert.plugin;

import android.content.Context;
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
import com.atakmap.android.maps.MapView;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.engine.MonitorConfig;
import fr.mariusbotkin.sdralert.core.engine.MonitorEngine;

import java.util.List;
import java.util.Locale;

/** Contenu du panneau : état de la clé, marche/arrêt, cascade, réglages, historique. */
final class SdrAlertPanel {
    private static final int MAX_ALERT_ROWS = 30;

    private final Context pluginContext;
    private final MapView mapView;
    private final SdrAlertController controller;
    private final UsbDongleMonitor usb;
    private final View root;
    private final WaterfallView waterfall;
    private final TextView usbStatus, engineStatus;
    private final TextView thresholdLabel, durationLabel, cooldownLabel, gainLabel;
    private final Button startButton;
    private final LinearLayout alertList;
    private final List<Band> bands = Bands.all();

    SdrAlertPanel(Context pluginContext, MapView mapView, SdrAlertController controller, UsbDongleMonitor usb) {
        this.pluginContext = pluginContext;
        this.mapView = mapView;
        this.controller = controller;
        this.usb = usb;
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.sdr_alert_panel, null);

        usbStatus = root.findViewById(R.id.usb_status);
        engineStatus = root.findViewById(R.id.engine_status);
        thresholdLabel = root.findViewById(R.id.threshold_label);
        durationLabel = root.findViewById(R.id.duration_label);
        cooldownLabel = root.findViewById(R.id.cooldown_label);
        gainLabel = root.findViewById(R.id.gain_label);
        startButton = root.findViewById(R.id.btn_start);
        alertList = root.findViewById(R.id.alert_list);

        // Vue créée par code : pas de vue personnalisée dans le XML (classloader du plugin).
        waterfall = new WaterfallView(pluginContext);
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
                refreshButton();
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
                SdrAlertPanel.this.mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        usbStatus.setText(UsbDongleMonitor.describe(d));
                        usbStatus.setTextColor(d != null ? Color.rgb(120, 230, 120) : Color.rgb(255, 170, 60));
                    }
                });
            }
        });
        controller.setUiListener(uiListener);
    }

    View view() {
        return root;
    }

    /** Appelé à chaque ouverture du panneau : resynchronise l'état. */
    void onShown() {
        usb.refresh();
        engineStatus.setText(controller.lastStatus());
        refreshButton();
        alertList.removeAllViews();
        for (AlertEvent e : controller.history()) addAlertRow(e, false);
    }

    private void setupBandSpinner() {
        Spinner sp = root.findViewById(R.id.band_spinner);
        ArrayAdapter<Band> adapter = new ArrayAdapter<>(pluginContext, android.R.layout.simple_spinner_item, bands);
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

    private interface SeekHandler {
        String apply(int progress);
    }

    private void bindSeek(int seekId, final TextView label, int initial, final boolean retune, final SeekHandler h) {
        SeekBar sb = root.findViewById(seekId);
        sb.setProgress(Math.max(0, Math.min(sb.getMax(), initial)));
        label.setText(h.apply(sb.getProgress()));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                label.setText(h.apply(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                controller.settingsChanged(retune); // sauvegarde / ré-accord au relâchement
            }
        });
    }

    private void setupSeekBars() {
        final MonitorConfig cfg = controller.config();
        bindSeek(R.id.threshold_seek, thresholdLabel, (int) cfg.detector.thresholdDb - 3, false, new SeekHandler() {
            @Override
            public String apply(int p) {
                cfg.detector.thresholdDb = 3 + p;
                return String.format(Locale.ROOT, "Seuil : %d dB au-dessus du bruit", 3 + p);
            }
        });
        bindSeek(R.id.duration_seek, durationLabel, (int) (cfg.detector.minDurationMs / 100) - 1, false, new SeekHandler() {
            @Override
            public String apply(int p) {
                cfg.detector.minDurationMs = (p + 1) * 100L;
                return String.format(Locale.ROOT, "Durée minimale : %d ms", (p + 1) * 100);
            }
        });
        bindSeek(R.id.cooldown_seek, cooldownLabel, (int) (cfg.detector.cooldownMs / 5000) - 1, false, new SeekHandler() {
            @Override
            public String apply(int p) {
                cfg.detector.cooldownMs = (p + 1) * 5000L;
                return String.format(Locale.ROOT, "Repos entre alertes : %d s", (p + 1) * 5);
            }
        });
        bindSeek(R.id.gain_seek, gainLabel, cfg.gainTenthsDb < 0 ? 0 : cfg.gainTenthsDb / 10, true, new SeekHandler() {
            @Override
            public String apply(int p) {
                cfg.gainTenthsDb = p == 0 ? -1 : p * 10;
                return p == 0 ? "Gain : automatique" : String.format(Locale.ROOT, "Gain : %d dB", p);
            }
        });
    }

    private void bindCheck(int id, boolean initial, CompoundButton.OnCheckedChangeListener l) {
        CheckBox cb = root.findViewById(id);
        cb.setChecked(initial);
        cb.setOnCheckedChangeListener(l);
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

    private void refreshButton() {
        startButton.setText(controller.isRunning() ? "Arrêter" : "Démarrer");
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
            refreshButton();
        }

        @Override
        public void onSpectrum(MonitorEngine.SpectrumFrame frame) {
            if (waterfall.isShown()) waterfall.addFrame(frame, controller.config().band);
        }

        @Override
        public void onAlert(AlertEvent event) {
            addAlertRow(event, true);
        }
    };
}
