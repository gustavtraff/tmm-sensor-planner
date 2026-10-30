package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import gov.tak.api.ui.IHostUIService;
import java.util.function.BooleanSupplier;
import se.tmm.sensorplanner.plugin.ingress.IngressProbeState;

/** Explicit isolated-lab observer only; never consumes/imports events or writes source/inbox state. */
@TargetApi(26)
final class IngressObservationProbe {
    private final MapView map;
    private final IHostUIService ui;
    private final BooleanSupplier allowed;
    private final MapEventDispatcher dispatcher;
    private final IngressProbeState state=new IngressProbeState();
    private volatile boolean closed;
    private MapEventDispatcher.MapEventDispatchListener listener;
    private AlertDialog dialog;

    IngressObservationProbe(MapView map,IHostUIService ui,BooleanSupplier allowed) {
        this.map=map;this.ui=ui;this.allowed=allowed;dispatcher=map.getMapEventDispatcher();
    }
    private boolean permitted() { return !closed&&allowed.getAsBoolean()&&ShareLabRoute.connected(map.getContext()); }
    void show() {
        if(!permitted()) { if(state.snapshot().active)state.invalidate(state.snapshot().generation);detach();ui.showToast("Källprovet kräver isolerat loopback-labb och flygplansläge");return; }
        if(dialog!=null)dialog.dismiss();
        IngressProbeState.Snapshot snapshot=state.snapshot();
        TextView text=new TextView(map.getContext());text.setPadding(24,16,24,16);text.setText(summary(snapshot));
        ScrollView scroll=new ScrollView(map.getContext());scroll.addView(text);
        dialog=new AlertDialog.Builder(map.getContext()).setTitle("Källkontext · syntetisk observation")
                .setView(scroll).setPositiveButton(snapshot.active?"Stoppa och visa":"Starta nytt prov",(d,w)-> {
                    if(snapshot.active){if(!permitted())state.invalidate(snapshot.generation);state.stop();detach();if(!closed)show();}else start();
                }).setNegativeButton(snapshot.active?"Fortsätt provet":"Stäng",null).create();dialog.show();
    }
    private void start() {
        if(!permitted()||!detach())return;
        long token=state.start();
        listener=event -> {
            if(closed||!state.accepting(token)||event==null||!MapEvent.ITEM_IMPORTED.equals(event.getType()))return;
            try {
                MapItem item=event.getItem();
                String uid=item==null?null:item.getUID();
                if(!IngressProbeState.isCanary(uid))return;
                if(!permitted()){state.drop(token);return;}
                state.record(token,uid,event.getExtras());
            } catch(RuntimeException unavailable){state.drop(token);}
        };
        try { dispatcher.addMapEventListenerToBase(MapEvent.ITEM_IMPORTED,listener);ui.showToast("Syntetisk observation aktiv. Återvänd hit för att stoppa och visa."); }
        catch(RuntimeException unavailable){state.drop(token);detach();ui.showToast("Källprovet kunde inte registreras. Ingen källa är verifierad.");}
    }
    private boolean detach() {
        if(listener==null)return true;
        try { dispatcher.removeMapEventListenerFromBase(MapEvent.ITEM_IMPORTED,listener);listener=null;return true; }
        catch(RuntimeException unavailable){state.invalidate(state.snapshot().generation);ui.showToast("Avregistrering kunde inte bekräftas. Avlasta pluginet och starta om testvärden.");return false;}
    }
    /** Immediately rejects late callbacks, including before a queued native unregister. */
    void stop() { closed=true;state.close(); }
    void close() { stop();detach();if(dialog!=null){dialog.dismiss();dialog=null;} }

    private static String summary(IngressProbeState.Snapshot s) {
        StringBuilder out=new StringBuilder("Endast fyra fasta syntetiska PUB18-UUID:n. Inga värden, kartdata eller filer sparas/skickas.\n")
                .append("Detta är inte en mottagnings- eller feedbekräftelse. Okända extrasnycklar räknas, inte läses.\n")
                .append("Status: ").append(s.active?"aktiv":s.invalidated?"ogiltigt prov – börja om":"stoppad")
                .append("\nObservationer: ").append(s.events.size()).append('/').append(IngressProbeState.MAX_EVENTS)
                .append(" · tappade/avbrutna: ").append(s.dropped).append("\nStarta nytt prov rensar tidigare minnesobservationer.\n");
        String[] names={"Mini","Scout","PIR","Gateway"};
        for(IngressProbeState.Observation e:s.events)out.append('\n').append(names[e.canaryIndex])
                .append(" · extras=").append(e.extrasPresent).append(" · from=").append(e.from)
                .append(" · serverFrom=").append(e.serverFrom).append(" · endpointFrom=").append(e.endpointFrom)
                .append(" · övriga nycklar=").append(e.otherKeyCount);
        return out.toString();
    }
}
