# lectern0

`lectern0` is the standalone EPUB-first reader application in the dev0 family.
It is a concrete Windows host for reader0, readerview0, UI0, and
zero_foundation. It has no re10 source dependency and does not define a generic
document-provider layer.

The integrated host provides a native window, an EPUB file picker and
command-line open, canonical-frame rendering, concrete reader0 API 3 page and
semantic navigation, resize repagination, and host-owned position, settings,
bookmark, highlight, and note persistence. Reader View API 1 composes the
responsive UI0 chrome shared with re10: top toolbar, page gutters, progress
seek, history, TOC/Find, font family/size/spacing/theme settings, bookmarks,
annotations, selection tools, note editing, fullscreen, and distraction-free
reading. Lectern0 projects concrete host state into that package and executes
its bounded action records; readerview0 does not own the EPUB engine or durable
records.

Slice 5A's lectern0-owned document/resource image cache remains over
zero_foundation's caller-owned decoder, so EPUB cover and inline PNG, JPEG, BMP,
and first-frame GIF resources render as decoded pixels. Unsupported, missing,
oversized, corrupt, or cache-full resources retain visible alt-text fallbacks.

The Presentation Engine API 1 adoption routes canonical EPUB row and image-box
vertical geometry through zero_foundation's callback-free block-flow builder.
Lectern0 still resolves reader/host metrics, owns caller storage, draws the
returned records, and retains every cache, persistence, and product decision.
Its native MSAA adapter exposes readerview0's portable semantic records while
keeping platform objects, screen-reader events, and action execution in the
application.

## Build and validate

From a linked worktree, point the dependency variables at exact clean
checkouts:

```powershell
$env:LECTERN0_READER0_DIR = 'C:\path\to\reader0-api3-worktree'
$env:LECTERN0_UI0_DIR = 'C:\path\to\ui0'
$env:LECTERN0_READERVIEW0_DIR = 'C:\path\to\readerview0-api1-worktree'
$env:LECTERN0_ZERO_FOUNDATION_DIR = 'C:\path\to\zero_foundation'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\require_dependencies_current.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\audit_architecture.ps1
cmd /c build\win32_build.bat no_run
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_lectern0_host_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_lectern0_reader_view_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_lectern0_accessibility_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_lectern0_visual_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_lectern0_image_smoke.ps1
```

No remote is configured for this repository.
