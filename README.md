# ClipForge AI — Native Android starter

Native Kotlin Android project targeting Android 10 (API 29) and newer. Designed for cloud APK builds from an Android-only workflow.

## What is implemented
- Native Android UI.
- Android document picker for videos and model files.
- Local model file import and provider catalog.
- Real model-open validation for ONNX Runtime (`.onnx`) and TensorFlow Lite (`.tflite`).
- Manual clip-range creation.
- MP4 stream remux export using Android `MediaExtractor` + `MediaMuxer` (no re-encoding).

## Important honest capability boundaries
This is a native starter build, not a finished automatic AI highlight detector. AI highlight detection needs task-specific models and preprocessing/postprocessing. Model file formats alone do not define what a model does.
- ONNX and TFLite runtimes are bundled and can open compatible models; this build does not yet include a video/speech highlight model pipeline.
- GGUF files can be imported and catalogued, but llama.cpp's native Android JNI library is not bundled in this starter. A matching ABI build and JNI adapter must be added before GGUF inference works.
- MediaPipe `.task` files can be imported/catalogued, but a task-specific MediaPipe Tasks runtime and adapter must be added for inference.
- API provider is an optional extension point; this build does not send media to an API.
- Export currently remuxes selected original audio/video streams to MP4. It does not re-encode, crop to 9:16, or burn captions. Some source codecs/container combinations may not be accepted by MP4 muxer.
- No models are bundled. Large quality models may be impractical on low-memory Android devices. Download only models compatible with the tablet's RAM, CPU and Android ABI.

## Build APK entirely from Android (GitHub Actions)
1. On Android, create a GitHub repository and upload/extract the contents of this project into its root. Include the `.github` folder.
2. Open the repository's **Actions** tab and enable Actions if GitHub asks.
3. Select **Build ClipForge Android APK**.
4. Tap **Run workflow**.
5. Wait for the workflow to finish, open its successful run, and download the `ClipForge-debug-apk` artifact.
6. Extract the artifact ZIP and install `app-debug.apk`. If Android asks, allow Chrome or your file manager to install unknown apps.

The workflow builds a debug APK. For a release APK suitable for distribution, configure signing securely using GitHub Actions secrets; never commit signing keys.

## Architecture
`ModelProvider` is the provider interface. `ProviderRegistry` maps engines to adapters. Add a provider by implementing `ModelProvider` and registering it. For actual AI clip detection, define a task interface (for example `HighlightDetector`) with model-specific preprocessing, inference and timestamp postprocessing.

## Device note
Target API 29 supports Android 10+. The app needs a device with enough RAM and storage for the selected model. Android 10 devices may have limited support for newer model-runtime features.
