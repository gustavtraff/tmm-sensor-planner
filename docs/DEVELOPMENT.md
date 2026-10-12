# Utveckling

## Lokal GitHub-token

Fyll i `GH_TOKEN=` i `.env.github` utan citattecken. Filen ignoreras av Git.
`.env.example` är en tom mall som får versionshanteras. Tokenfilen är lokal
autentisering för GitHub API; Git push använder fortsatt SSH.

Kontrollera inloggningen utan att skriva ut token eller ändra GitHub-data:

```sh
python3 scripts/check-github-auth.py
```

På macOS kan skriptet använda Homebrews befintliga CA-certifikat om Python saknar
en egen certifikatfil; TLS-verifieringen förblir aktiverad.

Detta verifierar tokenens identitet, inte alla repo-/Issue-/PR-behörigheter.
Lägg inte token i dokument, kommandoradsargument eller chatt.

## Kör kärnan

Installera JDK 17 och kör sh scripts/test.sh. Alternativt ange JAVA_HOME till en
lokal JDK. Skriptet kompilerar Java 8-kompatibel kod och kör tester utan bibliotek.
Denna JDK är endast kärnans testmiljö, inte ett beslut om ATAK-byggkedjan.

## Lås SDK innan Android-plugin

Hämta officiellt ATAK-CIV 5.5.0 SDK, utvecklarapp och guide via godkänd kanal:
https://tak.gov/pages/sdks. Kontrollera licens/distributionsvillkor. Förvara SDK
utanför Git och registrera SHA-256 samt exakta verktygsversioner här efter prov.

| Komponent | Status |
|---|---|
| ATAK-CIV SDK 5.5.0/hash | Saknas; inte verifierat |
| Utvecklarapp 5.5.0 | Saknas; inte verifierad |
| JDK för Android | Fastställs från SDK |
| Gradle / Android Gradle Plugin | Fastställs från SDK |
| Android SDK / build tools | Fastställs från SDK |
| Telefon / HV Reports / Data Sync | Faktiska versioner och prov återstår |

Skapa inte Gradle-/plugin-konfiguration från gissade API:er. Första Android-leverans
är SDK:ts minimala plugin byggt och installerat i rätt utvecklarapp. Ingen ordinarie
ATAK-kompatibilitet får antas från ett utvecklarbygge.
Inga känsliga lokala inställningar eller testtelefondata i Git/felsökningsloggar.
