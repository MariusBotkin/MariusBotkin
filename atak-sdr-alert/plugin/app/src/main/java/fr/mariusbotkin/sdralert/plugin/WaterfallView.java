package fr.mariusbotkin.sdralert.plugin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import fr.mariusbotkin.sdralert.core.band.Band;
import fr.mariusbotkin.sdralert.core.band.Channel;
import fr.mariusbotkin.sdralert.core.detect.ChannelLevelExtractor;
import fr.mariusbotkin.sdralert.core.engine.MonitorEngine;

import java.util.Arrays;
import java.util.Locale;

/**
 * Spectre + cascade de la bande surveillée. Haut : courbe du spectre et repères
 * de canaux (rouge = actif). Bas : cascade défilante, la ligne la plus récente en haut.
 * Créée par code (pas d'inflation XML, pour éviter les soucis de classloader des plugins).
 */
public class WaterfallView extends View {
    private static final int COLUMNS = 512;
    private static final int ROWS = 300;
    private static final float DYNAMIC_RANGE_DB = 45f;

    private final int[] palette = new int[256];
    private final int[] pixels = new int[COLUMNS * ROWS];
    private final Bitmap bitmap = Bitmap.createBitmap(COLUMNS, ROWS, Bitmap.Config.ARGB_8888);
    private int newestRow = 0;
    private final float[] column = new float[COLUMNS];
    private float[] lastColumn;
    private float levelLo = Float.NaN;

    private Band band;
    private long viewLoHz, viewHiHz;
    private boolean[] active;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint idlePaint = new Paint();
    private final Paint activePaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public WaterfallView(Context context) {
        super(context);
        for (int i = 0; i < 256; i++) palette[i] = heat(i / 255f);
        Arrays.fill(pixels, palette[0]);
        tracePaint.setColor(Color.rgb(120, 220, 255));
        tracePaint.setStyle(Paint.Style.STROKE);
        tracePaint.setStrokeWidth(2f);
        idlePaint.setColor(Color.argb(140, 80, 200, 80));
        activePaint.setColor(Color.argb(200, 255, 50, 50));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(22f);
    }

    /** Palette noir → bleu → cyan → jaune → rouge. */
    private static int heat(float t) {
        float r, g, b;
        if (t < 0.25f) { r = 0; g = 0; b = t / 0.25f; }
        else if (t < 0.5f) { r = 0; g = (t - 0.25f) / 0.25f; b = 1; }
        else if (t < 0.75f) { r = (t - 0.5f) / 0.25f; g = 1; b = 1 - (t - 0.5f) / 0.25f; }
        else { r = 1; g = 1 - (t - 0.75f) / 0.25f; b = 0; }
        return Color.rgb((int) (r * 255), (int) (g * 255), (int) (b * 255));
    }

    public void setBand(Band b) {
        if (b == band) return;
        band = b;
        long span = b.spanHz();
        long margin = Math.max(25_000, span / 8);
        viewLoHz = b.lowestHz() - margin;
        viewHiHz = b.highestHz() + margin;
        Arrays.fill(pixels, palette[0]);
        lastColumn = null;
        levelLo = Float.NaN;
        invalidate();
    }

    public void addFrame(MonitorEngine.SpectrumFrame f, Band b) {
        setBand(b);
        int n = f.powerDb.length;
        // Projection des cases FFT sur les colonnes affichées (max par colonne).
        int binLo = ChannelLevelExtractor.binForFrequency(viewLoHz, f.tuneHz, f.sampleRateHz, n);
        int binHi = ChannelLevelExtractor.binForFrequency(viewHiHz, f.tuneHz, f.sampleRateHz, n);
        binLo = Math.max(0, binLo);
        binHi = Math.min(n - 1, Math.max(binLo + 1, binHi));
        double perCol = (binHi - binLo + 1) / (double) COLUMNS;
        for (int c = 0; c < COLUMNS; c++) {
            int a = binLo + (int) (c * perCol);
            int z = Math.max(a, binLo + (int) ((c + 1) * perCol) - 1);
            float m = -200f;
            for (int k = a; k <= z && k < n; k++) m = Math.max(m, f.powerDb[k]);
            column[c] = m;
        }
        // Niveau bas auto : 20e percentile, lissé.
        float[] sorted = column.clone();
        Arrays.sort(sorted);
        float lo = sorted[COLUMNS / 5] - 3f;
        levelLo = Float.isNaN(levelLo) ? lo : levelLo * 0.95f + lo * 0.05f;

        newestRow = (newestRow - 1 + ROWS) % ROWS;
        int base = newestRow * COLUMNS;
        for (int c = 0; c < COLUMNS; c++) {
            int idx = (int) ((column[c] - levelLo) / DYNAMIC_RANGE_DB * 255f);
            pixels[base + c] = palette[Math.max(0, Math.min(255, idx))];
        }
        lastColumn = column.clone();
        active = f.channelActive;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(Color.BLACK);
        if (band == null) {
            canvas.drawText("En attente du récepteur…", 10, 30, textPaint);
            return;
        }
        int specH = (int) (h * 0.3f);

        // Cascade : lignes [newestRow..ROWS) puis [0..newestRow).
        int firstPart = ROWS - newestRow;
        bitmap.setPixels(pixels, newestRow * COLUMNS, COLUMNS, 0, 0, COLUMNS, firstPart);
        if (newestRow > 0) bitmap.setPixels(pixels, 0, COLUMNS, 0, firstPart, COLUMNS, newestRow);
        canvas.save();
        canvas.translate(0, specH);
        canvas.scale(w / (float) COLUMNS, (h - specH) / (float) ROWS);
        canvas.drawBitmap(bitmap, 0, 0, bmpPaint);
        canvas.restore();

        // Repères de canaux.
        for (int i = 0; i < band.channels.size(); i++) {
            Channel ch = band.channels.get(i);
            float x0 = xFor(ch.centerHz - ch.measureBandwidthHz / 2, w);
            float x1 = xFor(ch.centerHz + ch.measureBandwidthHz / 2, w);
            boolean on = active != null && i < active.length && active[i];
            canvas.drawRect(x0, specH - 8, Math.max(x0 + 2, x1), specH, on ? activePaint : idlePaint);
        }

        // Spectre.
        if (lastColumn != null && !Float.isNaN(levelLo)) {
            path.reset();
            for (int c = 0; c < COLUMNS; c++) {
                float x = c * w / (float) COLUMNS;
                float t = (lastColumn[c] - levelLo) / DYNAMIC_RANGE_DB;
                float y = specH - 10 - Math.max(0, Math.min(1, t)) * (specH - 14);
                if (c == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            canvas.drawPath(path, tracePaint);
        }
        canvas.drawText(String.format(Locale.ROOT, "%.3f", viewLoHz / 1e6), 4, 22, textPaint);
        String hi = String.format(Locale.ROOT, "%.3f MHz", viewHiHz / 1e6);
        canvas.drawText(hi, w - textPaint.measureText(hi) - 4, 22, textPaint);
    }

    private float xFor(long hz, int w) {
        return (float) ((hz - viewLoHz) / (double) (viewHiHz - viewLoHz) * w);
    }
}
