package se.tmm.sensorplanner.plugin;

import android.content.Context;
import android.os.Bundle;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.TAKServer;

/** Reports any connection loss, so that a reconnect between trial checks cannot resume consent, and every connection change. Reads state only. */
final class ServerTrialConnectionWatch extends CotStreamListener {
    private final Runnable lost, changed;

    ServerTrialConnectionWatch(Context context, Runnable lost, Runnable changed) {
        super(context, "TMM-TRIAL", null, false);
        this.lost = lost;
        this.changed = changed;
        init();
    }

    // The 5.6.0.24 SDK's onCotOutputUpdated calls connected(server, up) for both directions (checked in its bytecode).
    @Override protected void connected(TAKServer server, boolean up) { if (!up) lost.run(); changed.run(); }
    @Override protected void enabled(TAKServer server, boolean on) { if (!on) lost.run(); changed.run(); }
    @Override protected void serviceDisconnected() { lost.run(); changed.run(); }
    @Override public void onCotOutputRemoved(Bundle stream) { super.onCotOutputRemoved(stream); lost.run(); changed.run(); }
}
