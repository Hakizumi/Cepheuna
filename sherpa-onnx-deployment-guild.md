# Sherpa-ONNX Pure Java Deployment Guide
## Windows, macOS, and Linux

This document is for end users who want to deploy **Sherpa-ONNX in pure Java**.

---

## Scope

This guide covers:

- pure Java deployment
- Windows, macOS, and Linux
- runtime library selection by CPU architecture
- model selection by language and hardware profile
- local file-based offline speech recognition

Sherpa-ONNX non-Android Java uses **two JARs**:

- one main Java JAR
- one platform-specific native library JAR

---

## Runtime Selection

Choose the runtime by **operating system + CPU architecture**, not by CPU brand.

### CPU architecture rules

- **Intel** desktop/server CPUs are usually **x64**
- **AMD** desktop/server CPUs are usually **x64**
- **Apple Silicon** Macs are **arm64 / aarch64**
- **ARM Linux** servers and embedded boards are **arm64 / aarch64**

### Runtime mapping

| System  | CPU type               | Native runtime  |
|---------|------------------------|-----------------|
| Windows | Intel / AMD            | `win-x64`       |
| macOS   | Intel                  | `osx-x64`       |
| macOS   | Apple Silicon          | `osx-aarch64`   |
| Linux   | Intel / AMD            | `linux-x64`     |
| Linux   | ARM server / ARM board | `linux-aarch64` |

---

## Recommended Model Selection

### SenseVoice int8
Use this when you need:

- Chinese
- Mixed Chinese and English
- Japanese
- Korean
- Cantonese
- A practical default for business deployment

Recommended model:

`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17-int8`

Expected files inside the model directory:

- `model.int8.onnx`
- `tokens.txt`

### Whisper
Use this when you need:

- mostly English recognition
- a familiar ASR model family
- a broad community baseline

### Moonshine
Use this when you need:

- lighter deployment
- smaller runtime footprint
- faster startup

### Paraformer
Use this when you need:

- Chinese-focused ASR
- a Chinese engineering-oriented deployment path

---

## Recommended Defaults

### General multilingual deployment
- model family: **SenseVoice int8**
- runtime: choose by OS and architecture

### English-first deployment
- model family: **Whisper**
- runtime: choose by OS and architecture

### Lightweight deployment
- model family: **Moonshine**
- runtime: choose by OS and architecture

---

## Maven Dependency

```xml
<!-- Sherpa onnx api dependency -->
<dependency>
  <groupId>com.litongjava</groupId>
  <artifactId>sherpa-onnx-java-api</artifactId>
  <version>1.0.1</version>
</dependency>

<dependency>
  <groupId>com.k2fsa.sherpa.onnx</groupId>
  <artifactId>sherpa-onnx-native-lib-linux-x64</artifactId>
  <version>latest-native-lib-version</version>  <!-- See https://github.com/k2-fsa/sherpa-onnx/releases --> 
</dependency>
```

### Notice: Sherpa onnx native runtime lib is not published on maven central repository,so you should install it to your local repository
```shell
mvn install:install-file `
  -Dfile=./lib/sherpa-onnx-native-lib-<system>-<platform>-<version> `
  -DgroupId=com.k2fsa.sherpa.onnx `
  -DartifactId=sherpa-onnx-native-lib-<system>-<platform> `
  -Dversion=<version>
  -Dpackaging=jar
```

---

## Windows Deployment

Use this runtime on most Windows machines:

- `sherpa-onnx-native-lib-win-x64-<version>.jar`

---

## macOS Deployment

### Intel Mac
Use:

- `sherpa-onnx-native-lib-osx-x64-<version>.jar`

### Apple Silicon Mac
Use:

- `sherpa-onnx-native-lib-osx-aarch64-<version>.jar`

---

## Linux Deployment

### Linux x64
Use:

- `sherpa-onnx-native-lib-linux-x64-<version>.jar`

### Linux ARM64 / AArch64
Use:

- `sherpa-onnx-native-lib-linux-aarch64-<version>.jar`

---

## Minimal Pure Java Example

Use this example as a skeleton. Replace the recognizer configuration with the exact configuration required by your chosen model family.

```java
public class Main {
    static void main(String[] args) {
        String modelDir = "model";
        String audioFile = "audio/test.wav";

        System.out.println("Model directory: " + modelDir);
        System.out.println("Audio file: " + audioFile);

        // Initialize Sherpa-ONNX recognizer here
        // Load model.int8.onnx and tokens.txt here
        // Read WAV samples here
        // Run offline recognition here
        // Print recognition result here

        System.out.println("Replace this skeleton with the exact Java API calls for your selected model.");
    }
}
```

---

## Model and Runtime Decision Rules

Use **SenseVoice int8** when:

- you need Chinese or multilingual recognition
- you want the safest default deployment
- you want a compact int8 model

Use **Whisper** when:

- your workload is mostly English
- you want a familiar model family

Use **Moonshine** when:

- you care more about lightweight deployment and startup speed

Use **Paraformer** when:

- your workload is Chinese-focused

Use **x64 runtime** when:

- the machine is Intel or AMD

Use **arm64/aarch64 runtime** when:

- the machine is Apple Silicon or ARM Linux

---

## Download Notes

You need:

- the Sherpa-ONNX Java JAR
- the matching Sherpa-ONNX native runtime JAR
- one model directory with the files required by the selected model family

Keep the model outside the JAR. Do not package large models inside your application artifact.

---

## Practical Default

For most users, the safest first deployment is:

- runtime:
  - Windows Intel/AMD -> `win-x64`
  - macOS Intel -> `osx-x64`
  - macOS Apple Silicon -> `osx-aarch64`
  - Linux Intel/AMD -> `linux-x64`
  - Linux ARM -> `linux-aarch64`
- model: **SenseVoice int8**

This gives a practical multilingual offline setup with a smaller deployment profile than larger full-precision models.
