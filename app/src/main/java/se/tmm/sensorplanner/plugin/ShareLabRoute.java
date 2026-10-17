package se.tmm.sensorplanner.plugin;

import android.content.Context;
import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;

/** The previously verified isolated lab route; no operational-server fallback. */
final class ShareLabRoute {
    static final String STREAM = "127.0.0.1:8089:ssl";
    private ShareLabRoute() {}

    static boolean connected(Context context) {
        try {
            if (!OfflineMode.isOffline(context)) return false;
            TAKServer[] servers = TAKServerListener.getInstance().getServers();
            if (servers == null) return false;
            int enabled = 0;
            for (TAKServer server : servers) {
                if (!server.isEnabled() || !server.isStream()) continue;
                if (!server.isConnected() || !STREAM.equals(server.getConnectString())) return false;
                enabled++;
            }
            return enabled == 1;
        } catch (RuntimeException unavailable) { return false; }
    }

    static Contact recipient(String uid) {
        if (uid == null || uid.isEmpty() || ChatManagerMapComponent.isSpecialGroup(uid)) return null;
        Contact selected = Contacts.getInstance().getContactByUuid(uid);
        IndividualContact self = CotMapComponent.getInstance().getSelfContact(false);
        return selected instanceof IndividualContact && (self == null || !uid.equals(self.getUID()))
                ? selected : null;
    }

    static String destination(String uid) { return STREAM + "|" + uid; }
}
