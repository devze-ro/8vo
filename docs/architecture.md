# lectern0 architecture

## Concrete boundary

Lectern0 is a Windows EPUB application. It owns one caller-allocated
`EpubReader`, one bounded `EpubReaderFrameStorage`, the current layout key and
configuration, a Win32 window/backbuffer, readerview0/UI0 frame storage,
zero_foundation draw/render state, and versioned host persistence records.

The application shell is library-first: launch and Close Book resolve to a
Lectern0-owned Library surface, while an open EPUB resolves to Readerview0's
Reader surface. The library catalog is a bounded 512-entry, versioned binary
record written through zero_foundation's atomic-file mechanism. Lectern0 owns
normalized absolute paths, local entry IDs, file size/modified-time
fingerprints, MRU ordering, import/remove/locate behavior, canonical-progress
persistence, native picker use, responsive cover cards, and its bounded
48-entry/24 MiB thumbnail cache. A missing source path is derived at runtime;
it does not erase metadata or the cached cover, and Remove deletes only catalog
and thumbnail state. Existing `state.v1` position data seeds the catalog once
when no catalog exists.

Reader0 supplies EPUB title, author, cover-resource access, and canonical
spine/byte locations. zero_foundation supplies decoding, drawing, and atomic
file replacement. UI0 and Readerview0 gain no library API in this slice: the
grid and cards are concrete host composition, while Readerview0 remains the
open-book experience. Re10 can later compose a Books destination using the
same Reader0 metadata and location concepts while retaining its own shell,
catalog policy, and persistence. No shared library package is extracted until
two host integrations establish a real common contract.

Each catalog entry reserves a bounded, algorithm-tagged digest field, initially
`None`. This preserves room for a later explicit local-content fingerprint,
but the local entry ID, normalized path, and file timestamps are deliberately
not treated as cross-device identity. Kindle-like sync for Lectern0 and Re10
will require a separately designed account, remote work identity, conflict,
privacy, and reconciliation layer; none of that behavior is implemented here.

Reader0 owns the EPUB document engine, source layout, typography, pagination,
page transitions, semantic navigation, search/selection state, and canonical
frame. Lectern0 passes its viewport-derived layout values directly to reader0
API 4. No reader state is mirrored in application storage. Its narrow semantic
adapters capture the resulting canonical frame, set host status, and persist
the resulting reader-owned location.

Readerview0 API 3 owns only the proven-common UI0 composition: the accepted
fixed-slot top toolbar, page gutters and progress geometry, TOC/Find and
annotations/bookmarks panel shells, settings and row-action popups, selection
tools, note-editor interaction state, focus order, portable semantic records,
bounded returned actions, and product-neutral page/content rectangles. Its
projection/action/geometry API does not name reader0 types and has no reader0
or direct zero_foundation dependency.

Reader View text bindings carry one finite portable style identity. Lectern0
maps `ChromeTitle` through the accepted system-UI box raster at scale 2 and
`MenuItem`, `ChromeMetadata`, and `Default` at scale 1. `NoteEditor` draft rows
instead carry explicit Body typography metadata and use zero_foundation's
`TextEngineEditableRow` shaped painter at its 18-pixel height; the visible
caret x is derived from that same shaped row. The TextArea's 25-pixel line
advance remains layout geometry, not a font size. Find-result parent semantics
retain Reader0's full excerpt. When its first fitted line would omit a valid
match, the child text binding can borrow Readerview0's one-line natural-word
window and remapped match bytes; Lectern0 still performs the concrete
full-string measurement, rasterization, highlight paint, and clipping. The
record conveys semantic presentation only: no font pointer, callback, provider
table, or allocation crosses the boundary.

Lectern0 owns the projected TOC/search/selection meaning, settings choices,
stable bookmark/highlight/note IDs, 128-record bookmark and highlight
capacities, native commands, and every mutation. It persists the current
location, reading settings, and per-book annotations in separate versioned
host records. Projection strings and rows are borrowed for one build;
`ReaderViewState` and `ReaderViewFrameStorage` are caller-owned. The application
never treats an emitted action as a completed persistent mutation.

