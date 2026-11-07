package se.tmm.sensorplanner.plugin.local;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
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
    private final Context context;private final Host host;private AlertDialog dialog;private boolean closed;
    public MissionSourceChoiceUi(Context context,Host host){this.context=context;this.host=host;}
    public boolean isBusy(){return dialog!=null&&dialog.isShowing();}
    public void show(){
        if(closed||isBusy()||!host.available())return;
        try{
            List<String> streams=new ArrayList<>(host.profiles());
            if(streams.isEmpty()){info("Ingen ansluten TLS-server","Anslut en SSL-server i ATAK först. Endast den vanliga kommunikationsleverantören och DNS/IPv4-profiler stöds i detta prov.");return;}
            if(streams.size()>32||new HashSet<>(streams).size()!=streams.size())throw new IllegalArgumentException();
            for(String stream:streams)MissionEndpoint.fromProfile(stream,8443);
            AlertDialog next=new AlertDialog.Builder(context).setTitle("Välj ansluten ATAK-server")
                    .setItems(streams.toArray(new String[0]),null).setNegativeButton("Avbryt",(d,w)->cancel()).create();replace(next);
            next.getListView().setOnItemClickListener((parent,view,n,id)->{if(!closed&&dialog==next&&host.available())port(streams.get(n));});
        }catch(RuntimeException unavailable){info("Servervalet kunde inte verifieras","Ingen server kontaktades. Kontrollera ATAK:s anslutningar och prova igen.");}
    }
    private void port(String stream){
        if(closed||!host.available()){cancel();return;}
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,16,24,16);
        TextView message=new TextView(context);message.setText("Vald ATAK-anslutning: "+stream
                +"\n\nEndast uttrycklig hämtning av metadata. Dina utkast, positioner och bilder skickas inte. Ingen publicering, prenumeration eller bildhämtning startas. "
                +"ATAK:s befintliga TLS-profil används utan att TMM kopierar nycklar eller lösenord. Bekräfta att du får använda servern för testet."
                +"\n\nHTTPS-port för Mission API (ofta 8443). Detta är inte strömporten ovan. Fråga serverägaren vid behov.");body.addView(message);
        EditText input=new EditText(context);input.setContentDescription("HTTPS-port för Mission API");input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});input.setText("8443");input.setSelectAllOnFocus(true);input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_DONE)return false;InputMethodManager keyboard=(InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();return true;});body.addView(input);
        TextView error=new TextView(context);body.addView(error);
        AlertDialog next=new AlertDialog.Builder(context).setTitle("Bekräfta server för hämtning").setView(body)
                .setNegativeButton("Avbryt",(d,w)->cancel()).setPositiveButton("Läs tillgängliga feeds",null).create();replace(next);
        next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(closed||dialog!=next)return;if(!host.available()){cancel();return;}
            MissionEndpoint endpoint;
            try{
                String value=input.getText().toString();if(!value.matches("[0-9]{1,5}"))throw new IllegalArgumentException();
                endpoint=MissionEndpoint.fromProfile(stream,Integer.parseInt(value));
                if(!host.profiles().contains(stream))throw new IllegalArgumentException();
            }catch(RuntimeException unsupported){error.setText("Ange en port 1–65535 och kontrollera att samma TLS-profil fortfarande är ansluten. Ingen hämtning startades.");return;}
            cancel();try{host.selected(endpoint);}catch(RuntimeException unavailable){info("Starten är obekräftad","Servervalet kunde inte slutföras. Kontrollera anslutningen och inkorgen före ett nytt försök. Ingen annan källa väljs automatiskt.");}
        });
    }
    private void info(String title,String message){replace(new AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton("Stäng",(d,w)->cancel()).create());}
    private void replace(AlertDialog next){cancel();dialog=next;next.setCanceledOnTouchOutside(false);next.setOnCancelListener(d->cancel());next.show();}
    public void cancel(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    public void close(){closed=true;cancel();}
}
