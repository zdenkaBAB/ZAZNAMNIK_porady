# Meeting Manual v0.6

Android meeting recorder for long work meetings. Audio is stored locally and can be transcribed on-device with whisper.cpp.

## Workflow
1. Start recording.
2. Lock the phone if needed; the foreground service continues recording.
3. Pause/resume or stop from the app or recording notification.
4. Open **Prepis / preložiť**.
5. On first use, download the multilingual Whisper base model (~142 MB). This downloads only the model; meeting audio stays on the device.
6. Whisper produces a timestamped transcript locally.
7. If the requested output language differs, ML Kit translates the transcript locally after its translation model is downloaded.
8. Create the structured work manual and export it to Word.

## Local Whisper
The app uses `dev.ffmpegkit-maintained:whisper-android:1.0.0`, a prebuilt Android AAR bundling whisper.cpp. The library supports file-based WAV/MP3/FLAC transcription and multilingual Whisper models.

The default model is `ggml-base.bin` (~142 MB). It is stored in the app's private external files directory after download and is reused for later meetings.

## Recording format
Recordings are 16 kHz, mono, 16-bit PCM WAV. This makes them directly consumable by the local Whisper engine and avoids cloud conversion.

## Privacy
- No meeting audio is sent to a server by this app.
- The only network operation is downloading the Whisper model and, when needed, ML Kit's language model.
- Audio remains in the app's local meeting archive unless the user explicitly shares it.

## Android build
The repository is configured for Android API 35 and `arm64-v8a` devices.
