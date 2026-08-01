# Validation scripts

## Dependency bootstrap

`bootstrap_dependencies.ps1` clones the four public source dependencies into
ignored `local/dependencies/` directories at the exact revisions recorded
under `vendor/`. It verifies repository identity, commit identity, and
cleanliness, configures the existing `OCTAVO_*_DIR` variables for its
verification process, and runs the strict dependency guard.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bootstrap_dependencies.ps1
```

## Android fixtures

For historical Port 4 validation,
`build_android_fixture.ps1` reproducibly writes the Port 4 EPUB fixture in
fixed ZIP-entry order with fixed timestamps and no compression:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts\build_android_fixture.ps1
```

The script prints the exact output length and SHA-256 documented in
`docs/android_port4.md`.

`build_android_port5_fixtures.ps1` reproducibly writes Port 5's default fixture
and the smaller, visibly distinct selected-book fixture used by import/resume
instrumentation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts\build_android_port5_fixtures.ps1
```

Both outputs use fixed entry order, fixed timestamps, and no compression. The
script prints the exact lengths and SHA-256 values documented in
`docs/android_port5.md`.

`build_android_port6_fixtures.ps1` reproducibly writes Port 6's built-in
sample plus the visibly distinct Alpha and Beta imported-book fixtures used by
catalog, deduplication, exact-resume, migration, and removal instrumentation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts\build_android_port6_fixtures.ps1
```

All three outputs use fixed entry order, fixed timestamps, and no compression.
The script prints the exact lengths and SHA-256 values documented in
`docs/android_port6.md`.

## Public smoke suite

`run_public_smoke.ps1` is the reproducible public validation entry point. It
builds 8vo against the exact dependency revisions and runs seven self-contained
smoke tests. The tests generate their own EPUB, image, migration, and state
fixtures under ignored `local/` directories; they do not require private books
or machine-specific paths.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run_public_smoke.ps1
```

Pass `-SkipBuild` only when the current `build/win32/8vo.exe` was produced from
the same source and exact dependency checkouts.

## Exact-book regression scripts

The remaining scripts whose `BookPath` parameter is mandatory preserve
historical regression contracts for a specific external EPUB. They verify its
SHA-256 (and, where applicable, size) before use. The fixture is not stored in
this repository, so callers must provide it explicitly:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts\win32_octavo_library_smoke.ps1 `
  -BookPath C:\path\to\the\expected-fixture.epub
```

These exact-book regressions are additional maintainer evidence, not a
prerequisite for a stranger to build or validate the public source tree.
Outputs default to ignored paths under `local/validation/`.
