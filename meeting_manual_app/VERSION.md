# Meeting Manual v0.9.2

- Replaced local Whisper transcription with sherpa-onnx + NVIDIA Parakeet TDT v3 INT8.
- Parakeet TDT v3 supports 25 European languages, including Slovak.
- Model is downloaded from the official sherpa-onnx GitHub release and extracted locally.
- Audio remains on the phone; the app does not upload recordings for transcription.
- Long WAV files are processed in 60-second chunks to limit RAM usage.
