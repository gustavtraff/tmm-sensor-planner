package se.tmm.sensorplanner.core;

import android.app.*;
import android.preference.PreferenceManager;
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
    private MissionSourceChoiceUi.Host host;
    private List<String> profiles;private final List<MissionEndpoint> selected=new ArrayList<>();
    @Before public void setup(){
        activity=Robolectric.buildActivity(Activity.class).setup().get();profiles=new ArrayList<>(Collections.singletonList("tak.example.invalid:8089:ssl"));
        PreferenceManager.getDefaultSharedPreferences(activity).edit().remove("apiSecureServerPort").commit();
        host=new MissionSourceChoiceUi.Host(){
            public boolean available(){return available;}
            public List<String> profiles(){if(failProfiles)throw new IllegalStateException("PRIVATE SOURCE DETAIL");return profiles;}
            public void selected(MissionEndpoint endpoint){selected.add(endpoint);if(failSelected)throw new IllegalStateException("PRIVATE CALLBACK DETAIL");}
        };ui=new MissionSourceChoiceUi(activity,host);
    }
    @After public void close(){ui.close();activity.finish();}
    private AlertDialog dialog(){return ShadowAlertDialog.getLatestAlertDialog();}
    private String title(){return Shadows.shadowOf(dialog()).getTitle().toString();}
    private void choose(){dialog().getListView().performItemClick(null,0,0);}
    private void click(int button){dialog().getButton(button).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    private EditText input(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int n=0;n<g.getChildCount();n++){EditText found=input(g.getChildAt(n));if(found!=null)return found;}}return null;}
    private Button button(View view,String text){if(view instanceof Button&&text.contentEquals(((Button)view).getText()))return (Button)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int n=0;n<g.getChildCount();n++){Button found=button(g.getChildAt(n),text);if(found!=null)return found;}}return null;}
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
    @Test public void ordinaryFollowingKeepsExplicitConsentWithDefaultPortBehindSettings(){
        ui.close();ui=new MissionSourceChoiceUi(activity,host,"Ta emot gruppens enheter. ",true);
        ui.show();assertTrue(selected.isEmpty());choose();assertEquals("Anslut delad feed",title());
        EditText port=input(dialog().getWindow().getDecorView());assertEquals("8443",port.getText().toString());
        assertEquals(View.GONE,((View)port.getParent()).getVisibility());assertTrue(selected.isEmpty());
        click(AlertDialog.BUTTON_POSITIVE);assertEquals(1,selected.size());assertEquals("https://tak.example.invalid:8443",selected.get(0).base);
    }
    @Test public void ordinaryPortSettingsCanBeChangedAndInvalidInputStaysVisible(){
        ui.close();ui=new MissionSourceChoiceUi(activity,host,"Ta emot gruppens enheter. ",true);ui.show();choose();
        View root=dialog().getWindow().getDecorView();EditText port=input(root);button(root,"Anslutningsinställningar").performClick();
        assertEquals(View.VISIBLE,((View)port.getParent()).getVisibility());port.setText("0");
        button(root,"Dölj anslutningsinställningar").performClick();click(AlertDialog.BUTTON_POSITIVE);
        assertTrue(selected.isEmpty());assertEquals(View.VISIBLE,((View)port.getParent()).getVisibility());
        port.setText("9443");click(AlertDialog.BUTTON_POSITIVE);assertEquals(1,selected.size());assertEquals("https://tak.example.invalid:9443",selected.get(0).base);
    }
    @Test public void atakPortSuggestionRequiresConfirmationInBothModes(){
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putString("apiSecureServerPort","9443").commit();
        for(boolean following:new boolean[]{false,true}){
            ui.close();selected.clear();ui=new MissionSourceChoiceUi(activity,host,"Read only. ",following);
            ui.show();choose();assertTrue(selected.isEmpty());
            assertEquals("9443",input(dialog().getWindow().getDecorView()).getText().toString());
            click(AlertDialog.BUTTON_POSITIVE);assertEquals(1,selected.size());
            assertEquals("https://tak.example.invalid:9443",selected.get(0).base);
        }
    }
    @Test public void manualOverrideDoesNotChangeAtakPreference(){
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putString("apiSecureServerPort","9443").commit();
        ui.show();choose();input(dialog().getWindow().getDecorView()).setText("10443");click(AlertDialog.BUTTON_POSITIVE);
        assertEquals("https://tak.example.invalid:10443",selected.get(0).base);
        assertEquals("9443",PreferenceManager.getDefaultSharedPreferences(activity).getString("apiSecureServerPort",null));
    }
    @Test public void invalidOrWrongTypeAtakPreferenceRequiresManualPort(){
        for(String value:new String[]{"","0","65536","-1","8443text","123456"}){
            PreferenceManager.getDefaultSharedPreferences(activity).edit().putString("apiSecureServerPort",value).commit();
            assertManualPortRequired();
        }
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putInt("apiSecureServerPort",9443).commit();
        assertManualPortRequired();
    }
    private void assertManualPortRequired(){
        ui.close();ui=new MissionSourceChoiceUi(activity,host,"Read only. ",true);ui.show();choose();
        EditText port=input(dialog().getWindow().getDecorView());assertEquals("",port.getText().toString());
        assertEquals(View.VISIBLE,((View)port.getParent()).getVisibility());
        click(AlertDialog.BUTTON_POSITIVE);assertTrue(selected.isEmpty());
    }
    @Test public void retiredSourceAndPortCancelCannotDismissFreshChoices(){
        ui.show();AlertDialog source=dialog();choose();AlertDialog port=dialog();
        source.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue("Retired source Cancel must preserve port choice",port.isShowing());assertTrue(ui.isBusy());
        ui.cancel();ui.show();AlertDialog fresh=dialog();
        port.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue("Retired port Cancel must preserve fresh source choice",fresh.isShowing());assertTrue(selected.isEmpty());
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
