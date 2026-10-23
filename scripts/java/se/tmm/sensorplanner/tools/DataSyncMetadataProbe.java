package se.tmm.sensorplanner.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import se.tmm.sensorplanner.core.*;

/** Offline synthetic experiment only; never opens a network or a local draft store. */
public final class DataSyncMetadataProbe {
    private static final String[] CASES = {"mini", "scout", "pir", "gateway", "pir-update"};
    private static final int MAX_BYTES = 262144;

    public static void main(String[] args) {
        try {
            if (args.length == 1 && "self-test".equals(args[0])) selfTest();
            else if (args.length == 2 && "create".equals(args[0])) {
                Path directory = Paths.get(args[1]);
                // Atomic directory creation prevents overwriting any existing fixture set.
                Files.createDirectory(directory);
                Instant envelopeTime = Instant.now();
                for (String name : CASES) {
                    byte[] xml = event(name, envelopeTime);
                    verify(name, xml);
                    Files.write(directory.resolve(name + ".cot"), xml, StandardOpenOption.CREATE_NEW);
                }
                System.out.println("Created five synthetic metadata-only files. Nothing imported or sent.");
            } else if (args.length == 3 && "check".equals(args[0])) {
                Path file = Paths.get(args[2]);
                // Read at most the bound plus one byte, even if the source grows during reading.
                byte[] bytes;
                try (java.io.InputStream input = Files.newInputStream(file)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES + 1 - out.size()))) > 0) {
                        out.write(buffer, 0, count);
                        if (out.size() > MAX_BYTES) throw new IllegalArgumentException();
                    }
                    bytes = out.toByteArray();
                }
                verify(args[1], bytes);
                System.out.println("MATCH: synthetic UUID and TMM metadata preserved. Delivery and storage unproven.");
            } else throw new IllegalArgumentException();
        } catch (Exception rejected) {
            // Returned events may contain private data. Do not print parser text, paths or values.
            System.err.println("Probe failed: invalid command/case, unavailable output/input, or metadata mismatch.");
            System.err.println("Usage: sh scripts/datasync-metadata-probe.sh self-test | create NEW_DIRECTORY | check CASE FILE");
            System.exit(1);
        }
    }

    private static ShareReview.Metadata metadata(String name) {
        int index = Arrays.asList(CASES).indexOf(name);
        require(index >= 0);
        boolean update = index == 4;
        DeviceType type = DeviceType.valueOf(CASES[update ? 2 : index].toUpperCase(java.util.Locale.ROOT));
        Instant created = Instant.parse("2026-01-01T00:00:00.123456789Z");
        TmmDraft draft = new TmmDraft(UUID.fromString(String.format(java.util.Locale.ROOT,
                "00000000-0000-4000-8000-%012d", update ? 3 : index + 1)),
                "TMM-DATASYNC-LAB-" + type.name(), type,
                update ? DeviceStatus.ACTIVE : DeviceStatus.PLANNED, "SYNTHETIC-GROUP-ÅÄÖ",
                created, update ? created.plusSeconds(60) : created,
                update ? created.plusSeconds(30) : null,
                "Synthetic only\nTab:\tåäö < & > " + (update ? "UPDATED" : "BASELINE"),
                new Position(update ? 0.123456789123 : 0.123456789012, -0.234567891234),
                type.supportsBearing() ? (update ? 0.0 : 123.45678901234567) : null, null, 1);
        return new ShareReview(draft, "synthetic-feed-context", "Synthetic feed").metadata;
    }

    private static DocumentBuilder builder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "32");
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
        });
        return builder;
    }

    private static byte[] event(String name, Instant now) throws Exception {
        ShareReview.Metadata expected = metadata(name);
        Document doc = builder().newDocument();
        Element root = doc.createElement("event"); doc.appendChild(root);
        root.setAttribute("version", "2.0"); root.setAttribute("uid", expected.uid.toString());
        root.setAttribute("type", "b-m-p-w"); root.setAttribute("how", "h-g-i-g-o");
        root.setAttribute("time", now.toString()); root.setAttribute("start", now.toString());
        root.setAttribute("stale", now.plusSeconds(3600).toString());
        Element point = doc.createElement("point"); root.appendChild(point);
        Position rounded = ShareLabFields.envelopePosition(expected.position);
        point.setAttribute("lat", Double.toString(rounded.latitude));
        point.setAttribute("lon", Double.toString(rounded.longitude));
        point.setAttribute("hae", "9999999.0"); point.setAttribute("ce", "9999999.0"); point.setAttribute("le", "9999999.0");
        Element detail = doc.createElement("detail"); root.appendChild(detail);
        Element contact = doc.createElement("contact"); detail.appendChild(contact);
        contact.setAttribute("callsign", expected.displayId);
        Element fields = doc.createElement(ShareLabFields.TAG); detail.appendChild(fields);
        for (Map.Entry<String, String> entry : ShareLabFields.encode(expected).entrySet())
            fields.setAttribute(entry.getKey(), entry.getValue());
        return serialize(doc);
    }

    private static byte[] serialize(Document doc) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        javax.xml.transform.Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        return output.toByteArray();
    }

    private static Element singleChild(Element parent, String name) {
        Element found = null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && name.equals(child.getNodeName())) {
                require(found == null && child.getNamespaceURI() == null);
                found = (Element) child;
            }
        }
        require(found != null);
        return found;
    }

    private static void verify(String name, byte[] bytes) throws Exception {
        require(bytes.length > 0 && bytes.length <= MAX_BYTES);
        ShareReview.Metadata expected = metadata(name);
        Document doc = builder().parse(new ByteArrayInputStream(bytes));
        Element event = doc.getDocumentElement();
        require("event".equals(event.getNodeName()) && event.getNamespaceURI() == null);
        require("2.0".equals(event.getAttribute("version")) && "b-m-p-w".equals(event.getAttribute("type")));
        require(expected.uid.toString().equals(event.getAttribute("uid")));
        Element point = singleChild(event, "point");
        Element detail = singleChild(event, "detail");
        Element payload = singleChild(detail, ShareLabFields.TAG);
        require(doc.getElementsByTagName(ShareLabFields.TAG).getLength() == 1);
        for (Node child = payload.getFirstChild(); child != null; child = child.getNextSibling())
            require(child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty());
        Map<String, String> fields = new LinkedHashMap<>();
        NamedNodeMap attributes = payload.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i); require(attribute.getNamespaceURI() == null);
            fields.put(attribute.getNodeName(), attribute.getNodeValue());
        }
        ShareReview.Metadata actual = ShareLabFields.decode(event.getAttribute("uid"),
                new Position(Double.parseDouble(point.getAttribute("lat")), Double.parseDouble(point.getAttribute("lon"))), fields);
        require(actual.uid.equals(expected.uid) && ShareLabFields.encode(actual).equals(ShareLabFields.encode(expected)));
    }

    private static void selfTest() throws Exception {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        for (String name : CASES) {
            byte[] bytes = event(name, now); verify(name, bytes);
            require(Arrays.equals(bytes, event(name, now)));
            Document doc = builder().parse(new ByteArrayInputStream(bytes));
            Element root = doc.getDocumentElement();
            // Host envelope times/contact names may change; the TMM payload must not.
            root.setAttribute("time", now.plusSeconds(3).toString());
            singleChild(singleChild(root, "detail"), "contact").setAttribute("callsign", "HOST-LABEL");
            verify(name, serialize(doc));
        }
        byte[] original = event("pir", now);
        reject("pir-update", original); reject("unknown", original);
        for (String attribute : ShareLabFields.encode(metadata("pir")).keySet()) {
            Document doc = builder().parse(new ByteArrayInputStream(original));
            Element payload = singleChild(singleChild(doc.getDocumentElement(), "detail"), ShareLabFields.TAG);
            payload.removeAttribute(attribute); reject("pir", serialize(doc));
        }
        for (String mutation : new String[]{"uid", "duplicate", "imageRef", "namespace", "text", "position", "bearing"}) {
            Document doc = builder().parse(new ByteArrayInputStream(original));
            Element root = doc.getDocumentElement(); Element detail = singleChild(root, "detail");
            Element payload = singleChild(detail, ShareLabFields.TAG);
            if (mutation.equals("uid")) root.setAttribute("uid", metadata("mini").uid.toString());
            if (mutation.equals("duplicate")) detail.appendChild(payload.cloneNode(true));
            if (mutation.equals("imageRef")) payload.setAttribute("imageRef", "FORBIDDEN");
            if (mutation.equals("namespace")) payload.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:x", "urn:unexpected");
            if (mutation.equals("text")) payload.setTextContent("unexpected");
            if (mutation.equals("position")) singleChild(root, "point").setAttribute("lat", "1.0");
            if (mutation.equals("bearing")) payload.setAttribute("bearingDeg", "1.0");
            reject("pir", serialize(doc));
        }
        Document deep = builder().parse(new ByteArrayInputStream(original));
        Element parent = singleChild(deep.getDocumentElement(), "detail");
        for (int i = 0; i < 40; i++) {
            Element child = deep.createElement("nested"); parent.appendChild(child); parent = child;
        }
        reject("pir", serialize(deep));
        reject("pir", new byte[MAX_BYTES + 1]); reject("pir", new byte[0]);
        reject("pir", "<event>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        reject("pir", "<!DOCTYPE event [<!ENTITY x SYSTEM 'file:///not-read'>]><event>&x;</event>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("Offline probe self-checks passed: five cases, envelope tolerance and rejection boundaries.");
    }

    private static void reject(String name, byte[] bytes) throws Exception {
        boolean rejected = false;
        try { verify(name, bytes); } catch (Exception expected) { rejected = true; }
        require(rejected);
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid synthetic probe");
    }
}
