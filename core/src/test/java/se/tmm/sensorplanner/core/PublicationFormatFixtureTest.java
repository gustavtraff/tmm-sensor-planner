package se.tmm.sensorplanner.core;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Test-only inspector for a proposed format; no production codec, I/O writer or SDK integration. */
public final class PublicationFormatFixtureTest {
    private static final int MAX_BYTES = 262144;
    private static final Set<String> FIELDS = new HashSet<>(Arrays.asList("schemaVersion", "deviceUid",
            "displayIdUtf8", "type", "status", "ownerUtf8", "createdAt", "updatedAt", "deployedAt",
            "commentUtf8", "latitude", "longitude", "bearingDeg", "bearingReference", "imageIncluded"));

    public static void main(String[] args) throws Exception {
        if (!PublicationFormatFixtureTest.class.desiredAssertionStatus()) throw new IllegalStateException("Enable assertions");
        String[] names = {"mini", "scout", "pir", "gateway"};
        String[] hashes = {"4278f3b72d42c976a53e32de111547513276bbd71359e91c8836b59fbc015c74",
                "223989fdee91f97dd67f00c71d16457cee7341e26365cb3e067814f8ab569224",
                "0f61cecfa1a33ec5f729e83a5c89dda38d0dfcd9e59af44ecdb10ea477aa0f26",
                "97090a7a9aec483c4bc2af54302d8a15911bdd193463c2b4b2a1c79530b1e49f"};
        for (int i = 0; i < names.length; i++) {
            byte[] bytes = Files.readAllBytes(Paths.get("test-fixtures/synthetic/publication/" + names[i] + "-v1.xml"));
            assert hex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(hashes[i]);
            TmmDraft actual = inspect(bytes), expected = expected(i);
            assert ImportReview.assess(Collections.singletonList(expected), actual).kind == ImportReview.Kind.SAME_METADATA;
            assert actual.imageRef == null && actual.revision == 1;
            if (i == 1) assert Double.doubleToRawLongBits(actual.position.latitude) == Long.MIN_VALUE;
        }
        String pir = new String(Files.readAllBytes(Paths.get("test-fixtures/synthetic/publication/pir-v1.xml")), StandardCharsets.UTF_8);
        invalidInputs(pir);
        canonicalAndLimits(pir);
        System.out.println("Publication proposal fixtures passed (independent values, strict profile, limits and no image/local revision).");
    }

    private static TmmDraft expected(int index) {
        DeviceType type = new DeviceType[]{DeviceType.MINI, DeviceType.SCOUT, DeviceType.PIR, DeviceType.GATEWAY}[index];
        DeviceStatus status = new DeviceStatus[]{DeviceStatus.PLANNED, DeviceStatus.DEPLOYED, DeviceStatus.ACTIVE, DeviceStatus.RETRIEVED}[index];
        Instant created = Instant.parse("2026-01-01T00:00:00.123456789Z");
        return new TmmDraft(UUID.fromString(String.format(Locale.ROOT, "00000000-0000-4000-8014-%012d", index + 1)),
                index == 2 ? "PIR 134" : new String[]{"Mini 1", "Scout 1", "", "Gateway 1"}[index], type, status,
                "SYNTHETIC ROLE ÅÄÖ", created, created.plusSeconds(10),
                index == 1 || index == 2 ? Instant.parse("2026-01-01T00:00:05.000000042Z") : null,
                new String[]{"", "Synthetic Scout", "Synthetic only\nTab:\tåäö < & > 🌲", "Synthetic Gateway"}[index],
                new Position(new double[]{0.0, -0.0, 0.123456789012, -90.0}[index],
                        new double[]{1.25, 1.25, -0.234567891234, 180.0}[index]),
                new Double[]{null, 0.0, 123.45678901234567, null}[index], "synthetic-local-image", 99);
    }

