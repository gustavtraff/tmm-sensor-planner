# Tester och grindar

`python3 scripts/test-github-auth.py` testar tokenkontrollen med syntetiska data
och utan nätanrop.

sh scripts/test.sh kontrollerar domänvalidering och avstånd med syntetiska
koordinater. Testerna omfattar UUID-identitet, bearinggränser, ogiltiga koordinater,
tidsfält och sortering. De bevisar inte lagring, kartsekretess eller kompatibilitet.

Acceptanstester A01–A10 finns i projektplan §7. A01/A06/A07/A08 kräver faktiska
klienter, isolerad testserver och dokumenterad trafik-/mottagarobservation.
A02/A05/A09 införs tillsammans med lagring, bilder och migrationer.

| Prov | Resultat |
|---|---|
| Ren Java-kärna | Godkänd lokalt 2026-10-12 med Temurin 17.0.20.1+1 / --release 8 |
| ATAK 5.5.0 pluginladdning | Ej körd – SDK/utvecklarapp saknas |
| Lokal kartsekretess / två klienter | Ej körd |
| CoT-rundtur / bilddelning | Ej körd |
| HV Reports / Data Sync samexistens | Ej körd |

CI för kärnan är en publik kontroll. Android lint, APK och SDK-bygge läggs till
när tillåten byggmiljö finns, som separat obligatorisk kontroll. Skanning före
publicering måste också omfatta historik och artefakter, inte bara arbetskatalogen.
