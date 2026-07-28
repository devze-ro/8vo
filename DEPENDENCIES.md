# Source dependencies

8vo compiles four first-party repositories directly from source. The metadata
under `vendor/` is authoritative: builds must use the exact clean revisions
listed below and must not silently advance them.

| Repository | Revision | Version / API | Metadata |
| --- | --- | --- | --- |
| [reader0](https://github.com/devze-ro/reader0) | `98a6a2ba5a4946971b9c088781cf3728aeb16b1a` | 0.5.0-dev / API 5 | `vendor/reader0_dependency/` |
| [readerview0](https://github.com/devze-ro/readerview0) | `f97f9d38cf857c2cff1f90357cf5d2e5cf40dc03` | 0.3.0-dev / API 3 | `vendor/readerview0_dependency/` |
| [ui0](https://github.com/devze-ro/ui0) | `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec` | 0.1.0-dev / API 91 | `vendor/ui0_dependency/` |
| [ground0](https://github.com/devze-ro/ground0) | `fa7f680f933c23d84f9b74e15887a3b8bb78d2f9` | 0.4.3-dev / Presentation Engine API 1 | `vendor/ground0_dependency/` |

## Bootstrap

From the 8vo repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bootstrap_dependencies.ps1
```

The script reads the commit files above, clones the public repositories into
ignored `local/dependencies/` directories, checks out the exact revisions in
detached-HEAD mode, and verifies every commit and working tree. It is safe to
run again: clean exact checkouts are reused, while a dirty checkout stops the
bootstrap instead of overwriting local work.

The build and dependency guard automatically use these local checkouts.

## Manual checkouts

The build accepts explicit `OCTAVO_*_DIR` environment variables and otherwise
looks first in `local/dependencies/` and then for sibling checkouts.
`scripts/check_dependencies.ps1` verifies each checkout's revision, version,
API, and cleanliness before compilation.

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

- reader0: miniz and Noto Serif
- ui0: Lucide Icons and Feather-derived icons
- ground0: Unicode data

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for exact provenance and
the license texts that must accompany binary distributions.
