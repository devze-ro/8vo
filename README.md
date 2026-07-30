# 8vo

8vo is an experimental native reader. The working application currently
targets Windows and supports EPUB; an Android port is being developed in
bounded milestones. Other document and comic-book formats may be explored
later.

The name `8vo` is the bibliographic abbreviation for *octavo* and is
pronounced *octavo*.

## Why this project exists

I am building 8vo to explore how far coding agents such as Codex can go in
creating a large, working software project. I set the direction, review the
results, and evaluate the software, while agents do the implementation work.

8vo is under active development, has no public binary release yet, and is not
ready for general use.

## Windows build and test

Building requires 64-bit Windows, Git, PowerShell 5.1 or newer, and Visual
Studio 2022 or Build Tools 2022 with the C++ workload and a Windows SDK.

8vo compiles four first-party repositories from exact revisions. Bootstrap
those revisions into the ignored local dependency directory, then build and
run the public smoke suite:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bootstrap_dependencies.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run_public_smoke.ps1
```

See [DEPENDENCIES.md](DEPENDENCIES.md) for the exact pins and manual checkout
options.

Architecture details are in [docs/architecture.md](docs/architecture.md).

## Android Port 5

The Android port can now open a user-selected EPUB through Android's document
picker, copy it into app-private storage, and resume the last successfully
presented Reader0 location after pause, surface replacement, or Activity/process
recreation. Selection, managed-file ownership, and durable session policy stay
inside 8vo. Reader0 remains authoritative for opening, pagination, navigation,
canonical frames, and semantic location restore; Port 4's readable proportional
serif typography remains intact.

After bootstrapping the exact dependencies, open `android/` in Android Studio
or build and test it from a JDK 17 command prompt:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bootstrap_dependencies.ps1
cd android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The current scope and acceptance criteria are documented in
[docs/android_port5.md](docs/android_port5.md); the readable typography
milestone is documented in [docs/android_port4.md](docs/android_port4.md), the interactive navigation
milestone is documented in [docs/android_port3.md](docs/android_port3.md), the
static-page milestone in [docs/android_port2.md](docs/android_port2.md), the
lifecycle milestone in [docs/android_port1.md](docs/android_port1.md), and the
toolchain/shared-package foundation in [docs/android_port0.md](docs/android_port0.md).

## License

First-party source code and documentation are licensed under the
[Mozilla Public License 2.0](LICENSE). Third-party components retain their
original licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
