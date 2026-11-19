# TMM Sensor Planner - användarguide

<!-- Maintainers: full reference for the usability source changes after signed candidate 31. Native ATAK/Data Sync steps still require signed-device acceptance under #252. The concise PDF is generated from SNABBGUIDE.md with scripts/render-quick-guide.py. -->

**Om versionen:** Guiden beskriver det uppdaterade gränssnittet efter kandidat 31.
Det ingår ännu inte i den signerade kandidat 31. Kontrollera vilken version du har.
Använd bara övningsdata tills ansvarig person har godkänt användningen.

## 1. Kom igång

### Installera APK:n från ansvarig

Om ATAK redan är anslutet och du prenumererar på gruppens feed i Data Sync ska du
inte skapa en ny serveranslutning. Spara den mottagna APK-filen på telefonen, öppna
den och välj **Installera**. Tillåt installation från appen som öppnar filen om
Android frågar. Starta om ATAK och godkänn att TMM laddas om frågan visas. Öppna
**TMM Sensor Planner** i ATAK:s verktygsmeny. Saknas TMM eller nekas laddningen,
kontakta den som skickade APK:n för kontroll av rätt version.

Om **Arbeta uppkopplad** visas, läs villkoren och välj **Fortsätt**. Vid en helt ny
installation kan TMM sedan fråga **Starta utan lokal historik?**. Välj **Starta utan
historik** endast om du inte har tidigare TMM-data att bevara. Har du använt TMM
tidigare och historik saknas oväntat, avbryt och utred med ansvarig.

### Välj feed i TMM

TMM hjälper dig att skapa enheter, ändra dem vid utplacering och dela uppdateringar
med gruppen. En **feed** är gruppens gemensamma samling i ATAK:s Data Sync.

**Du behöver:** ATAK och TMM aktiverade, rätt serveranslutning i ATAK och åtkomst till
gruppens feed. Feeden ska sakna lösenord och inbjudningskrav, och din roll ska vara
**Subscriber**. För **Publicera** ska bara den avsedda serverprofilen vara aktiverad
och den ska vara ansluten.

1. Anslut servern i ATAK. Öppna gruppens feed i **Data Sync** och prenumerera där.
   TMM:s **Följ feed** ersätter inte den prenumerationen.
2. Öppna **TMM Sensor Planner** i ATAK:s verktygsmeny. Välj **Följ feed**.
3. Välj den anslutna ATAK-servern. I **Anslut delad feed** föreslås API-porten som
   redan är sparad i ATAK. Kontrollera att den gäller vald server.
   Ändra vid behov under **Anslutningsinställningar** och välj **Läs tillgängliga feeds**.
4. Välj gruppens feed i **Välj feed att följa**. Läs sammanfattningen och bekräfta
   med **Följ feed**. Vid första installationen, välj **Starta saknad historik** om
   frågan visas. Saknas tidigare historik oväntat: avbryt och kontakta ansvarig.
5. Kontrollera att rätt feed visas. TMM hämtar nu uppdateringar automatiskt medan
   ATAK körs och pluginet är laddat.

### Vad är anslutningsporten?

TMM föreslår värdet från ATAK:s **Secure Server API port**. Du behöver normalt inte
leta fram och skriva in det igen. Värdet används för att läsa feeden och överföra
bilder; det är inte ATAK:s strömport. Saknas inställningen visas **8443** som standard.
Om ett ogiltigt värde läses måste du ange rätt port under **Anslutningsinställningar**.
Kontakta serveransvarig om värdet är okänt. TMM ändrar inte ATAK:s inställning eller
adressen för en feed du redan följer.

Du väljer feed en gång per telefon. Efter omstart fortsätter följandet. Via feedens
knapp kan du välja **Uppdatera nu**, **Sluta följa** eller **Följ igen**. Att sluta
följa behåller det du redan har sparat.

**Arbeta lokalt:** Du kan skapa och redigera utan att följa en feed. Att spara lokalt
publicerar ingenting. Anslutningen till en server delar inte dina privata utkast.

