# Meeting Manual v0.7.0

## What is new
- Upgraded the default local Whisper model from base to multilingual small-q5_1 for better Slovak speech recognition.
- Model download is approximately 182 MB.
- Meeting audio remains local and is never uploaded for transcription.
- Existing recording, pause/resume, archive, translation and Word export remain unchanged.

## Why
The previous base model is relatively small (142 MiB / 74M parameters) and can produce poor results on longer Slovak meetings, especially with noise or multiple speakers. The small model has 244M parameters; the q5_1 quantized file is about 182 MiB, providing a better accuracy/memory trade-off for Android.

## Important
The new model is downloaded separately from the previous base model. After the first download, transcription can run locally/offline.
