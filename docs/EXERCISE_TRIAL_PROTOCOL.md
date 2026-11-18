# Exercise trial protocol (part of #252)

For maintainers: this is the on-site checklist the owner follows at the exercise,
on the real TAK Server 5.6 with two phones. It implements the M4 clarification in
[the metadata route proposal](PUBLISH_METADATA_ROUTE_PROPOSAL.md): about 15 minutes of
checks with one synthetic device, then real exercise data only after the owner
records a go, otherwise the candidate 30 fallback (#222). The steps and the result
form are Swedish because the owner reads them in the field. TMM texts are quoted from the
integrated UI (PR #264). Only ATAK's own native steps (Add Items, Done, the native
publish prompt and its **Nej**) say "(kontrollera mot signerad version)", because the
signed candidate may word them differently. Record results without operational
data (no real locations, photos, server names or accounts).

# Övningsprov: protokoll

Två telefoner, A och B. Testenhet: ett tydligt falskt namn (till exempel
"TESTENHET-INTE-RIKTIG") och en testbild utan operativt värde. Använd aldrig
verkliga platser eller bilder under kontrollerna.

## 1. Förberedelser

- [ ] Signerad kandidat installerad på både A och B. Versionen är kontrollerad och
      noterad (kontrollera mot signerad version).
- [ ] A och B följer samma feed.
- [ ] Feeden har inget lås (inte lösenordsskyddad, inte endast inbjudan) och din roll
      är **Subscriber**. Kontrollerat i ATAK:s feedlista.
- [ ] Bara en feed är prenumererad på varje telefon.
- [ ] Auto-publish är oförändrat (slå inte på det).
- [ ] Båda telefonerna är anslutna till servern.
- [ ] Testenheten och testbilden är klara. Noterat: klockslag för start.

## 2. Kontroller, cirka 15 minuter

Skriv "OK" eller "FEL" och en kort anteckning. Vid FEL: gå vidare bara om punkten
får vara FEL enligt avsnitt 3, annars avbryt och gå till reservplanen.

### 2.1 Skapa och publicera utan bild

1. På A: skapa testenheten utan bild (**Skapa enhet**, **Spara lokalt**) och tryck
   **Publicera**. Förhandsvisningen **Publicera till** *feedens namn* visar **NY ENHET I KARTAN**
   och **Ingen bild. Endast metadata publiceras.** Tryck **Publicera** i dialogen och följ sedan
   ATAK:s egna steg för en ny enhet: öppna feeden i Data Sync, **Add Items**, markera enheten,
   **Done** (kontrollera mot signerad version).
2. På B: vänta, tryck inte något.

Förväntat: A visar **Publicerad** när feeden visar samma metadata (TMM väntar upp till 30 s).
Testenheten dyker upp på B av sig själv inom cirka 2 minuter, utan frågor. Om inget syns efter 2 minuter, tryck **Uppdatera nu** och notera att det behövdes.

- [ ] OK  - [ ] FEL   Tid till synlig på B: ______

### 2.2 B uppdaterar (nyckelkontrollen)

B skapade inte enheten. Detta är M7 punkt 1.

1. På B: ändra status och bäring (**Redigera**, **Spara lokalt**), flytta positionen
   (**Flytta på kartan**) och lägg till testbilden (**Lägg till bild**).
2. Tryck **Publicera**. Förhandsvisningen visar **ÄNDRAD GEOMETRI · före → efter** med
   **Position · MGRS:** gammal → ny och **Bäring:** gammal → ny. Kryssa i den extra bekräftelsen
   för flyttad position (**Jag har kontrollerat den nya positionen. Den ersätter den gamla för
   alla som ser enheten.**) och tryck **Publicera**. Godkänn ATAK:s eget steg om det kommer
   (kontrollera mot signerad version).
3. Tillåtet fall: om bilden inte laddas upp och dialogen **Ej publicerad** visar
   **Metadata ännu inte i feeden, tryck Publicera igen** (till exempel för en ny enhet när ATAK:s
   egna Add Items, Done tar längre än 30 s): vänta en kort stund och tryck **Publicera**
   igen. Det är tolererat enligt avsnitt 3, men notera att det behövdes.
4. På A: vänta cirka 2 minuter (eller **Uppdatera nu**).

Förväntat: A visar status, position, bäring och bild som aktuella, utan **Konflikt**.
Ingen dubblett av enheten. Positions- och bäringsändring stöds: B ska inte vägras.

- [ ] OK  - [ ] FEL   Dubblett? ja/nej   Behövdes "Publicera igen"? ja/nej

### 2.3 Enbart statusändring behåller bilden

1. På A: ändra bara status. **Publicera** (ingen extra positionsbekräftelse behövs).
2. På B: vänta eller **Uppdatera nu**.

Förväntat: ny status på B, bilden finns kvar, ingen **Konflikt**.

- [ ] OK  - [ ] FEL

### 2.4 Konflikt

1. Ändra kommentaren på testenheten till olika text på A och på B. Publicera bara på A.
   B ska inte hinna hämta före sin egen ändring: ändra på B först, publicera sedan på A.
2. På B (nu **Ej publicerad**): vänta eller tryck **Uppdatera nu**.

Förväntat: B visar **Konflikt** på enheten, B:s egen text är kvar. Konfliktvyn visar
lokalt och inkommande bredvid varandra.

3. Prova **Behåll min**: B:s text finns kvar, **Konflikt** försvinner och enheten är
   **Ej publicerad** tills B publicerar.
4. Upprepa konflikten (nya olika kommentarer) och prova **Använd inkommande**: B får A:s
   text och **Konflikt** försvinner.

- [ ] Konflikten visades  - [ ] Behåll min OK  - [ ] Använd inkommande OK

### 2.5 Flygplansläge på B

1. Slå på flygplansläge på B.
2. På A: ändra status och **Publicera**.
3. Slå av flygplansläge på B. Vänta cirka 2 minuter eller tryck **Uppdatera nu**.

Förväntat: B kommer ikapp och visar A:s ändring. Inget förlorades medan B var offline.

- [ ] OK  - [ ] FEL

### 2.6 Svara Nej på ATAK:s publiceringsfråga

1. På B: ändra status, tryck **Publicera**, bekräfta, och svara **Nej** på ATAK:s egen
   publiceringsfråga (kontrollera mot signerad version).
2. Kontrollera enheten på B och feeden på A (**Uppdatera nu**).

Förväntat: enheten är **Ej publicerad** på B. Ingen bild laddades upp. A ser ingen ny
bild eller ny status.

- [ ] OK  - [ ] FEL

### 2.7 Starta om ATAK på B

1. Stäng ATAK helt på B och starta om. Öppna planeraren.

Förväntat: planeraren öppnas utan försöksdialogen, följandet fortsätter (knappen visar
**Följer** *feedens namn*, och **Sluta följa** finns i dialogen) och en ändring från A dyker upp.

- [ ] OK  - [ ] FEL

## 3. Go eller no-go

Alla sju kontrollerna ska vara OK för **go**.

Får vara FEL utan att stoppa: bara kosmetiska fel (fel text på en knapp) och att ett
enstaka **Uppdatera nu** eller **Publicera** igen behövdes. 2.2 och 2.6 får aldrig vara FEL. Dubblett av enheten, förlorad
ändring, bild som visas mot fel version, eller en bild som laddas upp efter **Nej**
är alltid **no-go**. Vid osäkerhet: **no-go**.

Anteckna för #252 (inga verkliga platser, bilder, servernamn eller konton):

- kandidatens version och signering, ATAK-version, telefonmodeller
- serverversion (TAK Server 5.6) och att feeden saknar lås, roll Subscriber
- datum och klockslag, och resultat OK/FEL per kontroll 2.1-2.7 med korta anteckningar
- om **Okänt om bilden kom fram** eller **Metadata ännu inte i feeden, tryck Publicera igen** visades
- ditt beslut (go eller no-go) och klockslag. Ett lyckat test är inte i sig ett beslut.

**Go:** verkliga övningsenheter och bilder får användas, efter att du skrivit beslutet.
**No-go eller sen:** använd reservplanen (#222): det redan godkända lokala arbetssättet på
senaste signerade paketet, utan att TMM delar bilder. Använd inte Publicera för verkliga data.

## 4. Under övningen

- Publicering är alltid ett eget val: **Publicera**, kontrollera förhandsvisningen, bekräfta.
  Inget skickas av sig självt.
- Appen hämtar nya versioner själv ungefär varje minut. Tryck **Uppdatera nu** om du väntar
  på något eller kommer tillbaka från ingen täckning.
- **Ej publicerad** betyder egna ändringar som inte nått feeden. **Publicerad** syns först
  när feeden visat samma version.
- **Konflikt**: du och en annan har ändrat samma enhet. Öppna enheten och jämför.
  **Behåll min** behåller din (du måste publicera själv sedan), **Använd inkommande** tar den andras.
- **Okänt om bilden kom fram**: tryck inte om direkt. Tryck **Uppdatera nu** och se om bilden finns.
- Att en bild tas bort ur feeden är inte radering på servern. Filen finns kvar på servern
  tills en administratör rensar.
- **Sluta följa** stoppar hämtningen men behåller allt du redan har.

## 5. Efteråt

- Skriv resultaten (avsnitt 3) som en kommentar i #252, utan verkliga platser, bilder,
  servernamn, konton eller nycklar.
- Ta inte med skärmbilder som visar operativa data.
- Ta bort testenheten på båda telefonerna. Be serverägaren rensa testbilden och testenheten
  ur feeden.
- Noterade avvikelser blir egna Issues.
