# Produkt

Planerare skapar en lokal TMM-enhet, granskar och delar manuellt. Utplacerare
uppdaterar samma stabila identitet. Målversion är ATAK-CIV 5.5.0.

Krav F01–F12 och N01–N08 definieras i [projektplanen](PROJECT_PLAN.md#3-krav).
MVP omfattar utkast, lagring, kartvy, formulär, bearing, avstånd, lokal bild och
granskning. Delning får läggas till först efter godkänd förstudie.
Hårdvarustyrning, telemetri, optimering, radio-/täckningsmodell och egen server
ligger utanför första versionen. Avstånd säger inget om radiolänk eller terräng.

Första kärnan implementerar delar av F01/F02/F03/F07/F08, utan användargränssnitt.
Minsta katalog: gateway, pir, scout. Föreslagen statuskatalog: planned, deployed,
active, retrieved, cancelled. Statusövergångar är inte fastställda.

Öppna beslut: katalog, ID-/ägarstandard, statusövergångar, automatisk utplaceringstid,
referens/visning för bearing, vilka statusar som räknas vid närmaste enhet,
bildgräns/EXIF, målkanal och redigering av andras objekt.
Se [preliminära kärnval](decisions/0001-sdk-independent-core.md).
