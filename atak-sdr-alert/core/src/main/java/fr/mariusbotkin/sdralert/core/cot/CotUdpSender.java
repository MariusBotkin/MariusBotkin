package fr.mariusbotkin.sdralert.core.cot;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.Charset;

/**
 * Envoie un message CoT en UDP.
 * - {@link #LOCAL_ATAK} : l'entrée UDP par défaut d'ATAK sur le même téléphone (port 4242),
 *   le marqueur n'apparaît que chez soi.
 * - {@link #SA_MULTICAST} : le multicast SA d'ATAK (239.2.3.1:6969), reçu par les ATAK
 *   du même réseau local (Wi-Fi, maillage) — y compris le sien.
 */
public final class CotUdpSender {
    public static final String LOCAL_HOST = "127.0.0.1";
    public static final int LOCAL_PORT = 4242;
    public static final String MULTICAST_HOST = "239.2.3.1";
    public static final int MULTICAST_PORT = 6969;

    public static final CotUdpSender LOCAL_ATAK = new CotUdpSender(LOCAL_HOST, LOCAL_PORT);
    public static final CotUdpSender SA_MULTICAST = new CotUdpSender(MULTICAST_HOST, MULTICAST_PORT);

    private final String host;
    private final int port;

    public CotUdpSender(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void send(String xml) throws IOException {
        byte[] data = xml.getBytes(Charset.forName("UTF-8"));
        InetAddress addr = InetAddress.getByName(host);
        DatagramSocket s = addr.isMulticastAddress() ? new MulticastSocket() : new DatagramSocket();
        try {
            if (s instanceof MulticastSocket) ((MulticastSocket) s).setTimeToLive(1);
            s.send(new DatagramPacket(data, data.length, addr, port));
        } finally {
            s.close();
        }
    }
}
