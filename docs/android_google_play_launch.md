# Android Google Play launch contract

Status: launch-gap audit and revised first-release scope as of 2026-08-07. The
merged API 36/API 34 Port 10 selection baseline is accepted. Port 11's local
bookmarks and multi-color highlights are accepted, and the local note/draft/
conflict slice passes API 36/API 34 automation and physical touch acceptance;
its bounded TalkBack review remains. Google Drive synchronization and the
remaining bounded UI polish are still required launch gates and are not yet
implemented.
Policy links were checked on 2026-08-06 and must be rechecked before submission.
This document authorizes no push, merge, signing-key operation, Play Console
mutation, or publication.

## First-release scope

The first Google Play release is a local-first EPUB reader for
user-owned books. It includes only:

- the bounded app-private Library, Android document picker, managed-copy import,
  duplicate detection, removal, and per-book resume already owned by 8vo;
- Reader0-authoritative EPUB interpretation, pagination, locations, Contents,
  Chapter/Location/Page/Percentage Go-to, and presentation-gated Return/Forward;
- bounded session-local in-book search with contextual snippets, retained/total
  disclosure, direct/previous/next navigation, and page-hit emphasis;
- Reader0-authoritative session-local word selection with long press, bounded
  handles, same-spine continuation across successfully presented pages, Android
  contextual Copy, selection-first system Back, and virtual-page Select/Copy/
  Clear/Extend accessibility actions;
- durable per-book bookmarks, multi-color highlights created from Reader0-
  authoritative selections, attached or point-anchored notes, and a bounded
  annotations workspace for review, edit, delete, filter, and direct navigation;
- atomic local annotation persistence with stable record identity, exact EPUB-
  digest plus Reader0 anchor ownership, visible capacity/failure states,
  corruption recovery, forward migration, and deterministic offline merging;
- explicit opt-in Google Drive synchronization for managed EPUB copies, Library
  identity, last successfully presented reading positions, global appearance/
  progress preferences, and annotations. Core reading and every local mutation
  must remain available offline;
- least-privilege Drive storage: hidden `appDataFolder` records for portable
  state and sync manifests, plus an app-created user-visible 8vo folder for EPUB
  files. Request only `drive.appdata` and `drive.file`; do not request broad
  whole-Drive access;
- user-visible Sync now, pending/progress/failure/conflict state, retry and
  metered-network policy, explicit disconnect, separate local/cloud deletion,
  and no proprietary 8vo account or server;
- the accepted appearance choices, native reader chrome, progress-display
  choices, lifecycle/surface recovery, and successful-presentation durability;
- the current bounded text and image presentation contract, including explicit
  failure states and existing cache/resource limits;
- native Android layout, input, system Back/insets, 48dp actions, system text
  scaling, keyboard/switch paths, and the existing accessibility bridge;
- bounded UI polish of the already-qualified Navigation surface followed by
  annotations, synchronization, Appearance, reader chrome, and Library, using
  the agreed UI0-derived native-Android pattern; and
- an English-first store listing and application surface.

The launch must use either a polished empty Library or a launch-quality original,
public-domain, or otherwise licensed sample. The current engineering fixture
labelled `Octavo Android Port 6` is test evidence, not acceptable first-run
product content.

## Explicitly deferred

The first release does not add:

- Unicode-aware durable indexing, paragraph selection expansion, annotation
  full-text search, broad citation/export workflows, or cross-spine highlights;
- collections, library search, cover/thumbnail expansion, or spatial previews;
- a proprietary 8vo account/backend, analytics, advertising, or a second cloud
  provider beyond the required Google Drive transport;
- embedded publication fonts, full complex-script shaping, or complete EPUB
  publication semantics for headings, links, tables, notes, and language;
- fixed-layout EPUB, comics, audio/video books, or broad publisher-fidelity work;
- per-book appearance overrides or cross-device-identical pagination;
- broad localization beyond the English-first release; or
- a speculative shared Android framework or re10 extraction before a real
  second consumer proves the identical contract.

