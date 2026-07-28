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
