# 0001 – börja med en SDK-oberoende kärna

Datum: 2026-10-12. Status: tekniskt startval; produktantaganden är preliminära.
Ansvarig för fastställande: repoägare/produktägare, ännu inte formellt utsedd.

SDK 5.5.0 finns inte lokalt. Börja med ren Java 8-kompatibel kärna så validering
och kartavstånd kan provas utan att uppfinna ATAK-API:er. Ingen Android-
verktygskedja låses förrän rätt SDK granskas. Alternativ: enbart dokumentation
eller att kopiera ett senare HelloWorld; det senare uppfyller inte SDK-grinden.

Preliminära testbara val: gateway/pir/scout, planens fem föreslagna statusar,
bearing från sann nord, obligatorisk ägare, 2000 teckens kommentar och alla
statusar i avståndsberäkning. Inga automatiska statusövergångar eller datum.
Dessa val måste fastställas före datamodellfrysning/UI. Ingen persistence/synk
har införts, så ingen lagringsmigration krävs för nuvarande kärna.

Konsekvens: testbar start, men ingen APK, kartintegration eller lagring ännu.
