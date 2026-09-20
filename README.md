# Kapetan luke – Android v0.2

Prva igriva verzija simulatora kapetana luke.

## Ugrađeno
- unos imena i prezimena kapetana
- zahtjev prikazan kao službeni e-mail; prihvaća se/odbija cijela grupa
- 5 različitih brodova s imenima, vrstama, dužinama i planiranim danima
- zapadni vezovi 14→1: 1–3 do 45 m, 4–8 do 65 m, 9–14 do 110 m
- ponton: tenderi do 8 m
- gat: do 120 m po strani
- istočna obala: 150 m
- plutajući dokovi 85 m, 60 m i 140 m; veliki je u osi gata
- povlačenje broda i rotacija 90° kratkim dodirom
- osnovna provjera valjanog veza i sudara
- odbrojavanje vremena, bodovi, kazna odbijanja i dvostruka kazna za prihvaćen ali nesmješten brod
- spremanje zadnjih rezultata lokalno
- Postavke za vrijeme i bodove
- Restart

## GitHub APK
Repozitorij sadrži `.github/workflows/build-apk.yml`. Nakon uploada na GitHub otvori Actions → Build APK → Run workflow. APK će biti u Artifacts kao `KapetanLuke-debug-apk`.

Napomena: v0.2 je prototip. Precizna geometrija slobodne dužine gata/obale/dokova i simulacija više dana dodatno se dorađuju nakon prvog testa na mobitelu.
