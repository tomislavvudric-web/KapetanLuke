# Kapetan Luke v0.6

Glavne izmjene:
- potpuno prepisan zoom/pan: dva prsta upravljaju samo kartom, do 5× zoom
- veliki Harbour Control HUD (250 px), izvan zooma
- INFO tipka s većom dodirnom zonom
- 20 različitih brodova; svaka nova igra nasumično bira 7
- provjera dokova 85 / 60 / 140 m i preostale slobodne dužine
- jasne poruke zašto brod ne može biti privezan
- osvježena paleta mora, obale, dokova i brodova
- reset generira novu kombinaciju brodova


## v0.6.1
- Ispravljena nedostajuća završna zagrada u onDraw(), koja je uzrokovala Kotlin compilation error i lanac `private is not applicable to local function` grešaka.
