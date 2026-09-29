# Meeting Manual v0.9.2

Android app for long meeting recording, local transcription, structured manual creation and Word export.

## Speech recognition

Version 0.9 uses sherpa-onnx with NVIDIA Parakeet TDT v3 INT8. The model supports 25 European languages including Slovak. The model is downloaded from the official sherpa-onnx GitHub release and then runs locally on the phone.

The app records 16 kHz mono PCM WAV and processes long recordings in 60-second chunks to avoid loading a multi-hour recording into RAM at once.

First model download is large (~640 MB). A resumable downloader is used and the archive is deleted after successful extraction.

## Build

GitHub Actions builds the debug APK using the existing workflow.


Build note v0.9.2: fixed Sherpa-ONNX Kotlin acceptWaveform argument order and removed the obsolete WhisperTranscriber source.
