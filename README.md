# TMM Sensor Planner

Projektstart för ett ATAK-CIV-plugin för lokal planering av TMM-enheter.
**Kompatibilitetsmål: ATAK-CIV 5.5.0; ännu inte verifierat.**

Här finns nu en SDK-oberoende Java-kärna för lokala utkast, validering och
horisontella kartavstånd. Det finns ännu ingen installerbar Android-app,
kartintegration, permanent lagring eller delning.

## Kom igång

Med JDK 17 (Java-koden kompileras för Java 8):

```sh
sh scripts/test.sh
```

Testerna kräver inga externa bibliotek eller ATAK SDK.

- [Projektplan](docs/PROJECT_PLAN.md)
- [Produkt och krav](docs/PRODUCT.md)
- [Utvecklingsmiljö och SDK-grind](docs/DEVELOPMENT.md)
- [Förstudie-backlog](docs/BACKLOG.md)
- [Tester](docs/TESTING.md)

Nästa leverans är ett minimalt plugin byggt med det officiella 5.5.0-SDK:t.
API:er, lokal kartsekretess och delningsväg måste verifieras innan integration.
Endast syntetiska testdata hör hemma i repot. Licensval återstår.
