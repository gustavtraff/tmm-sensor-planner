package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Executor;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.LocalImagePreview;

/**
 * #242 U3: local and incoming values side by side, with Behåll min and Använd inkommande. Neither action sends
 * anything (U8). Both are run by {@link FeedSyncApply#resolve} on the planner's worker and recheck the state this
 * view was read from.
 */
@TargetApi(26)
final class FeedConflictUi {
    interface Host {
        void post(Runnable action);
        boolean available();
        /** The app's existing field text for a device, including MGRS and mils. */
        String summary(TmmDraft draft);
        /** Local devices or markers changed. */
        void changed();
    }
    private final Context context;private final Executor worker;private final FeedSyncApply apply;private final LocalImages images;private final Host host;
    private final List<LocalImagePreview> previews=new ArrayList<>();
    private volatile boolean closed,busy;private AlertDialog dialog;
    private ImageAssociationRegistry.Record picked;

    FeedConflictUi(Context context,Executor worker,FeedSyncApply apply,LocalImages images,Host host){
        this.context=context;this.worker=worker;this.apply=apply;this.images=images;this.host=host;
    }
    boolean isBusy(){return busy||dialog!=null&&dialog.isShowing();}

    void show(UUID uid){
        if(closed||isBusy()||!host.available())return;busy=true;
        worker.execute(()->{
            FeedSyncApply.Conflict read=null;
            try{read=apply.conflict(uid);}catch(Exception|OutOfMemoryError unreadable){read=null;}
            FeedSyncApply.Conflict result=read;
            host.post(()->{busy=false;if(closed)return;if(result==null)info("Ingen konflikt","Enheten har ingen olöst konflikt längre. Inget ändrades.",null);else render(result);});
        });
    }
    void close(){closed=true;dismiss();}

    private void render(FeedSyncApply.Conflict c){
        picked=null;
        LinearLayout root=column();
        root.addView(text("Din version och den inkommande versionen har båda ändrats sedan senast de stämde. Inget skickas eller publiceras av valen nedan."));
        LinearLayout sides=new LinearLayout(context);sides.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout mine=side("Lokalt (din version)",host.summary(c.local));
        if(c.localImageRef==null)mine.addView(text("Bild: ingen lokal bild"));
        else mine.addView(preview(()->images.read(c.localImageRef),"Din lokala bild"));
        LinearLayout theirs=side("Inkommande",host.summary(c.remote));
        TextView choice=text("");
        if(c.candidates.isEmpty())theirs.addView(text("Bild: ingen inkommande bild"));
        for(int n=0;n<c.candidates.size();n++){
            ImageAssociationRegistry.Record r=c.candidates.get(n);int number=n+1;
            if(c.ambiguous)theirs.addView(text("Bildkandidat "+number+" av "+c.candidates.size()));
            theirs.addView(preview(()->apply.incomingImage(r),c.ambiguous?"Inkommande bildkandidat "+number:"Inkommande bild"));
            if(c.ambiguous)theirs.addView(button("Välj bild "+number,()->{picked=r;choice.setText("Vald: bildkandidat "+number+". Använd inkommande tar den bilden.");enableUse();}));
        }
        if(c.ambiguous){
            theirs.addView(text("Flera bilder är bundna till enheten. Välj en för Använd inkommande, eller behåll din egen. Ingen väljs automatiskt."));
            theirs.addView(choice);
        }
        sides.addView(mine,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        sides.addView(theirs,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        root.addView(sides);
        ScrollView scroll=new ScrollView(context);scroll.addView(root);
        dismiss();
        AlertDialog shown=new AlertDialog.Builder(context).setTitle("Konflikt").setView(scroll)
                .setPositiveButton("Använd inkommande",(d,w)->{if(dialog==d)resolve(c,FeedSyncApply.Resolution.USE_INCOMING);})
                .setNegativeButton("Behåll min",(d,w)->{if(dialog==d)resolve(c,FeedSyncApply.Resolution.KEEP_LOCAL);})
                .setNeutralButton("Stäng",(d,w)->{if(dialog==d)dismiss();}).create();
        dialog=shown;shown.setCanceledOnTouchOutside(false);shown.setOnCancelListener(d->{if(dialog==d)dismiss();});shown.show();
        if(c.ambiguous)shown.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }
    private void enableUse(){if(dialog!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}

    private void resolve(FeedSyncApply.Conflict c,FeedSyncApply.Resolution how){
        ImageAssociationRegistry.Record pick=picked;dismiss();busy=true;
        worker.execute(()->{
            FeedSyncApply.Outcome outcome=apply.resolve(c,how,pick);
            host.post(()->{
                busy=false;if(closed)return;
                if(outcome!=FeedSyncApply.Outcome.STALE)host.changed();
                switch(outcome){
                    case DONE:info("Klart",how==FeedSyncApply.Resolution.KEEP_LOCAL
                            ?"Din version behålls och visas som Ej publicerad tills du publicerar. Inget skickades."
                            :"Den inkommande versionen är tillämpad lokalt. Inget skickades.",null);break;
                    case STALE:info("Läget har ändrats","Enheten eller feeden har ändrats sedan vyn öppnades. Inget ändrades. Vyn läses om.",()->show(c.deviceUid));break;
                    default:info("Kunde inte tillämpas","Valet kunde inte genomföras säkert, till exempel för att den inkommande ändringen är äldre än din. Inget skickades. Kontrollera enhetens läge.",null);
                }
            });
        });
    }

    private LinearLayout column(){LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);int p=Math.round(12*context.getResources().getDisplayMetrics().density);l.setPadding(p,p,p,p);return l;}
    private LinearLayout side(String title,String body){LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);TextView t=text(title);t.setTypeface(null,android.graphics.Typeface.BOLD);l.addView(t);l.addView(text(body));return l;}
    private TextView text(String s){TextView t=new TextView(context);t.setText(s);return t;}
    private Button button(String label,Runnable action){Button b=new Button(context);b.setText(label);b.setOnClickListener(v->action.run());return b;}
    // The existing owner-scoped thumbnail decodes on the serialized worker; the picture also has a content description.
    private View preview(java.util.concurrent.Callable<byte[]> read,String description){
        LocalImagePreview p=new LocalImagePreview(context,worker,read,()->!closed,description,()->{});previews.add(p);return p;
    }
    private void info(String title,String message,Runnable then){
        if(closed)return;dismiss();
        AlertDialog shown=new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setPositiveButton("Stäng",(d,w)->{if(dialog==d){dismiss();if(then!=null)then.run();}}).create();
        dialog=shown;shown.setCanceledOnTouchOutside(false);shown.show();
    }
    private void dismiss(){
        for(LocalImagePreview p:previews)p.close();previews.clear();
        if(dialog!=null){AlertDialog d=dialog;dialog=null;d.dismiss();}
    }
}
