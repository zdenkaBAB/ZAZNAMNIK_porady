# Local ASR model setup

The app now uses sherpa-onnx + NVIDIA Parakeet TDT v3 INT8 instead of Whisper.

The model is downloaded from the sherpa-onnx GitHub release, not Hugging Face:
https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models

Model package: `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` (~640 MB extracted).

It supports 25 European languages, including Slovak (`sk`).
