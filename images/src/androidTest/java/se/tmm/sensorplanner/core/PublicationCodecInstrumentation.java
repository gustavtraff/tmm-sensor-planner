package se.tmm.sensorplanner.core;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.os.Build;
import android.os.Bundle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import javax.xml.parsers.SAXParserFactory;

/** Standalone synthetic parser probe; neither starts ATAK nor opens its storage. */
@TargetApi(26)
public final class PublicationCodecInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(Build.VERSION.SDK_INT >= 26);
            String parser = SAXParserFactory.newInstance().newSAXParser().getXMLReader().getClass().getName();
            check(parser.equals("org.apache.harmony.xml.ExpatReader"));
            String[] names = {"mini", "scout", "pir", "gateway"};
            for (int i = 0; i < names.length; i++) {
                byte[] bytes;
                try (InputStream input = getContext().getAssets().open(names[i] + "-v1.xml")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int n;
                    while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
                    bytes = out.toByteArray();
                }
                ShareReview.Metadata m = PublicationCodec.decode(new ByteArrayInputStream(bytes));
                check(m.uid.toString().equals("00000000-0000-4000-8014-00000000000" + (i + 1)));
                check(m.type == new DeviceType[]{DeviceType.MINI, DeviceType.SCOUT, DeviceType.PIR, DeviceType.GATEWAY}[i]);
                check(m.createdAt.equals(Instant.parse("2026-01-01T00:00:00.123456789Z")));
                check(m.owner.equals("SYNTHETIC ROLE ÅÄÖ"));
                if (i == 1) check(Double.doubleToRawLongBits(m.position.latitude) == Long.MIN_VALUE);
                if (i == 2) {
                    check(m.position.latitude == 0.123456789012 && m.position.longitude == -0.234567891234);
                    check(m.bearingDeg == 123.45678901234567);
                    check(m.comment.equals("Synthetic only\nTab:\tåäö < & > 🌲"));
                }
                TmmDraft draft = new TmmDraft(m.uid, m.displayId, m.type, m.status, m.owner, m.createdAt,
                        m.updatedAt, m.deployedAt, m.comment, m.position, m.bearingDeg, "synthetic-local-image", 1);
                check(Arrays.equals(bytes, PublicationCodec.encode(new PublicationPreparationReview(draft).prepare(draft))));
                String xml = new String(bytes, StandardCharsets.UTF_8);
                reject(xml.replace("schemaVersion=\"1\"", "schemaVersion=\"2\""));
                reject(xml.replace("tmmPublication ", "tmmPublication imageRef=\"forbidden\" "));
                reject(xml.replace("tmmPublication ", "tmmPublication xmlns=\"\" "));
                reject(xml.replace(" />", "><nested/></tmmPublication>"));
                reject(xml + "<!-- hidden -->");
                reject(xml + "<?unexpected value?>");
                reject("<!DOCTYPE tmmPublication SYSTEM 'file:///not-opened'>" + xml.substring(xml.indexOf("<tmmPublication")));
            }
            result.putString("result", "PASS"); result.putString("parser", parser);
            result.putString("scope", "Four independent fixtures, exact re-encoding and 28 malformed cases; no ATAK/data/transport");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "FAIL"); result.putString("errorType", failure.getClass().getSimpleName());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void reject(String xml) throws Exception {
        try { PublicationCodec.decode(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))); throw new AssertionError(); }
        catch (IOException expected) { check(expected.getCause() == null); }
    }
    private static void check(boolean valid) { if (!valid) throw new AssertionError(); }
}
