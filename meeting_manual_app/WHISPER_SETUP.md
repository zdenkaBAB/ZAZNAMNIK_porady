# Lokálny Whisper – setup

Oficiálny whisper.cpp má Android projekt s JNI/CMake integráciou. Tento projekt je pripravený tak, aby sa Whisper zapol ako voliteľná natívna vrstva.

## 1. Zdrojový kód

```bash
./scripts/setup-whisper.sh
```

## 2. Model

Použi viacjazyčný model, nie `*.en`, pretože stretnutia budú v slovenčine. Pre prvý test odporúčam `tiny`; pri lepšom telefóne môžeš skúsiť `base`.

Model umiestni do:

`app/src/main/assets/models/`

Napr.:

`ggml-tiny.bin`

Oficiálne whisper.cpp uvádza pre Android ako vhodné prvé modely `tiny` alebo `base`.

## 3. Prečo model nie je v ZIP-e

Model je veľký binárny súbor. Nechávame ho mimo GitHub repozitára a používateľ si ho pridá samostatne. Po jeho pridaní môže byť prepis úplne lokálny a bez odosielania zvuku na server.

## 4. Dôležité pre niekoľkohodinové porady

Finálna verzia nebude posielať celé 3-hodinové audio naraz do Whisperu. Nahrávku rozdelí na menšie segmenty, napr. 30–60 sekúnd, každý segment prepíše a výsledky spojí s časovými značkami. To znižuje RAM nároky a umožňuje pokračovať aj pri dlhých poradách.