TOC identities are the concrete reader0 `nav_index + 1`, never a dense visible
row index. Find text editing lives in caller-owned Reader View state; a
non-empty edit does not execute reader0 search or navigation. Commit executes
the borrowed query once, Clear immediately clears the committed engine search,
and result keys map to their exact bounded reader0 match indices.

Each projected annotation row carries its source heading, primary content, and
the accepted `<kind> - re10 loc <N>` metadata. Bookmark and Highlight primary
content is the captured excerpt; Note primary content is its persistent note
body, matching the frozen reader. Lectern0 derives `N` from the concrete
reader0 extracted-text spine sizes as
`1 + global_text_byte_offset / 128`. The wording is a compatibility label, not
a re10 source dependency. Persistent note text stays in lectern0; Reader View
borrows it for that row frame and returns only a bounded edit-note action.
Projection-only stable keys distinguish the Highlight row from its attached
Note while routing both back to the same persistent host record. The version-3
annotation schema stores an explicit `is_highlight` identity; version-1 and
version-2 records migrate with that identity active. Deleting a Highlight with
an attached Note clears its Highlight identity and star while retaining the
Note-only record. Deleting a Highlight without a Note removes the record;
deleting a Note clears it while a Highlight remains and removes a Note-only
record.
Bookmarks always project `ReaderViewRow_Starred`: the bookmark is itself the
frozen starred state, so its inline star removes that record through the
canonical host persistence helper exactly once.
The color rail is an explicit resolved `rail_color` while `color_key` retains
the host color identity, and
`ReaderViewRow_AttachedToPrevious` is set only when that Highlight is the
immediately preceding visible row.
Before projection, a fixed 384-entry host array deterministically orders the
maximum 128 bookmarks plus 128 Highlight/Note pairs by spine, byte, kind, and
stable ID. This is transient presentation ordering: persisted arrays and IDs
are not rewritten, and action keys map back to their original records.

Find-row bindings preserve reader0's byte-accurate match range. Lectern0 uses
actual system-UI font measurement at scale 1 to fill a caller-owned 256-entry
codepoint-advance table consumed by readerview0 as portable values. Basic Latin
is pinned; the current edit/transfer text, placeholder, committed query, and
history have explicit priorities; stale dynamic values are evicted before
lower-priority same-frame values; and generation wrap ages all dynamic entries.
The same measurement path fits the accepted single line, positions the
reader-highlight background from the measured prefix and match, then draws the
complete excerpt in primary text. No fixed-width estimate, split/recolored text
run, callback, provider table, or allocation crosses the package boundary.

While the Note editor is open, Lectern0 rebuilds a separate caller-owned
256-entry values table from the current draft, same-frame typed text, transfer
text, and localized empty-editor placeholder. Each unique scalar is measured
with the same system-UI face at 18 pixels used by the draw adapter; the portable
record also carries the measured fallback advance and 25-pixel line advance.
Readerview0 borrows those values for one build to wrap, hit-test, select, and
place the caret. Missing scalars use the explicit fallback when the bounded
table is full. No provider, callback, allocation, or retained borrowed pointer
crosses the package boundary.

An Annotations Note action binds a fixed host tuple of document, highlight,
spine, and byte range to the shared draft. It neither navigates nor changes the
reader selection or history. The projection revalidates that tuple each frame;
stale revisions cannot Save or Delete. New-selection Note creation writes its
provisional Highlight and Note in one host transaction. Save, Delete, and every
Bookmark/Highlight/Note star or row mutation change the live state only when
the atomic annotation write succeeds; failure restores the exact record array,
counts, next ID, stars, and revision. A failed editor mutation leaves its draft
open. After a successful host mutation, lectern0 calls
`reader_view_close_note_editor` and then releases the editor's retained target.
Annotation-origin Save/Delete preserve any unrelated concrete document
selection, selected text, anchor, location, and history; selection-origin
completion releases its owned selection. Explicit Cancel applies the same
origin-sensitive host cleanup without a persistent mutation.

UI0 owns generic signal, control, token, layout, draw, text-edit records, and
the six shared reader-chrome profiles. A narrow lectern0 adapter converts every
UI0 draw operation into clipped zero_foundation draw commands and binds semantic
typography roles to host font metrics. The generic `Filter` icon intent is
adapted only at this Reader View boundary to the frozen re10 SlidersVertical
24 by 24 geometry; an exact 18 by 18 raster hash prevents a portable-sprite
substitution from silently changing the accepted Annotations toolbar. All
other shared icons use UI0's canonical caller-rasterized pixels. UI0 does not
render EPUB content and is not a reader0 dependency.

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

