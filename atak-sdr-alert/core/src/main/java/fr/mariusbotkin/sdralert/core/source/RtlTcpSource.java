package fr.mariusbotkin.sdralert.core.source;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Client du protocole rtl_tcp.
 *
 * Sur Android, le serveur est fourni par l'application « RTL2832U driver »
 * (marto.rtl_tcp_andro), qui gère elle-même l'accès USB à la clé. Sur PC on
 * lance simplement {@code rtl_tcp -a 127.0.0.1}.
 *
 * Protocole : à la connexion le serveur envoie 12 octets ("RTL0", type de
 * tuner, nombre de gains), puis un flux continu d'octets I/Q non signés. Les
 * commandes sont des paquets de 5 octets : code + paramètre uint32 big-endian.
 */
public final class RtlTcpSource implements IqSource {
    private static final int CMD_FREQ = 0x01;
    private static final int CMD_SAMPLE_RATE = 0x02;
    private static final int CMD_GAIN_MODE = 0x03;
    private static final int CMD_GAIN = 0x04;
    private static final int CMD_PPM = 0x05;
    private static final int CMD_AGC = 0x08;

    private static final String[] TUNERS = {"inconnu", "E4000", "FC0012", "FC0013", "FC2580", "R820T", "R828D"};

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int tunerType = -1;
    private int gainCount = -1;

    public RtlTcpSource(String host, int port, int connectTimeoutMs) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
    }

    @Override
    public void open() throws IOException {
        Socket s = new Socket();
        s.setTcpNoDelay(true);
        s.setReceiveBufferSize(1 << 20);
        s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        s.setSoTimeout(5_000);
        socket = s;
        in = s.getInputStream();
        out = s.getOutputStream();
        byte[] hdr = new byte[12];
        new DataInputStream(in).readFully(hdr);
        if (hdr[0] != 'R' || hdr[1] != 'T' || hdr[2] != 'L' || hdr[3] != '0') {
            close();
            throw new IOException("Le serveur sur " + host + ":" + port + " n'est pas un serveur rtl_tcp");
        }
        tunerType = beInt(hdr, 4);
        gainCount = beInt(hdr, 8);
    }

    @Override
    public Format format() {
        return Format.U8_IQ;
    }

    public String tunerName() {
        return tunerType >= 0 && tunerType < TUNERS.length ? TUNERS[tunerType] : "inconnu";
    }

    public int gainCount() {
        return gainCount;
    }

    @Override
    public void setCenterFrequency(long hz) throws IOException {
        send(CMD_FREQ, (int) hz);
    }

    @Override
    public void setSampleRate(int hz) throws IOException {
        send(CMD_SAMPLE_RATE, hz);
    }

    @Override
    public void setGain(int tenthsDb) throws IOException {
        if (tenthsDb < 0) {
            send(CMD_GAIN_MODE, 0);
            send(CMD_AGC, 1);
        } else {
            send(CMD_AGC, 0);
            send(CMD_GAIN_MODE, 1);
            send(CMD_GAIN, tenthsDb);
        }
    }

    @Override
    public void setPpmCorrection(int ppm) throws IOException {
        send(CMD_PPM, ppm);
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        return in.read(buf, off, len);
    }

    @Override
    public String describe() {
        return "RTL-SDR (" + tunerName() + ") via rtl_tcp " + host + ":" + port;
    }

    private synchronized void send(int cmd, int param) throws IOException {
        if (out == null) throw new IOException("Source non ouverte");
        byte[] p = {(byte) cmd, (byte) (param >>> 24), (byte) (param >>> 16), (byte) (param >>> 8), (byte) param};
        out.write(p);
        out.flush();
    }

    private static int beInt(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    @Override
    public void close() {
        Socket s = socket;
        socket = null;
        in = null;
        out = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }
}
