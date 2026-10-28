# Ryuk — AI Companion (Forge 1.20.1)

Source code of the mod published on Modrinth and CurseForge as "Ryuk — AI Companion" (mod id `aicompanion`, version
0.1.0). All Rights Reserved.

## Build

- JDK 17 or newer.
- GeckoLib is a required dependency and is not in this repository: put `geckolib-forge-1.20.1-4.8.4.jar`
  (https://modrinth.com/mod/geckolib, version 4.8.4 for Forge 1.20.1) into `libs/`.
- `gradle.properties` sets `-Djava.io.tmpdir=C:\\Temp`, a workaround for one Windows machine; remove it elsewhere.
- `./gradlew build` (Windows: `dev.cmd build`) gives `build/libs/aicompanion-0.1.0.jar`.

## Binary files in the published jar

Besides the classes and assets built from this repository, the jar carries Ryuk's offline voice in
`aicompanion_voice/`. The build copies it from `run/aicompanion` (not in this repository). On first use the mod unpacks
it into `<game folder>/aicompanion` and runs `piper.exe` as a separate process to turn his lines into speech (Windows
only). No native code is loaded into the game itself. `aicompanion_voice/NOTICE.txt` in the jar lists the same.

Third-party, unmodified, all from the official Piper release 2023.11.14-2, file `piper_windows_amd64.zip`:
https://github.com/rhasspy/piper/releases/tag/2023.11.14-2

| File | Project, licence | SHA-256 |
| :- | :- | :- |
| `piper/piper.exe` | Piper (MIT), https://github.com/rhasspy/piper | `96f3da3811151580073e40bb4dd20eb0fb8115f5f5f76e2fb54282b3edfa5c1f` |
| `piper/piper_phonemize.dll` | piper-phonemize (MIT), https://github.com/rhasspy/piper-phonemize | `4b5f344b2f31204a8a94a0bf485f93e4971671e81188a0d67f326e113bfb0b2e` |
| `piper/onnxruntime.dll` | ONNX Runtime (MIT), https://github.com/microsoft/onnxruntime | `a630f67f4a82b6689e4178bf81d362d945522dd907b2a92cb43cebf72c83a06f` |
| `piper/onnxruntime_providers_shared.dll` | ONNX Runtime (MIT) | `71705b9bd76baced583eb37fe5ec2101946b4de50f1e9bdb156109f7e8082980` |
| `piper/espeak-ng.dll`, `piper/espeak-ng-data/` (English only) | eSpeak NG (GPL-3.0), source at the commit piper-phonemize builds against: https://github.com/rhasspy/espeak-ng/tree/0f65aa301e0d6bae5e172cc74197d32a6182200f | `9588480f8197df62fd8461a8431f8eaec6e8e7749c5ffcbe7fee656fe40a2189` (dll) |

Made for this mod:

| File | What it is | SHA-256 |
| :- | :- | :- |
| `voices/en_US-ryuk-medium.onnx` | Ryuk's voice: a Piper voice model (neural network weights, data, not executable code), fine-tuned with Piper's own training code from the public "john" (medium) checkpoint, https://huggingface.co/datasets/rhasspy/piper-checkpoints/tree/main/en/en_US/john/medium (LibriVox recordings, public domain), on recordings of a voice actor who agreed to its use and publication | `f6516380f0c62bfe58b645b1b80da32054b5a52896157107dc696d5a883e3f26` |
| `voices/en_US-ryuk-medium.onnx.json` | its Piper config (text) | `203186676a8e7b7b75610cc60ebe05e0577a1321c7c067258e13f0b4baf8ba8e` |