Image-only canonical rows are a distinct host-presentation case. Reader0 owns
their classification and `visual_units`; Lectern0 allocates the full content
width and exactly `visual_units * line_height` pixels of vertical media space,
then aspect-fits the decoded image inside that box. The 18-unit fallback is
used only when the canonical row omits a value, matching the frozen Re10
adapter. Loaded image-only media has no synthetic card background. Lectern0
selects explicit nearest sampling for this path to preserve the frozen Re10
benchmark; zero_foundation owns the sampling mechanism, not that product
policy. Publisher in-flow images retain their separately bounded box policy.

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

`ReaderViewLayout.viewport_rect`, `page_surface_rect`, and `content_rect` are
one atomic host boundary. Lectern0 uses the returned `content_rect` as the only
reader0 pagination and canonical-frame rectangle; `page_surface_rect` owns the
surrounding page paint. Shared gutters and panels are resolved in that same
state snapshot. A layout-affecting shared state change recomputes the contract
and repaginates at the current reader-owned location.

The accepted toolbar is a fixed right-aligned row of eleven shared 30 by 28 px
icon slots followed by one host slot, with a 56 px top chrome and 38 px footer.
A 38-pixel trailing reservation belongs to lectern0 and contains its Close Book
control. Lectern0 retains the action and return-to-Library transition, while UI0
paints the control as a nonquiet `IconButton` with its canonical Close icon.
Bounds too small for the fixed reference contract fail closed.
Distraction-free behavior remains dormant rather than becoming mandatory
shared chrome, while the host independently performs native fullscreen window
transitions.

Readerview0 owns stable portable focus IDs, popup/modal containment, roles,
states, and pointer/keyboard/accessibility convergence. Lectern0 translates
Win32 input and implements a host-owned MSAA `IAccessible` object over the
current semantic records plus one explicit bounded host record for Close Book.
The host inserts that native action between shared Find and Fullscreen and
preserves the frozen reference's focusable disabled Previous gutter seam; all
other shared order remains driven by Reader View semantic identities. Close Book
pointer activation requires press origin and release inside the host slot and
is cancelled on leave or capture loss. `WM_GETOBJECT`, native object lifetime,
screen-reader events, coordinate translation, Close Book invocation, and execution
of returned reader actions stay in lectern0.
The host Close Book path retains the focused IconButton's fill, border,
text, outer focus ring, and explicit Close icon. It clips those commands to the
38-pixel host reservation rather than the smaller control rect; startup evidence
requires both the adapted Close sprite and exact expanded rounded focus stroke.
Native MSAA `NEXT` and `PREVIOUS` navigation scan for enabled focusable logical
children, so disabled Back/Forward controls are skipped without changing their
semantic records. This preserves the required Find, host Close Book, Fullscreen
adjacency while shared keyboard traversal retains the frozen disabled-Previous
gutter focus stop and rejects its action.
The host order never forms a closed toolbar island: with Contents, Find, or
Annotations open, forward traversal after Progress and reverse traversal before
Contents delegate to Reader View's published panel tail. A bounded regression
checks both boundaries for all three panels, visible focus, and zero actions.
The draw bridge uses UI0's 32 by 32 public raster bound and passes its caller-
owned 32-pixel stride to zero_foundation sprites. Focused host regressions hash
the frozen 18 by 32 page-caret pixels and exercise both keyboard and pointer
gutter navigation; no host line-art fallback is permitted.

## Current exclusions

No PDF backend, generic document interface, library database, cloud sync,
shared library framework, shared
persistence, renderer, decoded-image cache, or native accessibility adapter is
moved into readerview0. Features absent from the current re10 EPUB reader—such
as later Kindle-gap reading controls—remain deferred until re10 and lectern0
share and stabilize this Stage 1 surface. Unsupported, missing, oversized,
corrupt, and cache-full image rows render bounded alt-text fallbacks.
Simple-grid adoption waits for a second host that presents table cells as
independent geometry; lectern0 does not invent a table UI merely to exercise
the foundation API.
