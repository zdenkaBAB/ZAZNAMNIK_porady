# Meeting Manual v0.6.0

## What is new
- Real on-device Whisper transcription using the prebuilt whisper.cpp Android AAR.
- Multilingual `ggml-base.bin` model (Slovak and English supported).
- First-run model download (~142 MB); the meeting audio is never uploaded.
- Timestamped transcript segments.
- Optional on-device Slovak/English translation with ML Kit.
- 16 kHz mono WAV recording so the local Whisper engine can process the recording directly.
- Long-running foreground recording remains compatible with screen lock and pause/resume.
- Whisper model status and manual download button in Settings.

## Important
The Whisper base model is intentionally not stored in Git because it is ~142 MB. The app downloads it once to private app storage. After that, transcription can run offline.