<!-- page -->

## 2. Skapa och placera ut

1. Tryck **Skapa enhet**. Ange **Enhetens ID**, typ (Mini, Scout, PIR eller Gateway),
   ägare och status. Använd samma ID som på enhetens märkning.
2. Välj **Välj plats på kartan**, flytta kartan så att siktet ligger rätt och bekräfta med **Använd plats**.
   Du kan också skriva en fullständig MGRS-referens i formuläret.
3. För **PIR** och **Scout**: ange **Bäring** i hela mils på en 6400-milscirkel.
   Mini och Gateway har ingen bäring. Sann nord är referensen och vinkeln går medurs:
   **0/6400 norr**, **1600 öster**, **3200 söder**, **4800 väster**.
4. Tryck **Spara lokalt**. Öppna enheten och kontrollera uppgifterna. Den är sparad
   på telefonen men har ännu inte publicerats.
5. Lägg till en bild med **Lägg till bild**. Granska och bekräfta. Använd
   **Ersätt bild** för att välja en annan bild. Tryck på bilden eller **Visa bild**
   för att förstora, zooma och panorera.

### När enheten placeras ut

Öppna samma enhet i listan eller via dess TMM-symbol på kartan. Skapa inte en ny
kopia för utplaceringen. Använd **Redigera** för status, bäring, ägare, kommentar
eller utplaceringstid. **Flytta på kartan** öppnar kartvalet; avsluta med
**Spara lokalt**. Lägg till eller ersätt bilden och publicera därefter ändringarna.

Du väljer själv mellan **Planerad**, **Utplacerad**, **Aktiv**, **Intagen** och
**Avbruten**. Utplaceringstiden anges manuellt; statusbytet sätter den inte automatiskt.

### Hitta och hantera enheten

- **Visa på kartan** centrerar kartan. **Kopiera MGRS** kopierar positionen.
- **Närmaste annan TMM** och gatewayavstånd är kartmått, inte radiotäckning.
  Bara Planerad, Utplacerad och Aktiv räknas som möjliga grannar.
- **Fler uppgifter** innehåller bland annat **Ta bort bild** och lokal radering.
  Lokal radering tar inte tillbaka sådant som redan har publicerats.

Enheten har en lokal bild åt gången. Originalfilen ändras inte. TMM sparar en
separat PNG utan inbäddad metadata, högst 1920 pixlar på längsta sidan. En vald
JPEG/PNG får vara högst 10 MiB. Kartpilen visar bäring, inte sensorns räckvidd.

<!-- page -->

## 3. Publicera till gruppens feed

**Publicera är ett eget val.** Att spara, ta emot eller lösa en konflikt skickar
inte dina ändringar. TMM visar din valda feed som mål i förhandsvisningen.
Kontrollera att du väljer samma feed i ATAK:s Data Sync; det är Data Sync som
bestämmer vart uppgifterna skickas.

1. Öppna den sparade enheten och tryck **Publicera**.
2. Granska **Publicera till** och feedens namn, uppgifterna och eventuell ny bild.
   Vid flytt ser du gammal och ny position och måste bekräfta att den nya positionen
   är kontrollerad. Tryck **Publicera** för att gå vidare.
3. Om TMM-dialogen ligger i vägen, tryck **Fortsätt i ATAK** för att stänga den
   och utföra rätt alternativ nedan. TMM fortsätter kontrollera feeden i bakgrunden.

Följ TMM:s förhandsvisning. Även en enhet som redan finns i feeden kan behöva en
ny kartkopia i ATAK på den här telefonen.

**TMM visar NY ENHET I KARTAN:** öppna den avsedda feeden i ATAK:s
**Data Sync**, välj **Add Items**, markera enheten och tryck **Done**.
Publicera i TMM gör inte hela detta första steg åt dig.

**Annars, uppdatering av befintlig kartkopia:** ATAK kan visa **Publish Content to Feeds**.
Välj bara den avsedda feeden och besvara frågan. **Nej** återställer inte en
kartändring som redan gjorts i ATAK.

