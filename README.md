# DivyaMusic 🎵

DivyaMusic is a music player for Android, built with Kotlin, Jetpack Compose, and Material 3. It is a project by [@divyalokk](https://github.com/divyalokk). The application source lives in the [`app`](./app) directory.

## Table of Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Features

- Browse songs stored on the device, with title and artist
- Play, pause, previous, and next controls
- Seek bar on the home screen and in the full-screen player
- Full-screen player, opened by swiping up on the now-playing card
- Background playback with a media-style notification and controls
- Equalizer page (enabled while audio output is connected)
- Settings page, including Material dynamic colours on Android 12 and newer
- In-app update check against GitHub Releases, with SHA-256 verification before install

## Screenshots

<!-- Add screenshots here, e.g. ![Home screen](docs/screenshots/home.png) -->

_Screenshots coming soon._

## Project Structure

```
divya-music/
├── app/                  # Android application module
│   ├── src/main/         # App sources, resources, and manifest
│   ├── src/debug/        # Debug-only source sets
│   ├── src/release/      # Release-only source sets
│   ├── module.toml       # Module, SDK, dependency, and build type config
│   └── proguard-rules.pro
├── .github/workflows/    # CI: release build on tag push
├── .gitignore
├── LICENSE               # MIT License
└── README.md
```

## Getting Started

### Prerequisites

- Android device or emulator running Android 5.0 (API 21) or newer
- An Android SDK with compile SDK 36
- Java 8 language level (`JAVA_8` in `app/module.toml`)

### Installation

```bash
# Clone the repository
git clone https://github.com/divyalokk/divya-music.git

# Move into the project folder
cd divya-music
```

### Build

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

Build a release APK:

```bash
./gradlew :app:assembleRelease
```

The `divyalok` signing config is used by both build types. The keystore and its passwords are never stored in the repository. Keep them in a local, git-ignored file, or read them from environment variables in the same way as the release workflow (`.github/workflows/release.yml`).

### Run tests

Unit tests live in `app/src/test`:

```bash
./gradlew :app:testDebugUnitTest
```

### Release workflow

Pushing a tag such as `v3.2` runs `.github/workflows/release.yml`. It builds a signed release APK and attaches `app-release.apk` and `app-release.apk.sha256` to the GitHub Release for that tag. The workflow needs these repository secrets: `RELEASE_KEYSTORE_BASE64` (the base64-encoded keystore), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.

## Usage

1. Open DivyaMusic and grant access to music files when asked. On Android 13 and newer, the app asks for audio permission; on older versions, it asks for storage access.
2. Your songs appear in the list on the home screen. Tap a song to start playing it.
3. Use the controls on the now-playing card to pause, skip, or seek.
4. Swipe up on the now-playing card to open the full-screen player.
5. Use the settings icon for appearance options, and the info icon for the About page.
6. When a new release is published on GitHub, DivyaMusic shows an update dialog. Tap **Update** to download the APK. Android then asks you to allow installs from DivyaMusic if you have not done so already. The update installs only if its SHA-256 checksum matches the published `.sha256` file.

Updates install only over a build signed with the same key as the installed app.

## Roadmap

- [ ] Playlists and favorites
- [ ] Search
- [ ] Add screenshots and documentation
- [ ] Add more unit tests
- [ ] First stable release

## Contributing

Contributions, issues, and feature requests are welcome.

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "Add your feature"`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

## License

This project is licensed under the [MIT License](./LICENSE).

## Author

**divyalokk** — [GitHub](https://github.com/divyalokk)
