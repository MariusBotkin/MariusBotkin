package fr.mariusbotkin.sdralert.core.band;

/** Un canal surveillé : fréquence centrale et largeur utile mesurée. */
public final class Channel {
    public final String name;
    public final long centerHz;
    public final int measureBandwidthHz;

    public Channel(String name, long centerHz, int measureBandwidthHz) {
        this.name = name;
        this.centerHz = centerHz;
        this.measureBandwidthHz = measureBandwidthHz;
    }

    @Override
    public String toString() {
        return name + " (" + String.format(java.util.Locale.ROOT, "%.5f", centerHz / 1e6) + " MHz)";
    }
}
