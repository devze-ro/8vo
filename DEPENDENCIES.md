# Source dependencies

8vo compiles four first-party repositories directly from source and links the
Reader0-owned PDF-only MuPDF core on Win32. The metadata under `vendor/` is
authoritative: builds must use the exact clean root/submodule revisions listed
below and must not silently advance them.

| Repository | Revision | Version / API | Metadata |
| --- | --- | --- | --- |
| [reader0](https://github.com/devze-ro/reader0) | `f17b80eab591150f92fcd34ad21acfcdf16c7bb4` | 0.12.0-dev / API 12 | `vendor/reader0_dependency/` |
| [readerview0](https://github.com/devze-ro/readerview0) | `a1b3f7a9b236cb9ac6cbfe68c293a1345267cc88` | 0.4.0-dev / API 4 | `vendor/readerview0_dependency/` |
| [ui0](https://github.com/devze-ro/ui0) | `b1cf8e4fbe7e06b9799e251665bbe491ae4c22b5` | 0.1.0-dev / API 91 | `vendor/ui0_dependency/` |
| [ground0](https://github.com/devze-ro/ground0) | `7abd8d136cc8cab2bd0d27f76c5bd0aa086901d5` | 0.4.6-dev / Presentation Engine API 1 | `vendor/ground0_dependency/` |
| [MuPDF](https://github.com/ArtifexSoftware/mupdf) | `fe374accd98a43174a328fa7980d7675e06d5b0d` plus exact `SUBMODULES` closure | 1.28.2 | `vendor/mupdf_dependency/` |

## Bootstrap

From the 8vo repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bootstrap_dependencies.ps1
```

The script reads the commit files above, clones the public repositories into
ignored `local/dependencies/` directories, checks out the exact revisions in
detached-HEAD mode, recursively initializes MuPDF's pinned submodules, and
verifies every commit and working tree. It is safe to run again: clean exact
checkouts are reused, while a dirty checkout stops the bootstrap instead of
overwriting local work.

The build and dependency guard automatically use these local checkouts.

## Manual checkouts

The build accepts explicit `OCTAVO_*_DIR` environment variables (including
`OCTAVO_MUPDF_DIR`) and otherwise
looks first in `local/dependencies/` and then for sibling checkouts.
`scripts/check_dependencies.ps1` verifies each checkout's revision, version,
API, and cleanliness before compilation. `-Target Win32Pdf` additionally
requires the exact clean MuPDF checkout and metadata closure. `-Target
AndroidEpub` deliberately does not resolve or require MuPDF because the Android
native target consumes only Reader0's EPUB surface.

## Licensing

All first-party source code and documentation in these supporting repositories
is licensed by its copyright holder under the Mozilla Public License 2.0. The
complete license text is retained in this repository's
[`LICENSE`](LICENSE).

The pinned revisions above predate the commits that added repository-level
`LICENSE` files to the supporting repositories. Consequently, a detached
checkout at an exact pin may not show its own root license file even though
the first-party files at that revision are licensed under MPL-2.0. The current
default branch of each supporting repository records the same licensing
declaration.

Third-party material flowing through these dependencies retains its original
license:

- MuPDF: AGPL-3.0 or a separate Artifex commercial license; this governs the
  linked PDF-enabled Win32 binary and its corresponding-source distribution
- reader0: miniz and Noto Serif
- ui0: Lucide Icons and Feather-derived icons
- ground0: Unicode data

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for exact provenance and
the license texts that must accompany binary distributions.