Store copy and screenshots must describe only shipped behavior. Private book
content and Kindle branding, assets, trade dress, or comparisons must not be
used in the listing.

## Release blockers

### Product and identity

- Confirm `ro.devze.octavo` as the permanent application ID before the first
  Play upload. Confirm the public product name, stable version name, unique
  version code, default language, category, pricing, and distribution regions.
- Replace the `0.8.0-dev` release identity with a deliberate public version while
  keeping native and Android version records coherent.
- Add a production adaptive launcher icon and round icon. Prepare the required
  512x512 Play icon, 1024x500 feature graphic, short and full descriptions, and
  at least two accurate screenshots. Google documents the current mandatory
  assets in [Add preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).
- Replace or remove the engineering sample from first-run Library state and move
  Alpha/Beta fixture books used only by instrumentation out of production assets.
- Complete whole-app audible TalkBack/touch-exploration and hands-on transition,
  touch, keyboard/switch, compact/large viewport, and first-run review after the
  bounded polish candidate passes on the emulator. The Port 9 search-sheet
  audible/touch gate is complete but does not substitute for that broader pass.

### Annotations and local durability

- Freeze a concise, versioned annotation record before UI implementation. It
  must use the managed EPUB digest for book identity and Reader0-owned spine/
  byte anchors for point bookmarks and same-spine ranges; Android must not
  reinterpret EPUB text or store Java character offsets as canonical anchors.
- Give every bookmark, highlight, note, mutation, and tombstone stable portable
  identity. Merge ordering must not depend on wall-clock agreement. Concurrent
  note edits must remain recoverable and visible rather than silently losing one
  device's text.
- Keep storage caller-owned, bounded, checksummed, versioned, and atomically
  replaced. A failed create/edit/delete must leave the previous durable file and
  visible reader state authoritative, retain the user's unsaved note draft when
  applicable, and provide an explicit retry path.
- Implement and qualify current-location bookmark toggle; selection-to-
  highlight with accessible theme-tuned colors; highlight removal; note create,
  edit, delete, and cancel; annotation-list filtering and navigation; stable
  rendering across page turns and reflow; and lifecycle/process restart.
- Define migration, corruption quarantine/recovery, import/export validation,
  capacity disclosure, book removal/re-import behavior, and exact rollback tests
  before any cloud transport consumes the records.

### Google Drive and synchronization

- Build and qualify a provider-neutral deterministic merge engine locally before
  adding Google APIs. It must merge portable snapshots/operations, positions,
  preferences, annotations, tombstones, and managed-book identities while
  remaining device-clock-independent, idempotent, bounded, and safe to retry.
- Keep local state authoritative while offline. Queue local changes durably;
  never block reading, annotation editing, or app startup on authorization or
  network access. A remote failure must be visible without rolling back a
  successful local mutation.
- Use Google Identity Services authorization only after the user explicitly
  enables Drive sync. Keep authentication and Drive authorization distinct,
  support cancellation and revoked/expired access, and request only
  `drive.appdata` plus `drive.file` when the corresponding feature is used.
- Store state/annotation manifests in `appDataFolder`. Store only user-approved
  managed EPUB copies in an app-created, user-visible 8vo Drive folder, keyed by
  content digest with duplicate suppression and resumable transfer. Never upload
  an external provider original implicitly.
- Provide initial-merge review, manual Sync now, last-success/last-error status,
  pending item counts, Wi-Fi/metered controls, quota and partial-transfer
  handling, deterministic conflict presentation, and safe retry after process
  death or network replacement.
- Provide separate actions to stop syncing, revoke Drive authorization, remove
  only this device's local copies, or delete 8vo-created cloud data with clear
  consequences. Disconnecting must not silently delete either local or remote
  user data.
- Decide and document the launch encryption claim before implementation freezes:
  either add user-controlled client-side encryption with a tested cross-device
  recovery model, or state accurately that Google Drive protects the stored
  data and make no end-to-end-encryption claim.
- Qualify clean first sync, two-device convergence, offline concurrent edits,
  note conflicts, delete/update races, tombstones, interrupted upload/download,
  duplicate EPUBs, low quota, authorization revocation, account switching,
  reinstall/reconnect, schema migration, and exact local recovery.

