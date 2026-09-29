# Meeting Manual v0.8.0

- Improved Android audio capture: VOICE_RECOGNITION audio source for speech-focused capture.
- Keeps local Whisper transcription and Small Q5 model from v0.7.x.
- Explicit Slovak/English language selection remains enabled.

Note: the current free whisper-android 1.0.0 API exposes the high-level WhisperConfig(language=...) used by this app; advanced beam-search/VAD options are not exposed by that AAR API, so v0.8 does not pretend to enable unsupported options.
