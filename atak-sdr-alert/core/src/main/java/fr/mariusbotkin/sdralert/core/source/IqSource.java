package fr.mariusbotkin.sdralert.core.source;

import java.io.Closeable;
import java.io.IOException;

/**
 * Récepteur abstrait. Implémentation actuelle : {@link RtlTcpSource} (RTL-SDR via
 * le protocole rtl_tcp). Plus tard : HackRF, Airspy… sans toucher au reste.
 */
public interface IqSource extends Closeable {

    /** Format des échantillons livrés par {@link #read}. */
    enum Format {
        /** Octets non signés entrelacés I/Q (RTL-SDR). */
        U8_IQ,
        /** Octets signés entrelacés I/Q (HackRF). */
        S8_IQ
    }

    void open() throws IOException;

    Format format();

    void setCenterFrequency(long hz) throws IOException;

    void setSampleRate(int hz) throws IOException;

    /** @param tenthsDb gain en dixièmes de dB, ou négatif pour le gain automatique. */
    void setGain(int tenthsDb) throws IOException;

    void setPpmCorrection(int ppm) throws IOException;

    /** Lecture bloquante ; renvoie le nombre d'octets lus ou -1 en fin de flux. */
    int read(byte[] buf, int off, int len) throws IOException;

    String describe();
}
