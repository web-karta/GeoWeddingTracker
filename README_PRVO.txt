SVATOVI UŽIVO — NAJJEDNOSTAVNIJA IZRADA APK-a

Ovaj ZIP je spreman za GitHub. Android Studio nije potreban.

1. Na GitHubu napravi novi repository, npr. geowedding-tracker.
2. Raspakiraj OVAJ ZIP i prenesi SAV SADRŽAJ iz mape GeoWeddingTracker_GitHub_APK
   u korijen repozitorija. Važno: prenesi i mapu .github.
3. Otvori karticu Actions.
4. Otvori workflow "Build Android APK".
   - Ako se nije automatski pokrenuo nakon uploada, klikni "Run workflow".
5. Kad build dobije zelenu kvačicu, otvori taj run.
6. Na dnu, pod Artifacts, preuzmi "Svatovi-uzivo-APK".
7. Raspakiraj preuzeti artifact. Unutra je Svatovi-uzivo.apk.
8. Pošalji APK na Android telefon i instaliraj ga.
   Android može tražiti "Dopusti instalaciju iz ovog izvora".

PRVO POKRETANJE
- 2909 = Mladoženja
- 2029 = Mlada
- Dopusti PRECIZNU lokaciju.
- Dopusti obavijesti.
- Ako Android ponudi postavku lokacije "Dopusti cijelo vrijeme", uključi je.
- Pritisni "Pokreni praćenje".
- Nakon toga aplikacija radi kao foreground location service i može nastaviti
  slati lokaciju dok je zaslon zaključan ili koristiš drugu aplikaciju.
- Stalna Android obavijest pokazuje da je praćenje aktivno.
- Za prekid koristi "Zaustavi".

PODATCI
Aplikacija šalje koordinate u postojeću Supabase tablicu wedding_live_location:
- id 1 = groom
- id 2 = bride

NAPOMENA
Android ipak zadržava krajnju kontrolu: force-stop aplikacije, oduzimanje dozvole,
gašenje lokacije, ekstremna štednja baterije ili gašenje telefona prekidaju praćenje.
