# 8vo

8vo is an experimental native reader. The Windows application supports EPUB
and now has a bounded Stage 1 PDF reader; an EPUB-only Android port is being
developed in parallel milestones. Other document and comic-book formats may be
explored later.

The name `8vo` is the bibliographic abbreviation for *octavo* and is
pronounced *octavo*.

## Why this project exists

I am building 8vo to explore how far coding agents such as Codex can go in
creating a large, working software project. I set the direction, review the
results, and evaluate the software, while agents do the implementation work.

8vo is under active development, has no public binary release yet, and is not
ready for general use.

## Windows build and test

Building requires 64-bit Windows, Git, PowerShell 5.1 or newer, Visual Studio
2022 or Build Tools 2022 with the C++ workload and a Windows SDK, and the exact
MuPDF source/submodule closure pinned in `vendor/mupdf_dependency`.

8vo compiles four first-party repositories from exact revisions. Bootstrap
those revisions into the ignored local dependency directory, then build and
run the public smoke suite:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bootstrap_dependencies.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run_public_smoke.ps1
```

See [DEPENDENCIES.md](DEPENDENCIES.md) for the exact pins and manual checkout
options.

The Win32 PDF Stage 1 surface opens `.pdf`, renders a fit-page premultiplied
raster, and supports Previous/Next, Back/Forward, and direct progress seek.
PDF search, Contents, settings, annotations, persistence/sync, and Android PDF
are intentionally deferred. Run its deterministic product/lifecycle proof with
`scripts\win32_octavo_pdf_stage1_smoke.ps1`.

Architecture details are in [docs/architecture.md](docs/architecture.md).

## Android Port 8 candidate

Accepted Ports 0-7 start on an 8vo-owned library, keep a deterministic
sample, import multiple EPUBs through Android's document picker, deduplicate
identical bytes by SHA-256, and store an independent successfully presented
Reader0 position for every book. Imported books can return to the library,
resume the exact canonical page under the same layout, and be removed without
deleting the provider-owned original. Catalog, managed-file, picker, lifecycle,
and presentation policy stay inside 8vo. Reader0 remains authoritative for
opening, pagination, navigation, canonical frames, and location restore; Port
7's premium appearance, semantic reflow, immersive chrome, and accessibility
foundation remain intact.

The historical Port 8 candidate added bounded Contents, Go-to, Return/Forward,
and progress choices through the then-current Reader0 API 7 surface. The
current tree consumes Reader0 API 10's compatible EPUB surface. Port 8's
corrective slice also closes six reported
navigation, pagination, image-page, chapter-targeting, and top-padding defects;
adds narrow, bounded image-only and in-flow image presentation; and hardens the
media preparation/presentation transaction. Corrected API 36 emulator and API
34 iQOO automation, external-restart, and 130%-text/animations-off
qualification are green. Controlled real-book review also closes the six
reported defects without a visible bright/black transition. Audible TalkBack,
UI polish, and user subjective/manual acceptance remain pending. Earlier Port
8 results remain predecessor evidence rather than proof for the current source.

Port 9 adds the merged Reader0-backed bounded in-book search slice. Port 10's
merged baseline adds Reader0-authoritative word selection and Copy on the same
exact dependency closure. Its API 36 and API 34 focused, ordinary, restart,
actual 130%-system-text/reduced-motion, empty-crash, visual, physical touch, and
audible TalkBack gates pass. The iQOO's 26 files and 4,751,505 bytes restored
byte-for-byte, and live Launcher/SystemUI motion was verified after explicit
  restoration. The Port 10 follow-up now continues either handle across presented
  pages inside one Reader0 spine, repeats only after successful presentation,
  stops explicitly at chapter boundaries, keeps Copy bounded, and supplies a
  native row-centered drag loupe. Its complete API 36 emulator and API 34 iQOO
  automation, restart, large-text, crash, exact-restore, touch, and bounded
  audible TalkBack gates pass. Cross-spine selection remains an explicit
  non-goal rather than a launch claim.

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

The current candidate scope and validation boundary are documented in
[docs/android_port10.md](docs/android_port10.md); the bounded search and
structural-navigation predecessors are documented in
[docs/android_port9.md](docs/android_port9.md) and
[docs/android_port8.md](docs/android_port8.md). The accepted appearance
foundation is documented in [docs/android_port7.md](docs/android_port7.md),
and the accepted library milestone in
[docs/android_port6.md](docs/android_port6.md). The document-open/resume
milestone is documented in [docs/android_port5.md](docs/android_port5.md), the
readable typography milestone in [docs/android_port4.md](docs/android_port4.md),
the interactive navigation
milestone is documented in [docs/android_port3.md](docs/android_port3.md), the
static-page milestone in [docs/android_port2.md](docs/android_port2.md), the
lifecycle milestone in [docs/android_port1.md](docs/android_port1.md), and the
toolchain/shared-package foundation in [docs/android_port0.md](docs/android_port0.md).

## License

First-party source code and documentation are licensed under the
[Mozilla Public License 2.0](LICENSE). Third-party components retain their
original licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
