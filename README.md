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

## Android Port 7 candidate

Accepted Ports 0-6 start on an 8vo-owned library, keep a deterministic
sample, imports multiple EPUBs through Android's document picker, deduplicates
identical bytes by SHA-256, and stores an independent successfully presented
Reader0 position for every book. Imported books can return to the library,
resume the exact canonical page under the same layout, and be removed without
deleting the provider-owned original. Catalog, managed-file, picker, lifecycle,
and presentation policy stay inside 8vo. Reader0 remains authoritative for
opening, pagination, navigation, canonical frames, and location restore; Port
4's readable proportional serif typography remains intact.

The Port 7 implementation candidate adds six independently tuned reader
themes, global typography/layout preferences, canonical semantic-anchor
reflow, measured immersive chrome, a target-colored theme-transition cover,
and a custom-reader accessibility bridge with deterministic keyboard focus.
Its API 36 emulator and dual-ABI build pass, as do its Android 14/API 34
physical-iQOO, Windows, and re10 automated gates. Real-device UiAutomator
traversal also reaches named reader controls in both directions without blank
chrome-container or raw-Surface stops. Audible TalkBack and hands-on keyboard/
switch review, reduced-motion review, extended reading, and subjective
dark-room comfort acceptance are still required. Ports 0-6 therefore remain
the formally accepted Android baseline.

The long-term Android direction is a premium, local-first reader for
user-owned books with Kindle-class capability and interaction quality, a
distinct 8vo design, user-controlled storage, and a better night-reading
experience. See the [Android product vision](docs/android_product_vision.md),
[feature-parity contract](docs/android_feature_parity.md), and
[roadmap](docs/android_roadmap.md).

After bootstrapping the exact dependencies, open `android/` in Android Studio
or build and test it from a JDK 17 command prompt:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bootstrap_dependencies.ps1
cd android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The current candidate scope and acceptance record are documented in
[docs/android_port7.md](docs/android_port7.md); the accepted library milestone
is documented in [docs/android_port6.md](docs/android_port6.md), the
document-open/resume
milestone is documented in [docs/android_port5.md](docs/android_port5.md), the
readable typography milestone in [docs/android_port4.md](docs/android_port4.md), the interactive navigation
milestone is documented in [docs/android_port3.md](docs/android_port3.md), the
static-page milestone in [docs/android_port2.md](docs/android_port2.md), the
lifecycle milestone in [docs/android_port1.md](docs/android_port1.md), and the
toolchain/shared-package foundation in [docs/android_port0.md](docs/android_port0.md).

## License

First-party source code and documentation are licensed under the
[Mozilla Public License 2.0](LICENSE). Third-party components retain their
original licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
