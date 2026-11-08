package se.tmm.sensorplanner.plugin.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
public class ServerTrialConsentUiTest {
    private static final String STREAM = "tak.example.invalid:8089:ssl";
    private List<String> profiles = Arrays.asList(STREAM);
    private boolean available = true;
    private String confirmedStream;
    private boolean[] confirmed;
    private int calls;
    private ServerTrialConsentUi consent;

    @Before public void setup() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        consent = new ServerTrialConsentUi(activity, new ServerTrialConsentUi.Host() {
            @Override public boolean available() { return available; }
            @Override public List<String> profiles() { return profiles; }
            @Override public void confirmed(String stream, boolean[] statements) { calls++; confirmedStream = stream; confirmed = statements; }
        });
    }

    @Test public void everyStatementIsRequiredBeforeProceeding() throws Exception {
        List<CheckBox> boxes = choose();
        assertEquals(4, boxes.size());
        assertFalse(positive().isEnabled());
        for (int i = 0; i < 3; i++) { boxes.get(i).setChecked(true); assertFalse(positive().isEnabled()); }
        boxes.get(3).setChecked(true); assertTrue(positive().isEnabled());
        boxes.get(0).setChecked(false); assertFalse(positive().isEnabled());
        boxes.get(0).setChecked(true);
        positive().performClick(); idle();
        assertEquals(1, calls); assertEquals(STREAM, confirmedStream);
        assertArrayEquals(new boolean[]{true, true, true, true}, confirmed);
        assertFalse(consent.isBusy());
    }

    @Test public void cancelGrantsNothing() throws Exception {
        List<CheckBox> boxes = choose();
        for (CheckBox box : boxes) box.setChecked(true);
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle();
        assertEquals(0, calls); assertFalse(consent.isBusy());
    }

    @Test public void lostAccessBeforeProceedingGrantsNothing() throws Exception {
        List<CheckBox> boxes = choose();
        for (CheckBox box : boxes) box.setChecked(true);
        available = false;
        positive().performClick(); idle();
        assertEquals(0, calls); assertFalse(consent.isBusy());
    }

    @Test public void noConnectedServerGrantsNothing() throws Exception {
        profiles = Collections.emptyList();
        consent.show(); idle();
        assertEquals("Ingen ansluten TLS-server", shadowOf(dialog()).getTitle().toString());
        assertEquals(0, calls);
    }

    @Test public void unavailableShowsNothing() {
        available = false; consent.show();
        assertFalse(consent.isBusy());
    }

    private List<CheckBox> choose() throws Exception {
        consent.show(); idle();
        dialog().getListView().performItemClick(null, 0, 0); idle();
        List<CheckBox> boxes = new ArrayList<>();
        collect(shadowOf(dialog()).getView(), boxes);
        return boxes;
    }
    private void collect(View view, List<CheckBox> boxes) {
        if (view instanceof CheckBox) boxes.add((CheckBox) view);
        else if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) collect(((ViewGroup) view).getChildAt(i), boxes);
    }
    private android.widget.Button positive() throws Exception { return dialog().getButton(AlertDialog.BUTTON_POSITIVE); }
    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }
    private AlertDialog dialog() throws Exception {
        Field f = ServerTrialConsentUi.class.getDeclaredField("dialog"); f.setAccessible(true); return (AlertDialog) f.get(consent);
    }
}
