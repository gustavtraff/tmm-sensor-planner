# Datamodell

Kärnans första modell är ett lokalt utkast (schemaVersion 1), ingen serialiserad
lagrings- eller CoT-standard ännu. Full föreslagen post finns i projektplan §5.

UUID är stabil identitet. displayId och owner är obligatoriska, trimmas och får
inte vara tomma. ID-unikhet över ett arbetsområde är inte implementerad.
Position: ändliga grader, latitud [-90,90], longitud [-180,180].
createdAt/updatedAt: UTC Instant; updatedAt får inte vara före createdAt.
Valfri deployedAt: faktisk tid, inte automatiskt härledd från status.
Bearing: valfri för PIR/Scout, ändlig [0,360), preliminärt sann nord; gateway
avvisar bearing. Kommentar: preliminärt högst 2000 UTF-16-kodenheter (Java String.length()).
Bildreferens: valfri lokal referens; ingen bildblob, filhantering eller delning.
Revision: lokalt positivt revisionsnummer, inga synk-/kvittensanspråk.

Status och typ är separata enums. Alla objekt är lokala utkast; delat tillstånd
skapas inte innan transport har verifierats. createdBy/updatedBy, precision/källa,
sharedAt och konfliktmetadata införs när identitet/SDK och synketappen fastställts.

Avstånd beräknas med haversine, meter, medelradie 6371008.8 m. Samma UUID
utesluts. Gateway-listan filtrerar gateway och sorterar stigande. Alla statusar
räknas preliminärt. Detta är kartmått, inte terräng-, länk- eller siktmått.

Serialisering, migrering, okända schemaversioner och CoT-mappning återstår.
Konflikter ska i synketappen bevara originalet för manuell granskning.
