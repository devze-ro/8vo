# lectern0

`lectern0` is the standalone EPUB-first reader application in the dev0 family.
It is a concrete Windows host for reader0, UI0, and zero_foundation. It has no
re10 source dependency and does not define a generic document-provider layer.

The integrated host provides a native window, an EPUB file picker and
command-line open, UI0-composed Open/Previous/Next chrome, canonical-frame
rendering, concrete reader0 API 3 page and semantic navigation, resize
repagination, and a small atomic last-location record. Slice 4B proves TOC
fragment and Find-result navigation headlessly; their shared UI0 chrome remains
reserved for readerview0. Images use explicit alt-text placeholders; decoded
image caching remains a later host slice.

## Build and validate

From a linked worktree, point the dependency variables at exact clean
checkouts:

```powershell
$env:LECTERN0_READER0_DIR = 'C:\path\to\reader0-api3-worktree'
$env:LECTERN0_UI0_DIR = 'C:\path\to\ui0'
$env:ZERO_FOUNDATION_DIR = 'C:\path\to\zero_foundation'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\require_dependencies_current.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\audit_architecture.ps1
cmd /c build\win32_build.bat no_run
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_lectern0_host_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_lectern0_visual_smoke.ps1
```

No remote is configured for this repository.