    private static void invalidInputs(String valid) throws Exception {
        for (String[] change : new String[][]{
                {"schemaVersion=\"1\"", "schemaVersion=\"2\""},
                {"tmmPublication", "tmmShareLab"}, {"tmmPublication", "x:tmmPublication"},
                {"imageIncluded=\"false\"", "imageIncluded=\"true\""},
                {"bearingReference=\"true-north\"", "bearingReference=\"magnetic\""},
                {"status=\"ACTIVE\"", "status=\"BROKEN\""},
                {"type=\"PIR\"", "type=\"MINI\""},
                {"latitude=\"0.123456789012\"", "latitude=\"NaN\""},
                {"latitude=\"0.123456789012\"", "latitude=\"91.0\""},
                {"longitude=\"-0.234567891234\"", "longitude=\"Infinity\""},
                {"bearingDeg=\"123.45678901234567\"", "bearingDeg=\"360.0\""},
                {"updatedAt=\"2026-01-01T00:00:10.123456789Z\"", "updatedAt=\"2025-01-01T00:00:00Z\""},
                {"createdAt=\"2026-01-01T00:00:00.123456789Z\"", "createdAt=\"2026-01-01T01:00:00.123456789+01:00\""},
                {"deviceUid=\"00000000-0000-4000-8014-000000000003\"", "deviceUid=\"0-0-4000-8014-3\""},
                {"displayIdUtf8=\"UElSIDEzNA==\"", "displayIdUtf8=\"UElSIDEzNA\""},
                {"displayIdUtf8=\"UElSIDEzNA==\"", "displayIdUtf8=\"/w==\""},
                {"displayIdUtf8=\"UElSIDEzNA==\"", "displayIdUtf8=\"\""},
                {"displayIdUtf8=\"UElSIDEzNA==\"", "displayIdUtf8=\"IFBJUiAxMzQ=\""},
                {" schemaVersion=\"1\"", ""}, {"tmmPublication ", "tmmPublication xmlns=\"urn:unexpected\" "},
                {" />", "><extra /></tmmPublication>"}, {" />", ">hidden data</tmmPublication>"},
                {" />", "><!--hidden data--></tmmPublication>"},
                {"encoding='utf-8'", "encoding='utf-16'"}, {"version='1.0'", "version='1.1'"}}) {
            assert valid.contains(change[0]) : change[0];
            reject(valid.replace(change[0], change[1]));
        }
        for (String field : new String[]{"imageRef", "imagePath", "revision", "contextId", "sourceId", "shared", "experiment"}) {
            reject(valid.replace("tmmPublication ", "tmmPublication " + field + "=\"forbidden\" "));
        }
        String root = valid.substring(valid.indexOf("<tmmPublication"));
        reject("<!DOCTYPE tmmPublication SYSTEM 'file:///not-read'>" + root);
        reject("<!DOCTYPE tmmPublication [<!ENTITY x 'unexpected'>]>" + root);
        reject(valid + root);
        reject(valid + "<!-- hidden -->");
        reject(valid + "<?unexpected value?>");
        reject(valid.replace("schemaVersion=\"1\"", "schemaVersion=\"1\" schemaVersion=\"1\""));
        reject("\ufeff" + valid);
        reject(Arrays.copyOf(valid.getBytes(StandardCharsets.UTF_8), 12));
        reject(new byte[]{(byte) 0xc3, 0x28});
        reject(new byte[MAX_BYTES + 1]);
    }

    private static void canonicalAndLimits(String valid) throws Exception {
        TmmDraft base = inspect(valid.getBytes(StandardCharsets.UTF_8));
        String reordered = valid.replace("schemaVersion=\"1\" ", "").replace(" />", " schemaVersion='1'/> ");
        assert ImportReview.assess(Collections.singletonList(base), inspect(reordered.getBytes(StandardCharsets.UTF_8)))
                .kind == ImportReview.Kind.SAME_METADATA;
        String zero = valid.replace("latitude=\"0.123456789012\"", "latitude=\"-0.0\"")
                .replace("bearingDeg=\"123.45678901234567\"", "bearingDeg=\"-0.0\"");
        TmmDraft negativeZero = inspect(zero.getBytes(StandardCharsets.UTF_8));
        TmmDraft positiveZero = inspect(zero.replace("-0.0", "0.0").getBytes(StandardCharsets.UTF_8));
        assert ImportReview.assess(Collections.singletonList(negativeZero), positiveZero).kind == ImportReview.Kind.SAME_METADATA;
        TmmDraft tiny = inspect(valid.replace("latitude=\"0.123456789012\"", "latitude=\"1.0E-7\"")
                .replace("bearingDeg=\"123.45678901234567\"", "bearingDeg=\"4.9E-324\"").getBytes(StandardCharsets.UTF_8));
        assert tiny.position.latitude == 1e-7 && Double.doubleToRawLongBits(tiny.bearingDeg) == 1;
        reject(valid.replace("latitude=\"0.123456789012\"", "latitude=\"0.1234567890120\""));
        String limit = String.join("", Collections.nCopies(65536, "x"));
        assert inspect(withText(valid, "ownerUtf8", limit).getBytes(StandardCharsets.UTF_8)).owner.length() == 65536;
        reject(withText(valid, "ownerUtf8", limit + "x"));
        String unicodeLimit = String.join("", Collections.nCopies(32768, "å"));
        assert inspect(withText(valid, "ownerUtf8", unicodeLimit).getBytes(StandardCharsets.UTF_8)).owner.length() == 32768;
        reject(withText(valid, "ownerUtf8", unicodeLimit + "å"));
        assert inspect(withText(valid, "commentUtf8", String.join("", Collections.nCopies(2000, "x")))
                .getBytes(StandardCharsets.UTF_8)).comment.length() == 2000;
        reject(withText(valid, "commentUtf8", String.join("", Collections.nCopies(2001, "x"))));
        assert base.revision == 1 && base.imageRef == null;
    }

