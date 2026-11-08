package se.tmm.sensorplanner.plugin;

import android.content.Context;
import android.os.Bundle;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.TAKServer;

/** Reports any connection loss, so that a reconnect between trial checks cannot resume consent. Reads state only. */
final class ServerTrialConnectionWatch extends CotStreamListener {
    private final Runnable lost;

    ServerTrialConnectionWatch(Context context, Runnable lost) {
        super(context, "TMM-TRIAL", null, false);
        this.lost = lost;
        init();
    }

    @Override protected void connected(TAKServer server, boolean up) { if (!up) lost.run(); }
    @Override protected void enabled(TAKServer server, boolean on) { if (!on) lost.run(); }
    @Override protected void serviceDisconnected() { lost.run(); }
    @Override public void onCotOutputRemoved(Bundle stream) { super.onCotOutputRemoved(stream); lost.run(); }
}
