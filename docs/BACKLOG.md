# Första backlog

GitHub Issues och milestones skapade 2026-10-12.
Uppstarts-PR kopplas till [#1 – projektuppsättning](https://github.com/gustavtraff/tmm-sensor-planner/issues/1).
Milestones: 0 Förstudie, 1 MVP, 2 Synk, 3 Förvaltning.
Föreslagna labels: api-verification, security, bug, feature, docs, decision.
Ansvariga utses av repoägaren. Använd Issue-mallen när dessa registreras i GitHub.

| ID | Milestone | Mål | Acceptans / beroenden |
|---|---|---|---|
| [E0-01 / #2](https://github.com/gustavtraff/tmm-sensor-planner/issues/2) | 0 | Hämta officiellt 5.5.0 SDK och lås byggkedja | Källa/villkor, hash, JDK/Gradle/AGP/Android SDK dokumenterade. Inget SDK i Git. |
| [E0-02 / #3](https://github.com/gustavtraff/tmm-sensor-planner/issues/3) | 0 | Minimalt plugin | Byggd APK laddas i 5.5.0 utvecklarapp; installationsbevis och verktygsversioner. Kräver E0-01. |
| [E0-03 / #4](https://github.com/gustavtraff/tmm-sensor-planner/issues/4) | 0 | Lokal kartsekretess (F04/F06/N03/A01) | Skapa/flytta/radera på A; B/server ser inget. Två klienter, syntetiska data, trafikobservation. Kräver E0-02. |
| [E0-04 / #5](https://github.com/gustavtraff/tmm-sensor-planner/issues/5) | 0 | CoT-rundtur och manuellt mål (F05/F10/A06/A07) | UUID och samtliga metadata överlever; avbryt skickar inget; fel påstår aldrig delat. Kräver E0-02. |
| [E0-05 / #6](https://github.com/gustavtraff/tmm-sensor-planner/issues/6) | 0 | Data Sync och bildtransport | Dokumenterat API eller tydligt senarelagt. Testa bilaga separat, inga osäkra filreferenser. Kräver E0-04. |
| [E0-06 / #7](https://github.com/gustavtraff/tmm-sensor-planner/issues/7) | 0 | Samexistens / signering (A08) | Faktiska ATAK/HV Reports/Data Sync-versioner, telefon och ordinarie signeringsväg provas. Kräver E0-02. |
| [E0-07 / #8](https://github.com/gustavtraff/tmm-sensor-planner/issues/8) | 0 | Fastställ produktkatalog | Typer/statusar, övergångar, ägar-/ID-standard, bearing, avståndsfilter, bildpolicy beslutas i ADR. |
| [E0-08 / #9](https://github.com/gustavtraff/tmm-sensor-planner/issues/9) | 0 | CI och repo/releasebeslut | Kärn-CI, separat SDK-check, licens, distributionsbeslut, ansvariga och main-skydd fastställda; fallerande PR blockeras. |
| [M1-01 / #10](https://github.com/gustavtraff/tmm-sensor-planner/issues/10) | 1 | Lokal domän och avstånd (F01/F03/F07/F08) | Första kärna/test finns; fastställ preliminära val E0-07 och senare SDK-adapter. |
| [M1-02 / #11](https://github.com/gustavtraff/tmm-sensor-planner/issues/11) | 1 | Permanent lokal lagring (F04/A02/A09) | Omstart/offline, fel utan tyst förlust, schema och migrationstest. Kräver M1-01. |
| [M1-03 / #12](https://github.com/gustavtraff/tmm-sensor-planner/issues/12) | 1 | Karta/formulär/granskning (F03/F06/N08) | Fältfel, urskiljbara markörer, tydligt lokalt utkast, praktiskt skärmprov. Kräver E0-03/M1-02. |
| [M1-04 / #13](https://github.com/gustavtraff/tmm-sensor-planner/issues/13) | 1 | Lokal bild (F09/A05) | Lägg till/visa/ersätt/ta bort och saknad fil; EXIF-/storlekspolicy E0-07. |
| [M1-05 / #14](https://github.com/gustavtraff/tmm-sensor-planner/issues/14) | 1 | En bekräftad delningsväg (F05/F12) | Endast efter E0-04; mål/fält/bildstatus i granskning, aktiv bekräftelse, tydligt fel. |
| [S2-01 / #15](https://github.com/gustavtraff/tmm-sensor-planner/issues/15) | 2 | Import, uppdatering och konflikter (F10/F11) | Samma UID blir inte dublett; samtidiga ändringar bevaras för manuell granskning. Kräver E0-04. |

Alla testdata är syntetiska. Varje avslutat SDK-prov ska ge reproducibla steg,
versioner, resultat och ett ADR; ett dokumentförslag är inte ett godkänt prov.
