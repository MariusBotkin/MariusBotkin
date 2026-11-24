package fr.mariusbotkin.sdralert.core.band;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Catalogue des bandes prédéfinies. Ajouter une bande = ajouter une méthode ici. */
public final class Bands {
    private Bands() {}

    /** PMR446 : 16 canaux de 12,5 kHz, de 446,00625 à 446,19375 MHz. */
    public static Band pmr446() {
        List<Channel> ch = new ArrayList<Channel>();
        for (int i = 0; i < 16; i++) {
            ch.add(new Channel("PMR " + (i + 1), 446_006_250L + i * 12_500L, 10_000));
        }
        return new Band("pmr446", "PMR446 (16 canaux)", ch);
    }

    /** LPD433 : 69 canaux de 25 kHz, de 433,075 à 434,775 MHz (talkies LPD, télécommandes). */
    public static Band lpd433() {
        List<Channel> ch = new ArrayList<Channel>();
        for (int i = 0; i < 69; i++) {
            ch.add(new Channel("LPD " + (i + 1), 433_075_000L + i * 25_000L, 20_000));
        }
        return new Band("lpd433", "LPD433 (69 canaux)", ch);
    }

    /** SRD 868 MHz : découpage en tranches de 100 kHz (LoRa, capteurs, télémétrie). */
    public static Band srd868() {
        return slices("srd868", "SRD 868 MHz", 868_000_000L, 869_600_000L, 100_000, 90_000);
    }

    /** ISM 915 MHz : tranches de 200 kHz sur 902,4–904,0 MHz (fenêtre de 1,6 MHz). */
    public static Band ism915() {
        return slices("ism915", "ISM 915 MHz (902–904)", 902_400_000L, 904_000_000L, 200_000, 180_000);
    }

    private static Band slices(String id, String label, long startHz, long stopHz, int stepHz, int bwHz) {
        List<Channel> ch = new ArrayList<Channel>();
        for (long f = startHz + stepHz / 2; f < stopHz; f += stepHz) {
            ch.add(new Channel(String.format(Locale.ROOT, "%.1f", f / 1e6), f, bwHz));
        }
        return new Band(id, label, ch);
    }

    public static List<Band> all() {
        return Collections.unmodifiableList(Arrays.asList(pmr446(), lpd433(), srd868(), ism915()));
    }

    public static Band byId(String id) {
        for (Band b : all()) {
            if (b.id.equalsIgnoreCase(id)) return b;
        }
        throw new IllegalArgumentException("Bande inconnue : " + id);
    }
}
