# Bidra

Följ Issue → branch → PR → review → squash merge. Använd feature/<issue>-namn,
fix/<issue>-namn eller docs/<issue>-namn. För uppstartsarbetet används lokala
backlog-ID:n tills GitHub Issues finns. Beskriv krav-ID, testbevis och påverkan
på data i PR. main ska skyddas när CI-kontrollerna finns och har verifierats.

Håll Java-kärnan fri från Android/ATAK-beroenden och kompatibel med Java 8 tills
5.5.0-SDK:t fastställer verktygskedjan. Kör sh scripts/test.sh.
Endast syntetiska data. Licens och bidragsvillkor är ännu inte beslutade;
inhämta det beslutet innan externa bidrag eller distribution.
