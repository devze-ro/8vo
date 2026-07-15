# lectern0 architecture

## Concrete boundary

Lectern0 is a Windows EPUB application. It owns one caller-allocated
`EpubReader`, one bounded `EpubReaderFrameStorage`, the current layout key and
configuration, a Win32 window/backbuffer, UI0 frame contexts, zero_foundation
draw/render state, and a versioned last-location record.

Reader0 owns the EPUB document engine, source layout, typography, pagination,
page transitions, semantic navigation, search/selection state, and canonical
frame. Lectern0 passes its viewport-derived layout values directly to reader0
API 3. No reader state is mirrored in application storage. Its narrow semantic
adapters capture the resulting canonical frame, set host status, and persist
the resulting reader-owned location.

UI0 owns generic toolbar layout, signal, control, token, and draw records. A
narrow lectern0 adapter converts those records into zero_foundation draw
commands. UI0 does not render EPUB content and is not a reader0 dependency.

Zero_foundation owns arenas, file/atomic-write facilities, font providers,
draw commands, software rendering, the Win32 DIB graphics seam, and the
caller-owned encoded-image decoder with its WIC backend. Lectern0 owns
HWND/WndProc, DPI/input mapping, the native EPUB picker, and presentation.
The presentation adapter registers each reader-resolved font provider with the
host render cache, splits canonical rows at grapheme/word boundaries to respect
the bounded draw-command text capacity, advances chunks with reader0's exact
measurement path, and marks shaped text commands explicitly. This keeps
pagination measurement and rasterization on the same provider without moving a
renderer or font cache into reader0.

Lectern0 also owns one bounded 64-entry image cache keyed by concrete reader0
document/resource identity. It fetches encoded resource bytes through reader0,
passes them to the explicit zero_foundation decoder, maps decoder failures into
canonical frame image status, and attaches arena-owned BGRA8 views to the
current frame. The host retains cache capacity/full policy, failed-attempt
retention, media-type exclusions, cache reset on successful document open, and
final aspect-fit sprite/fallback presentation. Neither reader0 nor
zero_foundation owns product cache policy.

Vertical placement follows the canonical row metadata: block top margins apply
only to `line_row == 0`, while line height and bottom margins advance each row.
The evidence path fails if every canonical row cannot be submitted inside the
reader body.

The `--render-smoke` path drives that same UI0/draw/render composition into a
caller-owned offscreen buffer, verifies complete canonical style-row coverage,
and writes BMP evidence. The wrapper repeats the render in a fresh process and
requires equal pixel and file hashes. It does not introduce a second layout or
presentation implementation.

## Current exclusions

No PDF backend, generic document interface, library database, search/TOC UI,
annotations, shared reader view/chrome, theme selector, or accessibility
redesign is included. Slice 4B proves semantic TOC and Find transitions without
pre-implementing their UI. Unsupported, missing, oversized, corrupt, and
cache-full image rows render bounded alt-text fallbacks.
