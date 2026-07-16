# lectern0

`lectern0` is the standalone EPUB-first reader application in the dev0 family.
It is a concrete Windows host for reader0, UI0, and zero_foundation. It has no
re10 source dependency and does not define a generic document-provider layer.

The integrated host provides a native window, an EPUB file picker and
command-line open, UI0-composed Open/Previous/Next chrome, canonical-frame
rendering, concrete reader0 API 3 page and semantic navigation, resize
repagination, and a small atomic last-location record. Slice 4B proves TOC
fragment and Find-result navigation headlessly; their shared UI0 chrome remains
reserved for readerview0. Slice 5A adds a lectern0-owned document/resource image
cache over zero_foundation's caller-owned decoder, so EPUB cover and inline PNG,
JPEG, BMP, and first-frame GIF resources render as decoded pixels. Unsupported,
missing, oversized, corrupt, or cache-full resources retain visible alt-text
fallbacks.

The Presentation Engine API 1 adoption routes canonical EPUB row and image-box
vertical geometry through zero_foundation's callback-free block-flow builder.
Lectern0 still resolves reader/host metrics, owns caller storage, draws the
returned records, and retains every cache, persistence, and product decision.

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
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_lectern0_image_smoke.ps1
```

No remote is configured for this repository.
