# Movies App

Native Android video player application built with **Kotlin** and **AndroidX Media3 (ExoPlayer)**, focused on smooth playback, responsive media controls, and support for multiple video formats.

## Features

* **Media3 ExoPlayer Integration:** Reliable video playback engine with lifecycle-aware player management.
* **Playback Controls:** Play/pause, seek scrubbing, buffering state handling, and responsive full-screen controls.
* **Format Support:** Compatible with standard adaptive streaming and local/remote video formats supported by Media3.

## Tech Stack

* **Language:** Kotlin
* **Media Engine:** AndroidX Media3 (ExoPlayer)
* **Build System:** Gradle Kotlin DSL

## Getting Started

Clone the repository and open it in **Android Studio**:

```bash
git clone https://github.com/ViniGouveia/movies-app.git
```

Wait for Gradle sync to complete and run the app on an emulator or physical device.

### Build via Terminal

```bash
# Debug build
./gradlew clean app:assembleDebug

# Release build
./gradlew clean app:assembleRelease
```