The current Google contracts are [application-specific Drive data](https://developers.google.com/workspace/drive/api/guides/appdata),
[Drive scope selection](https://developers.google.com/workspace/drive/api/guides/api-specific-auth),
and [Android user-data authorization](https://developer.android.com/identity/authorization).
They were checked on 2026-08-07 and must be rechecked before the Drive adapter
freezes and before Play submission.

### Bounded UI polish

- Apply the proven UI0-derived native-Android pattern to annotations and sync as
  they are built, then finish Appearance, reader chrome, and Library. Retain
  native Android composition, 48dp targets, system text scaling, IME/autofill,
  Back, insets, lifecycle, focus, TalkBack, and reduced-motion behavior.
- Complete light, dark, Warm dark, and High Contrast visual review across
  compact and large viewports. Validate empty, loading, pending, success,
  conflict, capacity, offline, authorization-denied, and failure states; no
  essential action may exist only as a gesture, color, or transient message.

### Release artifact and signing

- Define a reproducible release build and produce a signed Android App Bundle;
  new Play apps publish with AABs under the current
  [Android App Bundle requirement](https://support.google.com/googleplay/android-developer/answer/9844679?hl=en-GB).
- Establish an upload-key and Play App Signing plan following
  [Play App Signing guidance](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en).
  Keystores, passwords, certificates with private material, and signing
  properties must remain outside the repository. No key may be created or
  registered as part of ordinary implementation work.
- Add keystore patterns to ignore rules before any local signing setup. Plan the
  one-time transition from the currently installed Android-debug-signed package;
  it cannot be upgraded in place by a differently signed Play build.
- Generate and retain native debug symbols for the release AAB and upload the
  matching symbol archive. Java shrinking/obfuscation should remain off for the
  first release unless separately justified and qualified; if enabled, retain
  and upload its exact mapping file.
- Package the MPL-2.0 license, required third-party notices/license texts, and a
  durable source-availability statement as required by
  `THIRD_PARTY_NOTICES.md`.

### Privacy and Play policy

- Publish an active, app-specific privacy policy. Complete the Data safety form
  for the final Drive-enabled behavior; Google explicitly requires the form and
  privacy-policy link in
  [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
- Add Internet access only for the bounded Google Drive feature. Do not add a
  proprietary account, analytics, ads, unrelated runtime data SDK, or any other
  off-device transmission. Disclose exactly which book files, reading state,
  preferences, and annotations are transferred to the user's Drive.
- Publish in-app privacy/sync explanations covering opt-in, scopes, storage
  locations, retention, conflict handling, security/encryption claims,
  disconnect, authorization revocation, and deletion of 8vo-created Drive data.
  Keep the privacy policy, OAuth consent screen, and Data safety declarations
  consistent with the shipping behavior.
- Remove or debug-gate production diagnostics that record the full app-private
  document path, EPUB title, or reading anchor. In particular, the state-created
  log in `octavo_android_jni.c` currently publishes document and title values.
- Complete Play App-content declarations, including ads, app access, target
  audience/content, content rating, and privacy/security. The applicable Console
  declarations are summarized by Google in
  [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en).
- Provide the required support email and a durable support/privacy website.

## Already-good evidence

- `compileSdk` and `targetSdk` are 36. This already meets the Android 16/API 36
  threshold that applies to new apps and updates from 2026-08-31 under the
  current [target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-AU).
- `minSdk` 26 is explicit. The application declares no Android permissions, no
  service/provider/receiver, and only its launcher Activity is exported. The
  exact debug candidate now passes the API 26 focused, ordinary, restart,
  accessibility, scaled-text, reduced-motion, and crash-buffer gates. API 26
  release support still requires the Play-equivalent signed-artifact smoke
  below; the code-level minimum-SDK blocker is closed.
- The migrated Navigation inputs use the resolved UI0 selection role on every
  supported API. API 29+ applies the exact resolved accent with public per-view
  cursor/handle setters. API 26-28 use the fixed native compatibility accent
  `#8B7560` through public `colorAccent` and `colorControlActivated` theme
  attributes. Automated checks prove at least 3.5:1 contrast against all six
  actual UI0-derived input surfaces, and an API 26 framework check renders the
  cursor plus center/left/right handles and finds the exact color. The binding
  is guarded by the architecture audit and survives live product-theme changes
  without reflection or private APIs.
- The merged Port 10 baseline has no Internet permission or production runtime
  SDK dependency. EPUB selection uses `ACTION_OPEN_DOCUMENT`; selected bytes,
  catalog state, appearance, progress choice, and reading positions stay app-
  private. This becomes predecessor evidence once the bounded Drive adapter adds
  network access; the final manifest, SDK, and data-flow audit must be repeated.
- `allowBackup` is false, imports and catalogs are bounded, failures remain
  visible, and managed-copy removal does not delete the provider-owned original.
- Both packaged ABIs are 64-bit: `arm64-v8a` and `x86_64`.
- The existing debug APK passes 16-KB zip alignment, and both existing native
  libraries have 0x4000 ELF load alignment. This is useful toolchain evidence,
  but the signed release AAB and Play-generated APKs still require the same gate
  because Google requires 16-KB support for applicable submissions; see
  [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes).
- Exact dependency/architecture guards, strict shared/desktop consumers,
  dual-ABI debug/test builds, API 36 emulator, API 34 physical-device,
  lifecycle/restart, large-text/reduced-motion, crash-buffer, deterministic
  rendering, and byte-exact app-data restoration evidence already pass for the
  current functional candidate.
- For the 2026-08-06 Navigation-polish tree, API 26 passes 26/26 focused,
  85/85 ordinary in 180.560 seconds, confirmed-force-stop restart, and 22/22
  at 130% text with animations disabled in 22.217 seconds. API 36 passes the
  corresponding 26/26 focused, 85/85 ordinary in 245.948 seconds, restart, and
  22/22 reduced-motion matrix in 22.543 seconds. Both crash buffers are empty;
  the API 36 exit history contains only expected user-requested force stops.
  Bounded portrait-phone Navigation visual parity also passes in light, dark,
  and High Contrast.
- The Port 9 in-book-search candidate passes five focused tests, the complete
  API 36 ordinary matrix 90/90 in 4 minutes 2 seconds wall time, and the selected
  130%-text/reduced-motion matrix 27/27 in 30.401 seconds of instrumentation
  time. Confirmed force stop preserves durable state and clears transient
  search; crash review is empty.
  The strict Windows build, seven public smokes, six-theme real-book active-hit
  contrast smoke, and real-book snippet-context smoke also pass.
  Manual Paper, Warm dark, and High contrast review passes for the search sheet
  and active/inactive page emphasis.
- The Port 10 bounded text-selection candidate keeps canonical ranges in
  Reader0 and uses Android's native contextual Copy surface. The exact guards,
  architecture audit, strict Windows build, seven public smokes, and dual-ABI
  Android build pass. API 36 passes 5/5 focused selection tests in 25.544
  seconds, the final ordinary matrix 95/95 in 247.619 seconds, the final
  130%-text/reduced-motion matrix 32/32 in 43.913 seconds, and confirmed-force-
  stop restart. Paper, Warm dark, and High Contrast selection review passes.
  A real system-Back defect found during that review is fixed through the API
  33+ dispatcher and covered by an actual-key regression. The crash buffer is
  empty, and emulator font/animation settings restored exactly.
- The same-spine cross-page follow-up retains Reader0 as the only anchor owner
  and makes page plus endpoint mutation one rollback-safe presentation
  transaction. Its exact guards, architecture audit, strict Windows build,
  seven public smokes, dual-ABI builds, and `git diff --check` pass. On API 36,
  focused selection passes 10/10 in 73.180 seconds, the ordinary matrix passes
  100/100 in 490.402 seconds, external restart passes 1/1 plus 1/1 around
  confirmed force-stop, and the selected 130%-text/reduced-motion matrix passes
  37/37 in 101.257 seconds. Paper, Warm dark, and High Contrast cross-page
  captures pass visual review; settings restored exactly and the crash buffer
  is empty. An unfiltered diagnostic that included the unseeded external restart
  verifier is excluded; the explicitly filtered 100/100 run is authoritative.
- On the API 34 ARM64 iQOO, the follow-up passes 10/10 focused selection in
  16.799 seconds, 100/100 ordinary tests in 178.812 seconds, external confirmed-
  force-stop restart, and 37/37 selected tests in 39.059 seconds at 130% system
  text with normal motion retained. Coordinated touch accepts repeated same-
  spine continuation and the continuously updating, flicker-free, row-centered
  native loupe. The user confirmed that TalkBack exposed Select text, both
  Previous/Next extension actions, and Copy selected text without an issue.
  Cross-spine selection remains an explicit non-goal because Reader0 owns one
  spine and byte range; store wording must not imply cross-chapter selection.
- On the API 34 ARM64 iQOO, Port 10 passes focused selection 5/5 in 9.667
  seconds, the correctly filtered ordinary matrix 95/95 in 204.316 seconds,
  external confirmed-force-stop restart, and the final correctly configured
  130%-system-text/reduced-motion matrix 32/32 in 34.958 seconds. An earlier
  attempt used an unused global font key and is not large-text evidence; only
  the final system-namespace run counts. Single-page long press, handles, Copy,
  Back, and audible TalkBack Select/Copy/Clear actions pass hands-on review.
  This remains accepted predecessor evidence for the merged same-page baseline;
  the follow-up evidence above closes its physical continuation gate.
- The Port 10 device began with stored animation scales at `1.0` while vivo
  Recents animations were still behaviorally absent. Republishing `1.0` did not
  repair the stale runtime; restarting Launcher did. Reduced-motion cleanup
  restored all three scales explicitly to `1.0`, restarted Launcher, and the
  final probes rendered 47 Launcher and 74 SystemUI frames. TalkBack,
  accessibility, notification permission, rotation, audio, system/global font,
  animation, and secure-setting state were restored to the captured baseline;
  the crash buffer is empty. After the follow-up and TalkBack review, all 26
  original app files (4,751,505 bytes)
  restored byte-exact with archive SHA-256
  `1EF189A765D02321E1A9DC2203CF69B4F90111A9369D0AA6D585D0592DB46DBE`
  and manifest SHA-256
  `94A15EE1CCAAC59833EB0647887A55F3FF441FBD8DF4958B24537E8E6EB59B74`.
- On the API 34 ARM64 iQOO, Port 9 passes focused search 5/5 in 7.787 seconds,
  the correctly selected ordinary matrix 90/90 in 194.467 seconds of
  instrumentation time, external confirmed-force-stop restart, and the
  130%-text/reduced-motion matrix 27/27 in 27.219 seconds. Font and all three
  animation scales were restored explicitly to their original `1.0` values;
  74 rendered vivo SystemUI frames then verified live animation behavior. The
  crash buffer is empty, and all 26 original app files (4,751,505 bytes) were
  restored byte-exact.
- Representative-book physical review searched *Gardens of the Moon* for
  `Paran`, reported 630 matches with the first 64 disclosed, jumped from 0% to
  3%, advanced to the next result, and kept active/inactive page emphasis
  distinct. TalkBack 17.0.1 physical touch exploration then passed for Find
  controls, status, current/ordinary results, activation, Next, Back, spoken
  open/close state, and focus return. Accessibility, notification-permission,
  rotation, audio, font, animation, and 8vo data state restored to the captured
  baseline. Broader-book coverage and the remaining whole-app hands-on launch
  review remain gates.

## Release qualification plan

Qualification is ordered and stops on the first failed gate:

1. Freeze the reviewed commit and exact Ground0, Reader0, UI0, and Readerview0
   pins. Require clean participating worktrees and pass dependency and
   architecture guards without using `LECTERN0_ZERO_FOUNDATION_DIR`.
2. Qualify the local annotation store and provider-neutral sync merge engine
   before network integration: atomic failure/rollback, bounds, migration,
   corruption recovery, lifecycle/restart, reflow anchors, concurrent-device
   fixtures, tombstones, idempotence, export/restore, and deterministic hashes.
3. Qualify Google Drive against test accounts and at least two devices: explicit
   authorization, least-privilege scopes, first sync, offline convergence,
   conflicts, interruption/retry, quota, revocation, account switching,
   reinstall/reconnect, cloud deletion, no duplicate EPUBs, and no blocked local
   reading. No private book content may enter retained evidence.
4. Run source/license review, Android lint and release-vital lint, and verify the
   final merged manifest, permissions, exported components, package ID, version,
   launcher assets, OAuth scopes/client identity, notices, privacy/Data safety
   copy, Drive data flows, and absence of signing or OAuth secrets.
5. Build the signed release AAB through the documented release path. Verify its
   signature, validate it with the matching bundle tool, retain hashes and native
   symbols, and prove 64-bit plus 16-KB ELF/package alignment on generated APKs.
6. Generate Play-equivalent split APKs. On a clean API 26 emulator, first pass a
   bounded install/launch, Library-to-reader-to-Navigation, system Back,
   rotation/recreation, process-restart, and accessibility-node smoke. If that
   declared minimum cannot be qualified, raise `minSdk` deliberately before
   release. Then install the same candidate on the API 36 emulator and run the
   focused deterministic/polish matrix, complete ordinary matrix, lifecycle/
   restart, large-text/reduced-motion, accessibility (including attached live-
   region event delivery), explicit UI0 role-to-face metric comparison,
   transition, crash/ANR, and clean-install/upgrade-state checks.
7. Upload the exact signed AAB to internal testing only after the local release
   gates pass. Inspect App Bundle Explorer output, device compatibility, policy
   warnings, native-symbol association, and the Play pre-launch report. Install
   Play-generated artifacts rather than treating the locally assembled debug APK
   as release evidence.
8. Request the physical device only after emulator and Play-generated-artifact
   gates pass. Coordinate any device-wide animation changes. Restore window,
   transition, and animator duration scales explicitly to `1.0`, then verify
   visible vivo SystemUI behavior as well as stored values.
9. Run the physical focused and ordinary matrices, confirmed-force-stop restart,
   hands-on TalkBack/touch/transition review, crash/ANR review, and byte-exact
   app-data restoration. Record the final AAB, symbol, manifest, dependency, and
   validation hashes.
10. Complete a bounded internal/closed-track soak, triage Play Vitals and tester
   feedback, confirm rollback and support procedures, then request explicit user
   approval for any production submission or rollout.

## Console and account prerequisites requiring confirmation

The repository cannot establish whether the following are complete:

- Play developer identity verification, account ownership, required agreements,
  payments/profile setup, and permission to manage Play App Signing;
- a Google Cloud project with the Drive API enabled; completed OAuth branding,
  homepage, privacy-policy and terms URLs; Android OAuth clients for debug and
  Play app-signing SHA-1 identities; required consent-screen test users or
  publication/verification; and owner approval for those external mutations;
- availability and registration of `ro.devze.octavo`. Play package registration
  becomes mandatory on 2026-09-30 under the current
  [package-registration requirement](https://support.google.com/googleplay/android-developer/answer/16984799?hl=en-EN);
- whether this is a personal developer account created after 2023-11-13. If so,
  production access requires at least 12 closed-test users opted in continuously
  for 14 days before applying, as documented in
  [Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-EN);
- production-access eligibility, test-track availability, app-signing enrollment,
  upload-certificate registration, and managed-publishing configuration;
- the privacy-policy/support/terms URLs, contact email, listing and OAuth assets,
  translations, category/tags, target audience, content rating, Drive-enabled
  Data safety, ads/app-access answers, pricing, countries/regions, and release
  notes; and
- Play Console policy, device-catalog, pre-launch-report, and App Bundle Explorer
  findings for the exact release AAB.

These items are launch gates, not assumptions. They require explicit review in
the relevant owner account. This repository change must not create signing
material, upload artifacts, change Console state, push, merge, or publish.
