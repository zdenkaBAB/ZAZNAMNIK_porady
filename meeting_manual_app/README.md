# Meeting Manual – Android prototype

Aplikácia je určená na pracovné porady, kde potrebuješ z niekoľkohodinovej nahrávky vytvoriť použiteľný pracovný manuál.

## Workflow
1. Zadaj názov stretnutia.
2. Stlač **ZAČAŤ NAHRÁVANIE**.
3. Nahrávka sa ukladá lokálne do telefónu ako M4A a pokračuje aj pri zhasnutí displeja.
4. Po porade stlač **UKONČIŤ**.
5. **Vytvoriť prepis** – cieľová verzia používa lokálny whisper.cpp.
6. **Vytvoriť manuál** – automaticky vytvorí sekcie Hlavné úlohy, Termíny, Zodpovednosti, Postupy, Otvorené otázky a Kompletný prepis.
7. **Exportovať Word** – vytvorí DOCX, ktorý môžeš poslať kolegom.

## Lokálny Whisper
Projekt je pripravený na integráciu whisper.cpp. Oficiálny Android projekt whisper.cpp používa natívny CMake/JNI build a model v `assets/models`; oficiálne README odporúča pre Android `tiny` alebo `base` model. Pozri: https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android

Pre produkčnú verziu treba pribaliť whisper.cpp Android native library a model (napr. slovenský viacjazyčný `tiny` alebo `base`). Model je veľký súbor, preto nie je súčasťou tohto ZIP prototypu. Aplikácia má zatiaľ offline fallback, aby sa dala nainštalovať a otestovať UI/nahrávanie/export bez modelu.

## Bezpečnosť
Aplikácia neposiela nahrávku na server. Pred pracovným použitím treba overiť interné pravidlá organizácie a právny základ nahrávania účastníkov stretnutia.

## Aktuálna verzia prototypu
- dlhé lokálne nahrávanie
- uloženie poslednej nahrávky
- pracovný manuál z textu s logickými sekciami
- export DOCX cez systémové zdieľanie
- pripravený setup pre whisper.cpp

### Testovací scenár pre tvoju poradu k voľbám
Názov stretnutia napr.: `Pokyny k voľbám – hlavné úlohy`.
Po skončení: **Prepis → Pracovný manuál → skontrolovať → Exportovať Word**.

> Poznámka: aktuálny ZIP obsahuje offline fallback a pripravenú Whisper integráciu; samotný veľký Whisper model nie je pribalený. Po pridaní modelu bude možné prejsť na skutočný lokálny prepis.