    private static String withText(String xml, String attribute, String value) {
        return xml.replaceAll(attribute + "=\"[^\"]*\"", attribute + "=\""
                + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "\"");
    }

    private static TmmDraft inspect(byte[] bytes) throws Exception {
        require(bytes.length > 0 && bytes.length <= MAX_BYTES);
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        require(!text.startsWith("\ufeff"));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "1");
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External data forbidden"); });
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
        });
        Document doc = builder.parse(new InputSource(new StringReader(text)));
        require("1.0".equals(doc.getXmlVersion()));
        require(doc.getXmlEncoding() == null || "UTF-8".equalsIgnoreCase(doc.getXmlEncoding()));
        Element root = doc.getDocumentElement();
        require("tmmPublication".equals(root.getNodeName()) && root.getNamespaceURI() == null);
        for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling()) require(n == root);
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            require(n.getNodeType() == Node.TEXT_NODE && n.getTextContent().trim().isEmpty());
        }
        NamedNodeMap attributes = root.getAttributes();
        require(attributes.getLength() == FIELDS.size());
        for (int i = 0; i < attributes.getLength(); i++) {
            Node a = attributes.item(i);
            require(a.getNamespaceURI() == null && FIELDS.contains(a.getNodeName()));
            require(a.getNodeValue().length() <= (a.getNodeName().endsWith("Utf8") ? 87384 : 128));
        }
        require("1".equals(root.getAttribute("schemaVersion")) && "false".equals(root.getAttribute("imageIncluded"))
                && "true-north".equals(root.getAttribute("bearingReference")));
        UUID uid = UUID.fromString(root.getAttribute("deviceUid"));
        require(uid.toString().equals(root.getAttribute("deviceUid")));
        String deployed = root.getAttribute("deployedAt"), bearing = root.getAttribute("bearingDeg");
        String id = utf8(root.getAttribute("displayIdUtf8")), owner = utf8(root.getAttribute("ownerUtf8"));
        require(id.equals(id.trim()) && owner.equals(owner.trim()));
        return new TmmDraft(uid, id, DeviceType.valueOf(root.getAttribute("type")),
                DeviceStatus.valueOf(root.getAttribute("status")), owner, instant(root.getAttribute("createdAt")),
                instant(root.getAttribute("updatedAt")), deployed.isEmpty() ? null : instant(deployed),
                utf8(root.getAttribute("commentUtf8")), new Position(number(root.getAttribute("latitude")),
                        number(root.getAttribute("longitude"))), bearing.isEmpty() ? null : number(bearing), null, 1);
    }

    private static String utf8(String value) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(value);
        require(bytes.length <= 65536 && Base64.getEncoder().encodeToString(bytes).equals(value));
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Instant instant(String value) {
        Instant result = Instant.parse(value); require(result.toString().equals(value)); return result;
    }

    private static double number(String value) {
        double result = Double.parseDouble(value); require(Double.isFinite(result) && Double.toString(result).equals(value)); return result;
    }

    private static void reject(String text) throws Exception { reject(text.getBytes(StandardCharsets.UTF_8)); }
    private static void reject(byte[] bytes) throws Exception {
        try { inspect(bytes); throw new AssertionError("Invalid publication proposal accepted"); }
        catch (Exception expected) { }
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid proposal fixture"); }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255)); return out.toString();
    }
}
