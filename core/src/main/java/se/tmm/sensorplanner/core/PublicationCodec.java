package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;

/** Metadata XML v1 only; no artifact storage, host import, source authentication or draft application. */
public final class PublicationCodec {
    public static final int MAX_BYTES = 262144;
    private static final String INVALID = "Unsupported or invalid publication metadata";
    private static final Set<String> FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "schemaVersion", "deviceUid", "displayIdUtf8", "type", "status", "ownerUtf8", "createdAt",
            "updatedAt", "deployedAt", "commentUtf8", "latitude", "longitude", "bearingDeg",
            "bearingReference", "imageIncluded")));
    // Validate the declaration separately because SAX version/encoding reporting differs across runtimes.
    private static final Pattern DECLARATION = Pattern.compile("<\\?xml[ \\t\\r\\n]+version[ \\t\\r\\n]*=[ \\t\\r\\n]*(['\"])1\\.0\\1"
            + "(?:[ \\t\\r\\n]+encoding[ \\t\\r\\n]*=[ \\t\\r\\n]*(['\"])(?i:UTF-8)\\2)?"
            + "(?:[ \\t\\r\\n]+standalone[ \\t\\r\\n]*=[ \\t\\r\\n]*(['\"])(?:yes|no)\\3)?[ \\t\\r\\n]*\\?>");

    private PublicationCodec() { }

    /** Encode the explicit metadata projection of a successful preparation, never the review itself. */
    public static byte[] encode(PublicationPreparationReview.Prepared prepared) {
        try {
            ShareReview.Metadata m = Objects.requireNonNull(prepared, "prepared").metadata;
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("schemaVersion", "1"); fields.put("deviceUid", m.uid.toString());
            fields.put("displayIdUtf8", text(m.displayId)); fields.put("type", m.type.name());
            fields.put("status", m.status.name()); fields.put("ownerUtf8", text(m.owner));
            fields.put("createdAt", m.createdAt.toString()); fields.put("updatedAt", m.updatedAt.toString());
            fields.put("deployedAt", m.deployedAt == null ? "" : m.deployedAt.toString());
            fields.put("commentUtf8", text(m.comment)); fields.put("latitude", Double.toString(m.position.latitude));
            fields.put("longitude", Double.toString(m.position.longitude));
            fields.put("bearingDeg", m.bearingDeg == null ? "" : m.bearingDeg.toString());
            fields.put("bearingReference", "true-north"); fields.put("imageIncluded", "false");
            // All values are canonical ASCII scalars or Base64, so raw user text never enters XML syntax.
            StringBuilder xml = new StringBuilder("<?xml version='1.0' encoding='utf-8'?>\n<tmmPublication");
            for (Map.Entry<String, String> field : fields.entrySet()) {
                xml.append(' ').append(field.getKey()).append("=\"").append(field.getValue()).append('"');
            }
            byte[] bytes = xml.append(" />\n").toString().getBytes(StandardCharsets.UTF_8);
            require(bytes.length <= MAX_BYTES);
            return bytes;
        } catch (Exception rejected) { throw new IllegalArgumentException(INVALID); }
    }

    /** Reads at most MAX_BYTES + 1 bytes and leaves the caller's stream open, on success or failure. */
    public static ShareReview.Metadata decode(InputStream input) throws IOException {
        try {
            Objects.requireNonNull(input, "input");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (true) {
                int count = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES + 1 - out.size()));
                if (count < 0) break;
                if (count == 0) {
                    int one = input.read();
                    if (one < 0) break;
                    out.write(one);
                } else out.write(buffer, 0, count);
                require(out.size() <= MAX_BYTES);
            }
            require(out.size() > 0);
            String xml = utf8(out.toByteArray());
            require(!xml.startsWith("\ufeff"));
            // Strict UTF-8/XML 1.0 makes this token unambiguous; reject before any parser can resolve a DTD.
            require(!xml.contains("<!DOCTYPE"));
            if (xml.startsWith("<?xml")) {
                int end = xml.indexOf("?>");
                require(end >= 0 && DECLARATION.matcher(xml.substring(0, end + 2)).matches());
                xml = xml.substring(end + 2);
            }
            Handler handler = new Handler();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            // Required lexical callbacks reject DTDs before declarations, plus comments and CDATA.
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            reader.setContentHandler(handler); reader.setErrorHandler(handler); reader.setEntityResolver(handler);
            reader.parse(new InputSource(new StringReader(xml)));
            require(handler.complete);
            return metadata(handler.fields);
        } catch (Exception rejected) { throw new IOException(INVALID); }
        catch (javax.xml.parsers.FactoryConfigurationError unavailable) { throw new IOException(INVALID); }
    }

    private static ShareReview.Metadata metadata(Map<String, String> f) throws Exception {
        require("1".equals(f.get("schemaVersion")) && "false".equals(f.get("imageIncluded"))
                && "true-north".equals(f.get("bearingReference")));
        UUID uid = UUID.fromString(f.get("deviceUid")); require(uid.toString().equals(f.get("deviceUid")));
        String id = untext(f.get("displayIdUtf8")), owner = untext(f.get("ownerUtf8"));
        require(id.equals(id.trim()) && owner.equals(owner.trim()));
        String deployed = f.get("deployedAt"), bearing = f.get("bearingDeg");
        // Revision 1 is a validation carrier only and is absent from the returned projection.
        return new ShareReview.Metadata(new TmmDraft(uid, id, DeviceType.valueOf(f.get("type")),
                DeviceStatus.valueOf(f.get("status")), owner, instant(f.get("createdAt")), instant(f.get("updatedAt")),
                deployed.isEmpty() ? null : instant(deployed), untext(f.get("commentUtf8")),
                new Position(number(f.get("latitude")), number(f.get("longitude"))),
                bearing.isEmpty() ? null : number(bearing), null, 1));
    }

    private static String text(String value) throws Exception {
        require(value.length() <= 65536);
        ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
        require(bytes.remaining() <= 65536);
        byte[] copy = new byte[bytes.remaining()]; bytes.get(copy);
        return Base64.getEncoder().encodeToString(copy);
    }

    private static String untext(String value) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(value);
        require(bytes.length <= 65536 && Base64.getEncoder().encodeToString(bytes).equals(value));
        return utf8(bytes);
    }

    private static String utf8(byte[] bytes) throws Exception {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Instant instant(String value) {
        Instant result = Instant.parse(value); require(result.toString().equals(value)); return result;
    }

    private static double number(String value) {
        double result = Double.parseDouble(value); require(Double.isFinite(result) && Double.toString(result).equals(value)); return result;
    }

    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException(INVALID); }

    private static final class Handler extends DefaultHandler2 {
        final Map<String, String> fields = new LinkedHashMap<>();
        boolean started, complete;

        @Override public void startElement(String uri, String local, String name, Attributes attributes) throws SAXException {
            if (started || !uri.isEmpty() || !"tmmPublication".equals(name) || attributes.getLength() != FIELDS.size()) throw invalid();
            started = true;
            for (int i = 0; i < attributes.getLength(); i++) {
                String key = attributes.getQName(i), value = attributes.getValue(i);
                if (!attributes.getURI(i).isEmpty() || !FIELDS.contains(key) || fields.containsKey(key)
                        || value.length() > (key.endsWith("Utf8") ? 87384 : 128)) throw invalid();
                fields.put(key, value);
            }
        }
        @Override public void endElement(String uri, String local, String name) { complete = true; }
        @Override public void characters(char[] text, int start, int length) throws SAXException {
            for (int i = start; i < start + length; i++) {
                char c = text[i]; if (c != ' ' && c != '\t' && c != '\r' && c != '\n') throw invalid();
            }
        }
        @Override public void startPrefixMapping(String prefix, String uri) throws SAXException { throw invalid(); }
        @Override public void processingInstruction(String target, String data) throws SAXException { throw invalid(); }
        @Override public void startDTD(String name, String publicId, String systemId) throws SAXException { throw invalid(); }
        @Override public void startCDATA() throws SAXException { throw invalid(); }
        @Override public void comment(char[] text, int start, int length) throws SAXException { throw invalid(); }
        @Override public InputSource resolveEntity(String publicId, String systemId) throws SAXException { throw invalid(); }
        @Override public InputSource resolveEntity(String name, String publicId, String base, String systemId) throws SAXException { throw invalid(); }
        @Override public void error(SAXParseException error) throws SAXException { throw invalid(); }
        @Override public void fatalError(SAXParseException error) throws SAXException { throw invalid(); }
        private SAXException invalid() { return new SAXException(INVALID); }
    }
}
