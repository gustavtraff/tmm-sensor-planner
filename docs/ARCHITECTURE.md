# Arkitektur

Avsedd kedja: domänmodell → lokal lagring → lokal kartrepresentation →
explicit publiceringsadapter → verifierad ATAK/CoT-väg.

Implementerat: core/ med ren Java för utkast, validering och avstånd.
app/ reserverar plats för SDK-verifierat Android-plugin. Varken lagring,
ATAK-adaptrar, kartobjekt, bilagetransport eller nätkod finns ännu.

SDK-mål: ATAK-CIV 5.5.0. Inga integrations-API:er är bevisade.
Officiella HelloWorld på main är ett referensexempel, inte ett fastställt
5.5.0-kontrakt: https://github.com/TAK-Product-Center/atak-civ/blob/main/plugin-examples/helloworld/app/build.gradle
SDK-källa: https://tak.gov/pages/sdks

Etapp 0 måste visa att lokal kartrepresentation inte sänds, att UUID och metadata
överlever rundtur, att delningsmålet väljs manuellt och hur bilagor transporteras.
En vanlig CoT-händelse antas inte bli en Data Sync-feed.
