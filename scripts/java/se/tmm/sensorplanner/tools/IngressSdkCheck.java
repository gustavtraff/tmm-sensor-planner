package se.tmm.sensorplanner.tools;

import android.os.Bundle;
import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.android.importexport.CotEventImporter;
import com.atakmap.android.maps.*;
import com.atakmap.comms.*;
import com.atakmap.coremap.cot.event.*;
import java.lang.reflect.Modifier;

/** Signature checks only: no host construction, callback registration, import, store or dispatch. */
public final class IngressSdkCheck {
    public static void main(String[] args) throws Exception {
        check(CotServiceRemote.CotEventListener.class, "onCotEvent", void.class, CotEvent.class, Bundle.class);
        check(CommsMapComponent.class, "addOnCotEventListener", void.class, CotServiceRemote.CotEventListener.class);
        check(CommsMapComponent.class, "removeOnCotEventListener", void.class, CotServiceRemote.CotEventListener.class);
        check(CotImporterManager.class, "registerImporter", void.class, CotEventImporter.class);
        check(CotImporterManager.class, "unregisterImporter", void.class, CotEventImporter.class);
        check(CotEventImporter.class, "importData", CommsMapComponent.ImportResult.class, CotEvent.class, Bundle.class);
        check(MapEventDispatcher.MapEventDispatchListener.class, "onMapEvent", void.class, MapEvent.class);
        check(MapEventDispatcher.class, "addMapEventListenerToBase", void.class, String.class, MapEventDispatcher.MapEventDispatchListener.class);
        check(MapEventDispatcher.class, "removeMapEventListenerFromBase", void.class, String.class, MapEventDispatcher.MapEventDispatchListener.class);
        check(MapEvent.class, "getExtras", Bundle.class);
        check(MapEvent.class, "getItem", MapItem.class);
        check(CotDetailHandler.class, "toItemMetadata", CommsMapComponent.ImportResult.class, MapItem.class, CotEvent.class, CotDetail.class);
        check(CommsLogger.class, "logReceive", void.class, CotEvent.class, String.class, String.class);
        System.out.println("Ingress SDK signatures passed. No listener/importer registered, no source/feed trust inferred, no data received or sent.");
    }
    private static void check(Class<?> owner, String name, Class<?> result, Class<?>... arguments) throws Exception {
        java.lang.reflect.Method method = owner.getMethod(name, arguments);
        if (method.getReturnType() != result || !Modifier.isPublic(method.getModifiers())) {
            throw new AssertionError("Ingress SDK signature changed; review the API boundary");
        }
    }
}
