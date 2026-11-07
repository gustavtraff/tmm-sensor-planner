package se.tmm.sensorplanner.core;

import android.app.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import se.tmm.sensorplanner.plugin.ingress.MissionEndpoint;
import se.tmm.sensorplanner.plugin.local.MissionSourceChoiceUi;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,35},manifest=Config.NONE)
public class MissionSourceChoiceUiTest {
    private Activity activity;private MissionSourceChoiceUi ui;private boolean available=true,failProfiles,failSelected;
    private List<String> profiles;private final List<MissionEndpoint> selected=new ArrayList<>();
    @Before public void setup(){
        activity=Robolectric.buildActivity(Activity.class).setup().get();profiles=new ArrayList<>(Collections.singletonList("tak.example.invalid:8089:ssl"));
        ui=new MissionSourceChoiceUi(activity,new MissionSourceChoiceUi.Host(){
            public boolean available(){return available;}
            public List<String> profiles(){if(failProfiles)throw new IllegalStateException("PRIVATE SOURCE DETAIL");return profiles;}
            public void selected(MissionEndpoint endpoint){selected.add(endpoint);if(failSelected)throw new IllegalStateException("PRIVATE CALLBACK DETAIL");}
        });
    }
    @After public void close(){ui.close();activity.finish();}
    private AlertDialog dialog(){return ShadowAlertDialog.getLatestAlertDialog();}
    private String title(){return Shadows.shadowOf(dialog()).getTitle().toString();}
    private void choose(){dialog().getListView().performItemClick(null,0,0);}
    private void click(int button){dialog().getButton(button).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    private EditText input(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int n=0;n<g.getChildCount();n++){EditText found=input(g.getChildAt(n));if(found!=null)return found;}}return null;}
    @Test public void twoExplicitChoicesAreRequiredAndApiPortDoesNotUseStreamPort(){
        ui.show();assertEquals("Välj ansluten ATAK-server",title());assertTrue(selected.isEmpty());choose();
        assertEquals("Bekräfta server för hämtning",title());assertTrue(selected.isEmpty());EditText port=input(dialog().getWindow().getDecorView());
        assertEquals("8443",port.getText().toString());port.onEditorAction(EditorInfo.IME_ACTION_DONE);assertTrue(selected.isEmpty());assertTrue(ui.isBusy());
        port.setText("9443");Button old=dialog().getButton(AlertDialog.BUTTON_POSITIVE);old.performClick();old.performClick();
        assertEquals(1,selected.size());assertEquals("https://tak.example.invalid:9443",selected.get(0).base);assertFalse(ui.isBusy());
    }
    @Test public void cancellationAndCloseNeverSelectAProfile(){
        ui.show();click(AlertDialog.BUTTON_NEGATIVE);assertFalse(ui.isBusy());ui.show();choose();click(AlertDialog.BUTTON_NEGATIVE);
        assertTrue(selected.isEmpty());ui.show();choose();Button pending=dialog().getButton(AlertDialog.BUTTON_POSITIVE);ui.close();pending.performClick();assertTrue(selected.isEmpty());
    }
    @Test public void invalidPortsAndLostProfileCannotStartARequest(){
        ui.show();choose();EditText port=input(dialog().getWindow().getDecorView());
        for(String bad:new String[]{"","0","65536"}){port.setText(bad);click(AlertDialog.BUTTON_POSITIVE);assertTrue(selected.isEmpty());assertTrue(ui.isBusy());}
        port.setText("8443");profiles.clear();click(AlertDialog.BUTTON_POSITIVE);assertTrue(selected.isEmpty());
        profiles.add("tak.example.invalid:8089:ssl");available=false;click(AlertDialog.BUTTON_POSITIVE);assertTrue(selected.isEmpty());assertFalse(ui.isBusy());
    }
    @Test public void missingAmbiguousOrUnsupportedRegistryIsNotSilentlyUsed(){
        profiles.clear();ui.show();assertEquals("Ingen ansluten TLS-server",title());assertTrue(selected.isEmpty());ui.cancel();
        profiles.add("tak.example.invalid:8089:tcp");ui.show();assertEquals("Servervalet kunde inte verifieras",title());ui.cancel();
        profiles.set(0,"tak.example.invalid:8089:ssl");profiles.add(profiles.get(0));ui.show();assertEquals("Servervalet kunde inte verifieras",title());assertTrue(selected.isEmpty());
    }
    @Test public void staleListClickCannotReplaceConsentAndProviderFailureUsesFixedText(){
        ui.show();ListView old=dialog().getListView();choose();old.performItemClick(null,0,0);assertEquals("Bekräfta server för hämtning",title());
        failProfiles=true;click(AlertDialog.BUTTON_POSITIVE);assertTrue(selected.isEmpty());ui.cancel();ui.show();assertEquals("Servervalet kunde inte verifieras",title());
        failProfiles=false;ui.cancel();ui.show();choose();failSelected=true;click(AlertDialog.BUTTON_POSITIVE);assertEquals("Starten är obekräftad",title());assertEquals(1,selected.size());
    }
}
