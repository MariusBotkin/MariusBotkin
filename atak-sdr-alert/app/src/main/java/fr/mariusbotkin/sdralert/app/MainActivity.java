package fr.mariusbotkin.sdralert.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import fr.mariusbotkin.sdralert.core.engine.MonitorConfig;
import fr.mariusbotkin.sdralert.core.engine.MonitorEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Écran principal : état, marche/arrêt, cascade, réglages, historique des alertes. */
public class MainActivity extends Activity {
    private static final int MAX_ALERT_ROWS = 50;
    private static final int PERM_REQUEST = 7;

    private AppState state;
    private UsbDongleMonitor usb;
    private WaterfallView waterfall;
    private TextView usbStatus, engineStatus;
    private Button startButton;
    private LinearLayout alertList;
    private final List<Band> bands = Bands.all();
    private boolean startAfterPermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        usb = new UsbDongleMonitor(this);
        setContentView(buildUi());
    }

    // ------------------------------------------------------------------ UI

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sizeSp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    private View buildUi() {
        final ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(16, 20, 26));
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        final int pad = dp(12);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);
        // Android 15 : affichage bord à bord, on décale le contenu sous la barre d'état.
        if (Build.VERSION.SDK_INT >= 20) {
            scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                @SuppressWarnings("deprecation")
                public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                    col.setPadding(pad, pad + in.getSystemWindowInsetTop(), pad, pad + in.getSystemWindowInsetBottom());
                    return in;
                }
            });
        }

        TextView title = text("SDR Alerte", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);
        usbStatus = text("Clé RTL-SDR : recherche…", 14, Color.WHITE);
        col.addView(usbStatus);
        engineStatus = text(state.lastStatus(), 13, Color.rgb(180, 180, 180));
        col.addView(engineStatus);

        LinearLayout buttons = new LinearLayout(this);
        startButton = new Button(this);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (state.isRunning()) {
                    MonitorService.stop(MainActivity.this);
                    engineStatus.setText("Arrêt…");
                } else {
                    requestPermissionsThenStart();
                }
                refreshButton();
            }
        });
        Button driver = new Button(this);
        driver.setText("Relancer pilote");
        driver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchDriver();
            }
        });
        buttons.addView(startButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        buttons.addView(driver, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        col.addView(buttons);

        waterfall = new WaterfallView(this);
        waterfall.setBand(state.config.band);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280));
        wl.topMargin = dp(6);
        col.addView(waterfall, wl);

        col.addView(section("Bande"));
        col.addView(bandSpinner());
        addSeekBars(col);

        col.addView(section("Alertes"));
        final AppSettings s = state.settings;
        col.addView(check("Son", s.sound, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                s.sound = v;
                state.settingsChanged(false);
            }
        }));
        col.addView(check("Vibration", s.vibrate, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                s.vibrate = v;
                state.settingsChanged(false);
            }
        }));
        col.addView(check("Marqueur dans ATAK sur ce téléphone", s.cotLocal, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                s.cotLocal = v;
                state.settingsChanged(false);
            }
        }));
        col.addView(check("Partager le marqueur sur le réseau local (multicast SA)", s.cotShare,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        s.cotShare = v;
                        state.settingsChanged(false);
                    }
                }));

        LinearLayout histHeader = new LinearLayout(this);
        histHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = section("Dernières alertes");
        histHeader.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button clear = new Button(this);
        clear.setText("Effacer");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                state.clearHistory();
                alertList.removeAllViews();
            }
        });
        histHeader.addView(clear);
        col.addView(histHeader);
        alertList = new LinearLayout(this);
        alertList.setOrientation(LinearLayout.VERTICAL);
        col.addView(alertList);

        col.addView(text("\nFermer RF Analyzer avant de démarrer : la clé ne peut servir qu'à une appli à la fois. "
                + "Le marqueur est posé à votre position (type inconnu), pas à celle de l'émetteur.",
                12, Color.rgb(140, 140, 140)));
        refreshButton();
        return scroll;
    }

    private TextView section(String s) {
        TextView t = text(s, 16, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private CheckBox check(String label, boolean initial, CompoundButton.OnCheckedChangeListener l) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(Color.WHITE);
        cb.setChecked(initial);
        cb.setOnCheckedChangeListener(l);
        return cb;
    }

    private Spinner bandSpinner() {
        Spinner sp = new Spinner(this);
        ArrayAdapter<Band> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bands);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        for (int i = 0; i < bands.size(); i++) {
            if (bands.get(i).id.equals(state.config.band.id)) sp.setSelection(i);
        }
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Band b = bands.get(position);
                if (b.id.equals(state.config.band.id)) return;
                state.config.band = b;
                state.settingsChanged(true);
                waterfall.setBand(b);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return sp;
    }

    private interface SeekHandler {
        String apply(int progress);
    }

    private void seek(LinearLayout col, int max, int initial, final boolean retune, final SeekHandler h) {
        final TextView label = text("", 14, Color.WHITE);
        label.setPadding(0, dp(6), 0, 0);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(Math.max(0, Math.min(max, initial)));
        label.setText(h.apply(sb.getProgress()));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                label.setText(h.apply(p));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                state.settingsChanged(retune); // une seule sauvegarde au relâchement
            }
        });
        col.addView(label);
        col.addView(sb);
    }

    private void addSeekBars(LinearLayout col) {
        final MonitorConfig cfg = state.config;
        seek(col, 27, (int) cfg.detector.thresholdDb - 3, false, new SeekHandler() {
            @Override
            public String apply(int p) {
                cfg.detector.thresholdDb = 3 + p;
                return String.format(Locale.ROOT, "Seuil : %d dB au-dessus du bruit", 3 + p);
            }
        });
        seek(col, 29, (int) (cfg.detector.minDurationMs / 100) - 1, false, new SeekHandler() {
            @Override
            public String apply(int p) {
                cfg.detector.minDurationMs = (p + 1) * 100L;
                return String.format(Locale.ROOT, "Durée minimale : %d ms", (p + 1) * 100);
            }
        });
        seek(col, 59, (int) (cfg.detector.cooldownMs / 5000) - 1, false, new SeekHandler() {
            @Override
            public String apply(int p) {
                cfg.detector.cooldownMs = (p + 1) * 5000L;
                return String.format(Locale.ROOT, "Repos entre alertes (par canal) : %d s", (p + 1) * 5);
            }
        });
        seek(col, 50, cfg.gainTenthsDb < 0 ? 0 : cfg.gainTenthsDb / 10, true, new SeekHandler() {
            @Override
            public String apply(int p) {
                cfg.gainTenthsDb = p == 0 ? -1 : p * 10;
                return p == 0 ? "Gain : automatique" : String.format(Locale.ROOT, "Gain : %d dB", p);
            }
        });
    }

    private void refreshButton() {
        startButton.setText(state.isRunning() ? "Arrêter" : "Démarrer");
    }

    private void addAlertRow(AlertEvent e, boolean atTop) {
        TextView tv = text(e.summary(), 14, Color.rgb(255, 120, 120));
        tv.setPadding(0, dp(3), 0, dp(3));
        alertList.addView(tv, atTop ? 0 : alertList.getChildCount());
        while (alertList.getChildCount() > MAX_ALERT_ROWS) alertList.removeViewAt(alertList.getChildCount() - 1);
    }

    // ------------------------------------------------------------ Démarrage

    private void requestPermissionsThenStart() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        AppSettings s = state.settings;
        if ((s.cotLocal || s.cotShare) && !LocationTracker.hasPermission(this)) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!needed.isEmpty()) {
            startAfterPermissions = true;
            requestPermissions(needed.toArray(new String[0]), PERM_REQUEST);
            return;
        }
        startMonitoring();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERM_REQUEST && startAfterPermissions) {
            startAfterPermissions = false;
            startMonitoring(); // on démarre même si une permission est refusée
        }
    }

    private void startMonitoring() {
        // Le service se connecte en boucle au pilote : l'ordre n'a pas d'importance.
        MonitorService.start(this);
        launchDriver();
        refreshButton();
    }

    private void launchDriver() {
        String err = RtlDriverLauncher.launch(this, state.config.effectiveTuneHz(), state.config.effectiveSampleRate());
        if (err != null) engineStatus.setText(err);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RtlDriverLauncher.REQUEST_CODE) return;
        if (resultCode != RESULT_OK) {
            String msg = data != null ? data.getStringExtra("detailed_exception_message") : null;
            engineStatus.setText("Le pilote RTL-SDR n'a pas démarré" + (msg != null ? " : " + msg : "")
                    + ". Vérifier la clé et la permission USB.");
        }
    }

    // ------------------------------------------------------------ Cycle de vie

    private final AppState.UiListener uiListener = new AppState.UiListener() {
        @Override
        public void onStatus(String status, boolean running) {
            engineStatus.setText(status);
            refreshButton();
        }

        @Override
        public void onSpectrum(MonitorEngine.SpectrumFrame frame) {
            waterfall.addFrame(frame, state.config.band);
        }

        @Override
        public void onAlert(AlertEvent event) {
            addAlertRow(event, true);
        }
    };

    private final UsbDongleMonitor.Listener usbListener = new UsbDongleMonitor.Listener() {
        @Override
        public void onDongleChanged(final UsbDevice d) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    usbStatus.setText(UsbDongleMonitor.describe(d));
                    usbStatus.setTextColor(d != null ? Color.rgb(120, 230, 120) : Color.rgb(255, 170, 60));
                }
            });
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        usb.start();
        usb.addListener(usbListener);
        state.setUiListener(uiListener);
        engineStatus.setText(state.lastStatus());
        refreshButton();
        alertList.removeAllViews();
        for (AlertEvent e : state.history()) addAlertRow(e, false);
    }

    @Override
    protected void onStop() {
        state.setUiListener(null); // plus de rendu de cascade en arrière-plan
        usb.removeListener(usbListener);
        usb.stop();
        super.onStop();
    }
}
