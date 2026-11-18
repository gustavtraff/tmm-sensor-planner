# Användarguide: TMM Sensor Planner (online)

For maintainers: this Swedish guide describes the integrated candidate after PR #264
(Följ feed, Publicera, automatic fetch, Konflikt). The texts are taken from that PR and
must be checked against the signed version before the guide goes to users. ATAK's own
native steps are marked "(kontrollera mot signerad version)".

Guiden är för dig som planerar och placerar ut enheter tillsammans med andra i ATAK.
Den innehåller inga verkliga platser, konton eller servrar.

## 1. Komma igång

**Du behöver**

- ATAK och TMM-pluginet, installerade och aktiva.
- Den delade feeden i ATAK:s Data Sync, och att telefonen är ansluten till servern.
- Rollen **Subscriber** i feeden.
- En feed utan lås: den får inte vara lösenordsskyddad eller kräva inbjudan.

**Följ feed**

1. Öppna TMM och tryck **Följ feed**.
2. Välj feeden i listan **Välj feed att följa**.
3. Läs sammanfattningen och tryck **Följ feed** igen för att bekräfta.

Därefter hämtar TMM nya uppgifter åt dig. Det går bara att följa en feed per telefon.

**Efter en omstart** av telefonen eller ATAK fortsätter följandet av sig självt. Knappen
visar **Följer** och feedens namn. Du behöver inte välja om. Vill du sluta, öppna knappen och
tryck **Sluta följa**. Det du redan har finns kvar, och **Följ igen** startar på nytt.

## 2. Planera en enhet

1. Tryck **Skapa enhet**, fyll i typ, ID, status och eventuell position, och tryck
   **Spara lokalt**. Enheten är nu ett lokalt utkast och delas inte.
2. Öppna enheten och tryck **Publicera**.
3. Granska förhandsvisningen **Publicera till** *feedens namn*. En ny enhet visas som
   **NY ENHET I KARTAN**. Tryck **Publicera** för att bekräfta, eller **Avbryt**.
4. En ny enhet kräver också ATAK:s eget steg: öppna feeden i Data Sync, välj **Add Items**,
   markera enheten och tryck **Done** (kontrollera mot signerad version).

När feeden visar enheten visar TMM **Publicerad**. Då kan andra se den.

## 3. Placera ut

Den som placerar ut enheten uppdaterar samma enhet med verkligt läge:

- **Redigera**: ändra status och bäring (bäring finns bara för vissa typer), och tryck
  **Spara lokalt**.
- **Flytta på kartan**: sätt den faktiska positionen.
- **Lägg till bild** (eller **Ersätt bild**): lägg till ett foto av placeringen.

Tryck sedan **Publicera**.

**Förhandsvisning.** Om position eller bäring ändrats visar den
**ÄNDRAD GEOMETRI · före → efter**, med gammal och ny **Position · MGRS** och **Bäring**.
Är positionen flyttad måste du också kryssa i **Jag har kontrollerat den nya positionen.
Den ersätter den gamla för alla som ser enheten.** Det är en extra säkerhet, eftersom
alla ser den nya positionen. En ny bild laddas upp direkt när metadata syns i feeden.
Om ATAK frågar om att publicera till feeden (kontrollera mot signerad version) och du svarar
**Nej**, publiceras ingenting och enheten blir **Ej publicerad**.

**Vad statusarna betyder**

- **Publicerad**: feeden visar exakt det du publicerade (TMM väntar upp till 30 sekunder).
- **Ej publicerad**: du har ändringar som inte nått feeden. Inget har gått förlorat.
- **Metadata ännu inte i feeden, tryck Publicera igen**: uppgifterna syntes inte i feeden i
  tid, ofta när ATAK:s egna steg tog lång tid. Bilden laddades inte upp. Vänta en stund och
  tryck **Publicera** igen.
- **Okänt om bilden kom fram**: TMM vet inte om bilden nådde feeden. Tryck inte om direkt.
  Tryck **Uppdatera nu** och se om bilden finns. TMM skickar inte om själv.

## 4. Se andras uppdateringar

TMM hämtar nya uppgifter automatiskt ungefär varje minut, och när telefonen får kontakt igen.
Vill du inte vänta, öppna **Följer** *feedens namn* och tryck **Uppdatera nu**.

- Ändringar från andra som inte krockar med dina tillämpas direkt.
- En bild du får blir enhetens aktuella bild.
- Dina egna oskickade ändringar skrivs aldrig över.

Att hämta publicerar aldrig något.

## 5. Konflikt

**Konflikt** betyder att du och någon annan har ändrat samma enhet. TMM väljer inte åt dig
och behåller din version tills du bestämmer dig. Öppna enheten och tryck **Lös konflikt**.
Du ser **Lokalt (din version)** och **Inkommande** bredvid varandra.

- **Behåll min**: din version blir kvar och visas som **Ej publicerad** tills du publicerar.
- **Använd inkommande**: den andras version tas in lokalt.
- **Stäng**: ingenting ändras.

Inget av valen skickar eller publicerar något.

Om flera bilder hör till enheten visas de som **Bildkandidat** 1, 2 och så vidare. Tryck
**Välj bild** på den du vill ha och sedan **Använd inkommande**. Ingen bild väljs åt dig.
Vill du behålla din egen bild, tryck **Behåll min**.

## 6. Utan uppkoppling

Allt du redan har går att läsa och ändra utan uppkoppling. När telefonen får kontakt igen
kommer TMM ikapp av sig självt, eller när du trycker **Uppdatera nu**. Ingenting publiceras av
sig självt: ändringar du gjorde offline är **Ej publicerad** tills du trycker **Publicera**.

## 7. Bra att veta

- Att en bild tas bort ur feeden är inte radering på servern. Filen finns kvar där tills en
  administratör rensar den.
- Lösenordsskyddade feeds och feeds som kräver inbjudan fungerar inte. Be feedens ägare om en
  öppen feed där du har rollen **Subscriber**.
- Publicering är alltid ett eget val. Du trycker **Publicera**, granskar och bekräftar. Inget
  skickas av sig självt.
- Efter publicering ersätts din lokala bild av exakt den publicerade bilden. Det är samma bild
  (samma pixlar), men den sparas om i ett strikt format. Därför får inte din egen nästa hämtning
  en falsk **Konflikt**.
- En följd feed per telefon.
- Använd bara övnings- och testdata tills ansvarig person har sagt något annat.
