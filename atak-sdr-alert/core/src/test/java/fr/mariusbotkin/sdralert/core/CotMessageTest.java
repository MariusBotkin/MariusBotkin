package fr.mariusbotkin.sdralert.core;

import fr.mariusbotkin.sdralert.core.band.Bands;
import fr.mariusbotkin.sdralert.core.cot.CotMessage;
import fr.mariusbotkin.sdralert.core.cot.CotUdpSender;
import fr.mariusbotkin.sdralert.core.detect.AlertEvent;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import static org.junit.Assert.*;

public class CotMessageTest {
    private static AlertEvent sample() {
        return new AlertEvent(0, Bands.pmr446(), Bands.pmr446().channels.get(2), -40f, -60f);
    }

    @Test
    public void producesValidCotXml() throws Exception {
        long t = 1_764_000_000_000L;
        String xml = CotMessage.forAlert(sample(), "dev<1>", 48.8566, 2.3522, 12, t, 15);
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
        Element ev = d.getDocumentElement();
        assertEquals("event", ev.getTagName());
        assertEquals("a-u-G", ev.getAttribute("type"));
        assertEquals("sdralert-dev<1>-pmr446-PMR_3", ev.getAttribute("uid"));
        assertEquals("2025-11-24T16:00:00.000Z", ev.getAttribute("time"));
        assertEquals("2025-11-24T16:15:00.000Z", ev.getAttribute("stale"));
        Element pt = (Element) ev.getElementsByTagName("point").item(0);
        assertEquals(48.8566, Double.parseDouble(pt.getAttribute("lat")), 1e-6);
        assertEquals(12.0, Double.parseDouble(pt.getAttribute("ce")), 1e-6);
        Element contact = (Element) ev.getElementsByTagName("contact").item(0);
        assertEquals("RF PMR 3", contact.getAttribute("callsign"));
        assertTrue(ev.getElementsByTagName("remarks").item(0).getTextContent().contains("446.03125"));
    }

    @Test
    public void udpSenderDeliversDatagram() throws Exception {
        DatagramSocket rx = new DatagramSocket(0);
        rx.setSoTimeout(3000);
        new CotUdpSender("127.0.0.1", rx.getLocalPort()).send("<event/>");
        DatagramPacket p = new DatagramPacket(new byte[1024], 1024);
        rx.receive(p);
        rx.close();
        assertEquals("<event/>", new String(p.getData(), 0, p.getLength(), "UTF-8"));
    }
}
