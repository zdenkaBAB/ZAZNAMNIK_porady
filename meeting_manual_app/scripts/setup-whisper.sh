#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p third_party
if [ ! -d third_party/whisper.cpp/.git ]; then
  git clone --depth 1 https://github.com/ggml-org/whisper.cpp.git third_party/whisper.cpp
else
  git -C third_party/whisper.cpp pull --ff-only
fi
mkdir -p app/src/main/assets/models
cat <<MSG
Whisper source pripravený v third_party/whisper.cpp.

Ďalší krok:
  1. Stiahni viacjazyčný ggml model (tiny alebo base) z whisper.cpp model repository.
  2. Ulož ho ako app/src/main/assets/models/ggml-tiny.bin alebo ggml-base.bin.
  3. V Android Studio nastav ENABLE_WHISPER=true.

Model sa necommitne automaticky, aby GitHub repozitár zbytočne nenarástol o desiatky/stovky MB.
MSG
