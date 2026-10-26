package se.tmm.sensorplanner.tools;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Independent offline checker of the fixed v18 canaries; never imports or dispatches content. */
public final class PublicationEnvelopeVerifier {
    private static final String[] CASES = {"mini", "scout", "pir", "gateway"};
    private static final String[] HASHES = {"14c233d2d95eb88d5995b5a5b1df90890d8a3ed4db317c85a495589825f64d33",
            "61459dd1ad63f7d4d72b55d8bd9375c4c1715c5a5c81de9d341ca397f328c7a4",
            "7ffc134004ea1da97a132a0352d686986cb512cf6b2a834771b43939dc07c8bb",
            "c785562ae47b0a4859ffcdcd47abb7bced5d7a76be56a4b450a49aac85fe2bec"};

    public static void main(String[] args) {
        try {
            if (args.length == 1 && "self-test".equals(args[0])) selfTest();
            else if (args.length == 3 && "check".equals(args[0])) {
                verify(args[1], bounded(Paths.get(args[2]), 1048576));
                System.out.println("MATCH: fixed publication canary UUID, type, point and metadata preserved. No receipt acknowledgment.");
            } else throw new IllegalArgumentException();
        } catch (Exception invalid) {
            System.err.println("Envelope check failed: unsupported command/case, unavailable input or metadata mismatch.");
            System.exit(1);
        }
    }

    public static void verify(String name, byte[] bytes) throws Exception {
        Element expected = fixture(name);
        Document doc = parse(bytes); Element event = doc.getDocumentElement();
        require(event.getNamespaceURI() == null && "event".equals(event.getNodeName()));
        require("2.0".equals(event.getAttribute("version")) && "b-m-p-s-p-loc".equals(event.getAttribute("type")));
        require(expected.getAttribute("deviceUid").equals(event.getAttribute("uid")));
        Element detail = single(event, "detail"), point = single(event, "point"), actual = single(detail, "tmmPublication");
        require(doc.getElementsByTagName("tmmPublication").getLength() == 1
                && doc.getElementsByTagName("tmmShareLab").getLength() == 0);
        require(actual.getAttributes().getLength() == 15);
        for (int i = 0; i < expected.getAttributes().getLength(); i++) {
            Node a = expected.getAttributes().item(i); require(a.getNodeValue().equals(actual.getAttribute(a.getNodeName())));
        }
        for (Node n = actual.getFirstChild(); n != null; n = n.getNextSibling()) {
            require(n.getNodeType() == Node.TEXT_NODE && n.getTextContent().trim().isEmpty());
        }
        require(Double.parseDouble(point.getAttribute("lat")) == rounded(expected.getAttribute("latitude"))
                && Double.parseDouble(point.getAttribute("lon")) == rounded(expected.getAttribute("longitude")));
    }

    private static Element fixture(String name) throws Exception {
        int i = Arrays.asList(CASES).indexOf(name); require(i >= 0);
        byte[] bytes = bounded(Paths.get("test-fixtures/synthetic/publication-envelope/" + name + ".xml"), 262144);
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
        require(HASHES[i].equals(hash.toString()));
        return parse(bytes).getDocumentElement();
    }

    private static Document parse(byte[] bytes) throws Exception {
        require(bytes.length > 0 && bytes.length <= 1048576);
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "32");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false); f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException e) throws SAXException { throw e; }
            @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
        });
        Document result = b.parse(new ByteArrayInputStream(bytes)); require("1.0".equals(result.getXmlVersion())); return result;
    }

    private static Element single(Element parent, String name) {
        Element found = null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (name.equals(n.getNodeName())) {
            require(found == null && n instanceof Element && n.getNamespaceURI() == null); found = (Element) n;
        }
        require(found != null); return found;
    }
    private static double rounded(String value) { return Math.round(Double.parseDouble(value) * 1e7) / 1e7; }
    private static byte[] bounded(Path path, int max) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
            while ((n = in.read(b, 0, Math.min(b.length, max + 1 - out.size()))) != -1) {
                out.write(b, 0, n); require(out.size() <= max);
            }
            return out.toByteArray();
        }
    }
    private static byte[] serialize(Document doc) throws Exception {
        TransformerFactory f = TransformerFactory.newInstance(); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        f.newTransformer().transform(new DOMSource(doc), new StreamResult(out)); return out.toByteArray();
    }
    private static void selfTest() throws Exception {
        for (String name : CASES) {
            Element expected = fixture(name); Document d = parse("<event/>".getBytes(StandardCharsets.UTF_8));
            Element e = d.getDocumentElement(); e.setAttribute("version", "2.0"); e.setAttribute("type", "b-m-p-s-p-loc");
            e.setAttribute("uid", expected.getAttribute("deviceUid"));
            Element p = d.createElement("point"); p.setAttribute("lat", Double.toString(rounded(expected.getAttribute("latitude"))));
            p.setAttribute("lon", Double.toString(rounded(expected.getAttribute("longitude")))); e.appendChild(p);
            Element detail = d.createElement("detail"); e.appendChild(detail); detail.appendChild(d.importNode(expected, true));
            byte[] valid = serialize(d); verify(name, valid);
            for (String[] mutation : new String[][]{{"type", "b-m-p-w"}, {"uid", "wrong"}}) {
                String old = e.getAttribute(mutation[0]); e.setAttribute(mutation[0], mutation[1]); rejected(name, serialize(d)); e.setAttribute(mutation[0], old);
            }
            p.setAttribute("lat", "1.0"); rejected(name, serialize(d));
            p.setAttribute("lat", Double.toString(rounded(expected.getAttribute("latitude"))));
            detail.appendChild(d.importNode(expected, true)); rejected(name, serialize(d)); detail.removeChild(detail.getLastChild());
            Element payload = single(detail, "tmmPublication"); payload.setAttribute("imageRef", "forbidden"); rejected(name, serialize(d));
            rejected(name, "<!DOCTYPE event SYSTEM 'file:///not-opened'><event/>".getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("Independent publication envelope checker passed all four canaries and rejection controls. Nothing imported or sent.");
    }
    private static void rejected(String name, byte[] bytes) throws Exception {
        try { verify(name, bytes); throw new AssertionError("Invalid canary accepted"); } catch (Exception expected) { }
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException(); }
}
