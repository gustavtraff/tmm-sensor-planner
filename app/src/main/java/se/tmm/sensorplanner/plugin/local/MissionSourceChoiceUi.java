package se.tmm.sensorplanner.plugin.local;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import se.tmm.sensorplanner.plugin.ingress.MissionEndpoint;

/** Explicit source/port consent; no network request, credential read or store operation. */
public final class MissionSourceChoiceUi {
    public interface Host {
        boolean available();
        List<String> profiles();
        void selected(MissionEndpoint endpoint);
    }
    private final String purpose;
    private final boolean following;
    private final Context context;private final Host host;private AlertDialog dialog;private boolean closed;
    public MissionSourceChoiceUi(Context context,Host host){this(context,host,
            "Endast uttrycklig hämtning av metadata. Dina utkast, positioner och bilder skickas inte. Ingen publicering, prenumeration eller bildhämtning startas. ");}
    public MissionSourceChoiceUi(Context context,Host host,String purpose){this(context,host,purpose,false);}
    public MissionSourceChoiceUi(Context context,Host host,String purpose,boolean following){this.context=context;this.host=host;this.purpose=purpose;this.following=following;}
    public boolean isBusy(){return dialog!=null&&dialog.isShowing();}
    public void show(){
        if(closed||isBusy()||!host.available())return;
        try{
            List<String> streams=new ArrayList<>(host.profiles());
            if(streams.isEmpty()){info("Ingen ansluten TLS-server","Anslut servern i ATAK först. TMM använder ATAK:s vanliga TLS-anslutning; servernamn och IPv4-adresser stöds.");return;}
            if(streams.size()>32||new HashSet<>(streams).size()!=streams.size())throw new IllegalArgumentException();
            for(String stream:streams)MissionEndpoint.fromProfile(stream,8443);
            AlertDialog next=new AlertDialog.Builder(context).setTitle("Välj ansluten ATAK-server")
                    .setItems(streams.toArray(new String[0]),null).setNegativeButton("Avbryt",(d,w)->{if(dialog==d)cancel();}).create();replace(next);
            next.getListView().setOnItemClickListener((parent,view,n,id)->{if(!closed&&dialog==next&&host.available())port(streams.get(n));});
        }catch(RuntimeException unavailable){info("Servervalet kunde inte verifieras","Ingen server kontaktades. Kontrollera ATAK:s anslutningar och prova igen.");}
    }
    private void port(String stream){
        if(closed||!host.available()){cancel();return;}
        String suggested=suggestedPort();
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,16,24,16);
        TextView message=new TextView(context);message.setText("Vald ATAK-anslutning: "+stream
                +"\n\n"+purpose
                +"ATAK:s befintliga TLS-profil används utan att TMM kopierar nycklar eller lösenord. "
                +(following?"Kontrollera att detta är servern för gruppens delade feed.":"Bekräfta att du får använda servern för testet."));body.addView(message);
        TextView portSummary=new TextView(context);portSummary.setText("HTTPS-port: "+(suggested.isEmpty()?"behöver anges":suggested));if(following)body.addView(portSummary);
        LinearLayout settings=new LinearLayout(context);settings.setOrientation(LinearLayout.VERTICAL);
        TextView explanation=new TextView(context);explanation.setText("Förslaget kommer från ATAK:s inställning Secure Server API port. Om inställningen saknas visas standardvärdet 8443. "
                +"Porten används för feedens metadata och bilder och är separat från ATAK:s vanliga anslutningsport. Kontrollera förslaget för vald server. "
                +"Om inget giltigt värde kunde läsas behöver du ange porten här.");settings.addView(explanation);
        EditText input=new EditText(context);input.setContentDescription("HTTPS-port för feeden");input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});input.setText(suggested);input.setSelectAllOnFocus(true);input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable value){portSummary.setText("HTTPS-port: "+(value.length()==0?"behöver anges":value));}
        });
        input.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_DONE)return false;InputMethodManager keyboard=(InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();return true;});settings.addView(input);
        Button toggle=new Button(context);toggle.setText("Anslutningsinställningar");toggle.setMinHeight(Math.round(48*context.getResources().getDisplayMetrics().density));
        if(following){
            settings.setVisibility(suggested.isEmpty()?View.VISIBLE:View.GONE);body.addView(toggle);
            if(suggested.isEmpty())toggle.setText("Dölj anslutningsinställningar");
            toggle.setOnClickListener(v->{boolean show=settings.getVisibility()!=View.VISIBLE;settings.setVisibility(show?View.VISIBLE:View.GONE);
                toggle.setText(show?"Dölj anslutningsinställningar":"Anslutningsinställningar");portSummary.setText("HTTPS-port: "+input.getText());});
        }
        body.addView(settings);
        TextView error=new TextView(context);body.addView(error);
        ScrollView scroll=new ScrollView(context);scroll.addView(body);
        AlertDialog next=new AlertDialog.Builder(context).setTitle(following?"Anslut delad feed":"Bekräfta server för hämtning").setView(scroll)
                .setNegativeButton("Avbryt",(d,w)->{if(dialog==d)cancel();}).setPositiveButton("Läs tillgängliga feeds",null).create();replace(next);
        next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(closed||dialog!=next)return;if(!host.available()){cancel();return;}
            MissionEndpoint endpoint;
            try{
                String value=input.getText().toString();if(!value.matches("[0-9]{1,5}"))throw new IllegalArgumentException();
                endpoint=MissionEndpoint.fromProfile(stream,Integer.parseInt(value));
                if(!host.profiles().contains(stream))throw new IllegalArgumentException();
            }catch(RuntimeException unsupported){settings.setVisibility(View.VISIBLE);toggle.setText("Dölj anslutningsinställningar");error.setText("Ange en port 1–65535 och kontrollera att samma TLS-profil fortfarande är ansluten. Ingen hämtning startades.");return;}
            cancel();try{host.selected(endpoint);}catch(RuntimeException unavailable){info("Starten är obekräftad","Servervalet kunde inte slutföras. Kontrollera anslutningen och inkorgen före ett nytt försök. Ingen annan källa väljs automatiskt.");}
        });
    }
    private String suggestedPort(){
        try{
            // Verified in the supplied ATAK 5.6.0.24 APK's network_connections_preferences.xml.
            String value=PreferenceManager.getDefaultSharedPreferences(context).getString("apiSecureServerPort","8443");
            if(value!=null&&value.matches("[0-9]{1,5}")&&Integer.parseInt(value)>=1&&Integer.parseInt(value)<=65535)return value;
        }catch(RuntimeException unreadable){/* Require manual entry; never guess a replacement for an invalid setting. */}
        return "";
    }
    private void info(String title,String message){replace(new AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton("Stäng",(d,w)->{if(dialog==d)cancel();}).create());}
    private void replace(AlertDialog next){cancel();dialog=next;next.setCanceledOnTouchOutside(false);next.setOnCancelListener(d->{if(dialog==d)cancel();});next.show();}
    public void cancel(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    public void close(){closed=true;cancel();}
}