TMM väntar upp till cirka 30 sekunder på att exakt de publicerade uppgifterna
syns i feeden och laddar därefter upp en ny bild. En oförändrad bild laddas inte
upp igen. Utan bild publiceras bara uppgifterna. Du får ett resultat när försöket
är klart. Om dialogen är kvar visas **Väntar på feeden** och vid en ny bild
**Publicerar bilden**; efter **Fortsätt i ATAK** är dialogen stängd under väntan.

ATAK:s egna knapptexter kan skilja sig mellan installationer. Data Sync bestämmer
metadataflödets destination; kontrollera därför feedvalet där också. **Publicerad**
betyder att TMM har verifierat uppgifterna i feeden, inte att alla mottagare har
hämtat dem.

### Om publiceringen inte blir klar

- **Metadata ännu inte i feeden, tryck Publicera igen:** slutför Data Sync-steget
  och vänta en stund. Ingen ny bild laddades upp. Tryck sedan **Publicera** igen.
- **Okänt om bilden kom fram:** kontrollera feeden med **Uppdatera nu** innan du
  försöker igen. TMM skickar inte om automatiskt.
- **Metadata publicerad · bilden inte bekräftad:** läs resultatet; uppgifterna och
  bilden kan ha olika utfall. En publiceringsbekräftelse är ingen mottagarkvittens.

**Visa detaljer** öppnar tekniska publiceringsuppgifter om du behöver hjälp med ett fel.

**Avbryta:** före bekräftelsen kan du avstå utan att starta publiceringen. När ett
anrop redan pågår kan skickade uppgifter inte tas tillbaka. En känd brist i
avbrytandet under bilduppladdning utreds; lita därför inte på **Avbryt** som garanti
för att bilden inte kopplas till feeden. Kontrollera resultatet i feeden vid osäkerhet.

<!-- page -->

## 4. Ta emot och fortsätt arbeta

När du följer en feed hämtar TMM nya uppgifter och bilder ungefär varje minut och
när telefonen återfår kontakt. Du behöver inte göra en manuell import för varje
ändring. Via feedens knapp finns **Uppdatera nu** om du vill kontrollera direkt.

Ändringar som inte krockar med ditt lokala arbete tillämpas automatiskt. TMM använder
samma enhets identitet, så att en utplacerad enhet uppdateras i stället för att bli
en ny enhet. En mottagen bild kan bli enhetens aktuella lokala bild.

### Läs enhetens delningsstatus

- **Sparad lokalt:** enheten finns på telefonen. Texten är inte en bekräftelse
  på att enheten är publicerad.
- **Ändringar att publicera:** du har lokala ändringar som inte matchar feeden.
- **Konflikt:** din version och den inkommande skiljer sig; jämför innan du väljer.
- En feedstatus visar anslutning och hämtning. Den är inte i sig ett besked om att
  en viss enhet eller bild har levererats till alla.

### Lös en konflikt

Öppna enheten och tryck **Lös konflikt**. Jämför **Lokalt (din version)** med
**Inkommande**. TMM behåller din version tills du väljer.

- **Behåll min** behåller ditt arbete. Det behöver publiceras om gruppen ska få det.
- **Använd inkommande** tar in den andra versionen lokalt.
- **Stäng** lämnar konflikten oförändrad.

Om flera bilder visas som **Bildkandidat**, välj en med **Välj bild** innan du
väljer **Använd inkommande**. Ingen bild väljs åt dig. Inget av konfliktvalen
publicerar något.

### Utan uppkoppling

Allt du redan har går att läsa och ändra. Ändringarna sparas lokalt. När kontakten
återkommer hämtar TMM ikapp; du publicerar dina egna ändringar när du är redo.

Äldre manuella funktioner för metadatahämtning, bildexport och publiceringsprov
finns under **Avancerat / test**. De behövs inte för arbetsflödet i denna guide.
Att ta bort en bild ur feeden raderar inte serverns innehållslager; serveransvarig
hanterar eventuell rensning där.
