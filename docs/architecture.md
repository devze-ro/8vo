# lectern0 architecture

## Concrete boundary

Lectern0 is a Windows EPUB application. It owns one caller-allocated
`EpubReader`, one bounded `EpubReaderFrameStorage`, the current layout key and
configuration, a Win32 window/backbuffer, readerview0/UI0 frame storage,
zero_foundation draw/render state, and versioned host persistence records.

Reader0 owns the EPUB document engine, source layout, typography, pagination,
page transitions, semantic navigation, search/selection state, and canonical
frame. Lectern0 passes its viewport-derived layout values directly to reader0
API 3. No reader state is mirrored in application storage. Its narrow semantic
adapters capture the resulting canonical frame, set host status, and persist
the resulting reader-owned location.

Readerview0 API 2 owns only the proven-common UI0 composition: the responsive
top toolbar and overflow, page gutters and progress geometry, TOC/Find and
annotations/bookmarks panel shells, settings and row-action popups, selection
tools, note-editor interaction state, focus order, portable semantic records,
bounded returned actions, and product-neutral page/content rectangles. Its
projection/action/geometry API does not name reader0 types and has no reader0
or direct zero_foundation dependency.

Lectern0 owns the projected TOC/search/selection meaning, settings choices,
stable bookmark/highlight/note IDs, 128-record bookmark and highlight
capacities, native commands, and every mutation. It persists the current
location, reading settings, and per-book annotations in separate versioned
host records. Projection strings and rows are borrowed for one build;
`ReaderViewState` and `ReaderViewFrameStorage` are caller-owned. The application
never treats an emitted action as a completed persistent mutation.

UI0 owns generic signal, control, token, layout, draw, text-edit records, and
the six shared reader-chrome profiles. A narrow lectern0 adapter converts every
UI0 draw operation into clipped zero_foundation draw commands and binds semantic
typography roles to host font metrics. UI0 does not render EPUB content and is
not a reader0 dependency.

Zero_foundation owns arenas, file/atomic-write facilities, font providers,
Presentation Engine API 1 block-flow geometry, draw commands, software
rendering, the Win32 DIB graphics seam, and the caller-owned encoded-image
decoder with its WIC backend. Lectern0 owns HWND/WndProc, DPI/input mapping,
the native EPUB picker, and presentation policy. Its bounded adapter resolves
reader canonical rows, host pixel metrics, and image boxes into caller-owned
block-flow specs, then draws only the returned row/media rectangles.

The presentation adapter also registers each reader-resolved font provider
with the host render cache, splits canonical rows at grapheme/word boundaries
to respect the bounded draw-command text capacity, advances chunks with
reader0's exact measurement path, and marks shaped text commands explicitly.
This keeps pagination measurement and rasterization on the same provider
without moving a renderer or font cache into reader0.

Lectern0 also owns one bounded 64-entry image cache keyed by concrete reader0
document/resource identity. It fetches encoded resource bytes through reader0,
passes them to the explicit zero_foundation decoder, maps decoder failures into
canonical frame image status, and attaches arena-owned BGRA8 views to the
current frame. The host retains cache capacity/full policy, failed-attempt
retention, media-type exclusions, cache reset on successful document open, and
final aspect-fit sprite/fallback presentation. Neither reader0 nor
zero_foundation owns product cache policy.

Vertical placement follows the canonical row metadata through Presentation
Engine API 1: block top margins apply only to `line_row == 0`, while resolved
line/image height and bottom margins advance each row. The engine owns only
checked stacking and row-relative media rectangles. Lectern0 retains the
96-row/16-image storage chosen to match reader0's bounded frame and fails if
every canonical row cannot be submitted inside the reader body.

The `--render-smoke` path drives that same UI0/draw/render composition into a
caller-owned offscreen buffer, verifies complete canonical style-row coverage,
records a deterministic Presentation Engine geometry hash, and writes BMP
evidence. The wrapper repeats the render in a fresh process and requires equal
geometry, pixel, and file hashes. It does not introduce a second layout or
presentation implementation.

## Viewport, responsive layout, focus, and accessibility

`ReaderViewLayout.viewport_rect` is the host viewport boundary. Lectern0 passes
it to `reader_view_resolve_content_geometry()` and uses the returned
`content_rect` as the only reader0 pagination and canonical-frame rectangle;
the returned `page_surface_rect` owns the surrounding page paint. Shared
gutters and docked panels reduce the viewport; overlays never move EPUB
rendering behind an unrelated application layout. A layout-affecting shared
state change recomputes both contracts and repaginates at the current
reader-owned location.

Full toolbar composition starts at 1024 pixels, compact composition and
overflow run from 720 through 1023, and narrower windows use the minimal
toolbar. Both panels can dock from 1180 pixels; one docks between 840 and 1179;
narrower panels overlay. A 38-pixel trailing toolbar reservation belongs to
lectern0 and contains its exit control. Distraction-free behavior remains
dormant rather than becoming mandatory shared chrome, while the host
independently performs native fullscreen window transitions.

Readerview0 owns stable portable focus IDs, popup/modal containment, roles,
states, and pointer/keyboard/accessibility convergence. Lectern0 translates
Win32 input and implements a host-owned MSAA `IAccessible` object over the
current semantic records. `WM_GETOBJECT`, native object lifetime, screen-reader
events, coordinate translation, and execution of returned reader actions stay
in lectern0.

## Current exclusions

No PDF backend, generic document interface, library database, shared
persistence, renderer, decoded-image cache, or native accessibility adapter is
moved into readerview0. Features absent from the current re10 EPUB reader—such
as later Kindle-gap reading controls—remain deferred until re10 and lectern0
share and stabilize this Stage 1 surface. Unsupported, missing, oversized,
corrupt, and cache-full image rows render bounded alt-text fallbacks.
Simple-grid adoption waits for a second host that presents table cells as
independent geometry; lectern0 does not invent a table UI merely to exercise
the foundation API.
