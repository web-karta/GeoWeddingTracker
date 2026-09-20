GeoWedding Tracker — Android

NAMJENA
Aplikacija šalje lokaciju mladoženje ili mlade u istu Supabase tablicu
wedding_live_location koju koristi svadbena web-stranica.

ŠIFRE
2909 = Mladoženja
2029 = Mlada

KAKO POKRENUTI
1. Instaliraj Android Studio.
2. File > Open i odaberi mapu GeoWeddingTracker.
3. Pričekaj Gradle Sync.
4. Spoji Android mobitel USB kabelom i uključi Developer options > USB debugging.
5. Pritisni Run.
   Za APK: Build > Build App Bundle(s) / APK(s) > Build APK(s).

PRVO POKRETANJE
1. Dopusti preciznu lokaciju.
2. Ako Android ponudi postavke lokacije, postavi "Dopusti cijelo vrijeme"
   kada želiš najšire dopuštenje za pozadinsku lokaciju.
3. Dopusti obavijesti.
4. Unesi 2909 ili 2029.
5. Pokreni praćenje.
6. Dok je aktivno, Android prikazuje stalnu obavijest.
7. Praćenje zaustavi u aplikaciji ili tipkom "Zaustavi" u obavijesti.

NAPOMENA
Android foreground location service je namjerno vidljiv korisniku putem stalne
obavijesti. Sustav/korisnik i dalje mogu prisilno zaustaviti aplikaciju ili
ukinuti dozvole; nijedna obična aplikacija ne može zaobići takvu odluku OS-a.
