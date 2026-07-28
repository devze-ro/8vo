# 8vo architecture

## Concrete boundary

8vo is a format-neutral Windows reader application. EPUB is its only current
document backend: the host owns one caller-allocated `EpubReader`, one bounded
`EpubReaderFrameStorage`, the current layout key and configuration, a Win32
window/backbuffer, readerview0/UI0 frame storage, ground0 draw/render
state, and versioned host persistence records.

The application shell is library-first: launch and Close Book resolve to a
8vo-owned Library surface, while an open EPUB resolves to Readerview0's
Reader surface. The library catalog is a bounded 512-entry, versioned binary
record written through ground0's atomic-file mechanism. 8vo owns
normalized absolute paths, local entry IDs, file size/modified-time
fingerprints, MRU ordering, import/remove/locate behavior, canonical-progress
persistence, native picker use, responsive cover cards, and its bounded
48-entry/24 MiB thumbnail cache. A missing source path is derived at runtime;
it does not erase metadata or the cached cover, and Remove deletes only catalog
and thumbnail state. Existing `state.v1` position data seeds the catalog once
when no catalog exists.

Reader0 supplies EPUB title, author, cover-resource access, and canonical
spine/byte locations. ground0 supplies decoding, drawing, and atomic
file replacement. UI0 and Readerview0 gain no library API in this slice: the
grid and cards are concrete host composition, while Readerview0 remains the
open-book experience. Re10 can later compose a Books destination using the
same Reader0 metadata and location concepts while retaining its own shell,
catalog policy, and persistence. No shared library package is extracted until
two host integrations establish a real common contract.

Each catalog entry reserves a bounded, algorithm-tagged digest field, initially
`None`. This preserves room for a later explicit local-content fingerprint,
but the local entry ID, normalized path, and file timestamps are deliberately
not treated as cross-device identity. Kindle-like sync for 8vo and Re10
will require a separately designed account, remote work identity, conflict,
privacy, and reconciliation layer; none of that behavior is implemented here.

Reader0 owns the EPUB document engine, source layout, typography, pagination,
page transitions, bounded navigation preparation, semantic navigation,
search/selection state, and canonical frames. 8vo passes its
viewport-derived layout values directly to Reader0 API 5. It asks Reader0 to
prepare at most one direction-aware window, queries up to four already
prepared forward page ranges, and copies each range through Reader0's
caller-storage page-frame builder. No reader state is mirrored or temporarily
mutated in application storage. Its narrow semantic adapters capture the
resulting canonical frame, set host status, and persist the resulting
reader-owned location.

8vo defensively checks a prepared result without taking over its
ownership. Same-spine results pass Reader0's public exact-adjacency validator.
For a cross-spine result, the active pagination cannot re-prove Reader0's
private prepared-ring ownership, so the host requires a public
`AdjacentSpine` or `AlreadyReady` result, a nonempty range, and strict movement
in the requested document direction. Reader0 remains solely responsible for
proving the exact cross-spine predecessor and its bounded lifetime.

8vo owns when presentation warming runs: 12 ms on first-open idle work and
8 ms on ordinary idle work. During a held page key it permits only Reader0's
direction-aware logical preparation after stable presentation and within the
remaining repeat-deadline slack; page-frame construction and raster warming
remain suspended. It warms at most four forward text pages and at most one
cross-spine page. The
existing one-page, image-free, 4096-by-4096-capped pixel snapshot remains an
explicit host presentation optimization because 8vo's software renderer
does not share Re10's retained UI caches. A cross-spine page that references an
embedded font is prepared by Reader0 but not rasterized until that spine's
font state becomes active. The snapshot is never persisted or synchronized
and is rejected unless canonical frame content, annotations, geometry,
typography, and theme all match.

Win32 page-key repeat is host policy. The first physical keydown moves
immediately, and repeat is armed only when Reader0 reports a successful page
move and the resulting canonical frame has been captured. While that same key
remains active, 8vo coalesces only its
native repeat messages and uses monotonic wall-clock deadlines equivalent to
Re10's 60 Hz 24/3 policy: 400 ms before the first repeat and 50 ms from each
actual emitted repeat to the next due time. It does not render idle frames to
count either delay. An action-to-presentation gate is independent of repeat
lifetime, so key-up, cancellation, or a direction change cannot admit a second
Reader0 move before the accepted frame is visibly stable. At most one new
physical page action is retained in an explicit pending slot; it runs only
after that presentation, and a released pending key cannot arm repeat. The
synchronous gate identity includes the Reader document id/generation, layout
generation, exact canonical page range, Reader frame generation, and host
capture generation. Text surfaces must match their exact visible byte range.
Image-only surfaces instead match that canonical page plus the decoded image's
visual-unit/placement signature, because their frame has no visible UTF-8
payload. Optional frame page-index/count summaries never override the committed
Reader canonical page. Capture failure leaves the gate outstanding; same-page
recovery requires a newer frame/capture epoch, while a successful book open
remains a successful catalog transaction and recovers its first frame in place.
All other open, close, history, seek, settings, repagination, picker, and Reader
View page mutations are rejected while that gate is outstanding. The scheduler
rebases after emission so a late frame cannot cause a catch-up burst. The paired
one-millisecond Win32 timer resolution prevents the wait from stretching to the
platform's coarse default. Each accepted action is followed by a complete
successful surface presentation whose post-action Reader View state needs no
follow-up frame before another action can advance. Key-up, focus loss, app
deactivation, or a Ctrl/Shift/Alt or system-key transition stops the stream;
none clears an outstanding presentation gate, and stale
repeats for that key are consumed until key-up. The bounded active message drain
reserves only a real invalid-region `WM_PAINT` for 8vo's own window, while
dispatching auxiliary-window and null-region paints normally. Before optional
Reader0 tail preparation, any already-queued message returns control to that
same bounded FIFO drain; key-up or another cancellation transition therefore
clears the pending tail before it can perform useless post-stop work.
Speculative page-frame construction, raster warming, and synchronous persistence remain
suspended for the duration of the hold; pending persistence and ordinary
forward idle warming are rescheduled after release. The real-queue regression
verifies this with temporary host-owned state/catalog files: the paired-save
counter and both files' modified times and bounded content hashes
stay fixed during the hold, then advance once after the stop condition's
debounced save. The two directions, five cancellation routes, and mutation
gate require eight unchanged holds and eight corresponding post-stop paired
host saves. Each file is replaced atomically on its own; this does not claim a
cross-file transaction or rollback. The SHA-locked GOTM queue proof performs one connected
13-page forward traversal and exact 13-page reversal across a spine boundary,
requires 26 canonical nonempty frames with no zero-page, orphan-text, or
mid-word-start frame, and gates sustained move/prepare, render, and present
maxima separately. The fixture-only content oracle is independent of adjacent-
page replay: for every long-form text page whose active source contains at
least 128 bytes, it checks the raw active-spine UTF-8 bytes at the canonical
`first_byte`, rejects continuation-byte starts and starts inside
ASCII/non-ASCII words (including apostrophe/hyphen connectors), and also
requires at least eight non-whitespace bytes and one completely covered style
row. This rejects the reported isolated-character defect without rejecting a
legitimate one-row chapter tail. Short
publisher headings remain valid when their exact canonical page/frame identity
and complete row coverage agree; image-only pages use the exact visual identity
described above. The
queue-derived range sequence proves ordering only; frozen Re10/cross-host
canonical ranges remain the independent acceptance oracle. The same real queue
also rejects seven mutation routes, exercises page/same-page/open capture
failure recovery, and locks gate identity on the cover plus three map pages.
Together, these checks match Re10's bounded action scheduling without moving
input, scheduling, rendering, or persistence into Reader0.

Readerview0 API 3 owns only the proven-common UI0 composition: the accepted
fixed-slot top toolbar, page gutters and progress geometry, TOC/Find and
annotations/bookmarks panel shells, settings and row-action popups, selection
tools, note-editor interaction state, focus order, portable semantic records,
bounded returned actions, and product-neutral page/content rectangles. Its
projection/action/geometry API does not name reader0 types and has no reader0
or direct ground0 dependency.

Reader View text bindings carry one finite portable style identity. 8vo
maps `ChromeTitle` through the accepted system-UI box raster at scale 2 and
`MenuItem`, `ChromeMetadata`, and `Default` at scale 1. `NoteEditor` draft rows
instead carry explicit Body typography metadata and use ground0's
`TextEngineEditableRow` shaped painter at its 18-pixel height; the visible
caret x is derived from that same shaped row. The TextArea's 25-pixel line
advance remains layout geometry, not a font size. Find-result parent semantics
retain Reader0's full excerpt. When its first fitted line would omit a valid
match, the child text binding can borrow Readerview0's one-line natural-word
window and remapped match bytes; 8vo still performs the concrete
full-string measurement, rasterization, highlight paint, and clipping. The
record conveys semantic presentation only: no font pointer, callback, provider
table, or allocation crosses the boundary.

8vo owns the projected TOC/search/selection meaning, settings choices,
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
body, matching the frozen reader. 8vo derives `N` from the concrete
reader0 extracted-text spine sizes as
`1 + global_text_byte_offset / 128`. The wording is a compatibility label, not
a re10 source dependency. Persistent note text stays in 8vo; Reader View
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

Find-row bindings preserve reader0's byte-accurate match range. 8vo uses
actual system-UI font measurement at scale 1 to fill a caller-owned 256-entry
codepoint-advance table consumed by readerview0 as portable values. Basic Latin
is pinned; the current edit/transfer text, placeholder, committed query, and
history have explicit priorities; stale dynamic values are evicted before
lower-priority same-frame values; and generation wrap ages all dynamic entries.
The same measurement path fits the accepted single line, positions the
reader-highlight background from the measured prefix and match, then draws the
complete excerpt in primary text. No fixed-width estimate, split/recolored text
run, callback, provider table, or allocation crosses the package boundary.

While the Note editor is open, 8vo rebuilds a separate caller-owned
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
open. After a successful host mutation, 8vo calls
`reader_view_close_note_editor` and then releases the editor's retained target.
Annotation-origin Save/Delete preserve any unrelated concrete document
selection, selected text, anchor, location, and history; selection-origin
completion releases its owned selection. Explicit Cancel applies the same
origin-sensitive host cleanup without a persistent mutation.

UI0 owns generic signal, control, token, layout, draw, text-edit records, and
the six shared reader-chrome profiles. A narrow 8vo adapter converts every
UI0 draw operation into clipped ground0 draw commands and binds semantic
typography roles to host font metrics. The generic `Filter` icon intent is
adapted only at this Reader View boundary to the frozen re10 SlidersVertical
24 by 24 geometry; an exact 18 by 18 raster hash prevents a portable-sprite
substitution from silently changing the accepted Annotations toolbar. All
other shared icons use UI0's canonical caller-rasterized pixels. UI0 does not
render EPUB content and is not a reader0 dependency.

Zero_foundation owns arenas, file/atomic-write facilities, font providers,
Presentation Engine API 1 block-flow geometry, draw commands, software
rendering, the Win32 DIB graphics seam, and the caller-owned encoded-image
decoder with its WIC backend. 8vo owns HWND/WndProc, DPI/input mapping,
the native EPUB picker, and presentation policy. Its bounded adapter resolves
reader canonical rows, host pixel metrics, and image boxes into caller-owned
block-flow specs, then draws only the returned row/media rectangles.

The presentation adapter also registers each reader-resolved font provider
with the host render cache, splits canonical rows at grapheme/word boundaries
to respect the bounded draw-command text capacity, advances chunks with
reader0's exact measurement path, and marks shaped text commands explicitly.
This keeps pagination measurement and rasterization on the same provider
without moving a renderer or font cache into reader0.

8vo also owns one bounded 64-entry image cache keyed by concrete reader0
document/resource identity. It fetches encoded resource bytes through reader0,
passes them to the explicit ground0 decoder, maps decoder failures into
canonical frame image status, and attaches arena-owned BGRA8 views to the
current frame. A separate 16-entry/64 MiB presentation cache retains
target-sized ground0 resamples for the active page and layout key.
The host retains cache capacity/full policy, failed-attempt retention,
media-type exclusions, cache reset on successful document open, resampling
policy, and final aspect-fit sprite/fallback presentation. Neither reader0 nor
ground0 owns product cache policy.

Image-only canonical rows are a distinct host-presentation case. Reader0 owns
their classification and `visual_units`; 8vo allocates the full content
width and exactly `visual_units * line_height` pixels of vertical media space,
then aspect-fits the decoded image inside that box. The 18-unit fallback is
used only when the canonical row omits a value, matching the frozen Re10
adapter. Loaded image-only media has no synthetic card background. 8vo
prepares area-filtered target surfaces for shrink, linear-filtered surfaces for
enlargement, and exact-size nearest surfaces; the prepared surface is then
drawn one-to-one. The same policy applies to publisher in-flow images.
Zero_foundation owns the resampling mechanism while 8vo owns the bounded
host cache and product policy. Library thumbnails use the same area filter,
persist as thumbnail format version 2 so legacy nearest thumbnails are
invalidated, and use explicit area/linear sampling for their final card fit.

Vertical placement follows the canonical row metadata through Presentation
Engine API 1: block top margins apply only to `line_row == 0`, while resolved
line/image height and bottom margins advance each row. The engine owns only
checked stacking and row-relative media rectangles. 8vo retains the
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
one atomic host boundary. 8vo uses the returned `content_rect` as the only
reader0 pagination and canonical-frame rectangle; `page_surface_rect` owns the
surrounding page paint. Shared gutters and panels are resolved in that same
state snapshot. A layout-affecting shared state change recomputes the contract
and repaginates at the current reader-owned location.

The accepted toolbar is a fixed right-aligned row of eleven shared 30 by 28 px
icon slots followed by one host slot, with a 56 px top chrome and 38 px footer.
A 38-pixel trailing reservation belongs to 8vo and contains its Close Book
control. 8vo retains the action and return-to-Library transition, while UI0
paints the control as a nonquiet `IconButton` with its canonical Close icon.
Bounds too small for the fixed reference contract fail closed.
Distraction-free behavior remains dormant rather than becoming mandatory
shared chrome, while the host independently performs native fullscreen window
transitions.

Readerview0 owns stable portable focus IDs, popup/modal containment, roles,
states, and pointer/keyboard/accessibility convergence. 8vo translates
Win32 input and implements a host-owned MSAA `IAccessible` object over the
current semantic records plus one explicit bounded host record for Close Book.
The host inserts that native action between shared Find and Fullscreen and
preserves the frozen reference's focusable disabled Previous gutter seam; all
other shared order remains driven by Reader View semantic identities. Close Book
pointer activation requires press origin and release inside the host slot and
is cancelled on leave or capture loss. `WM_GETOBJECT`, native object lifetime,
screen-reader events, coordinate translation, Close Book invocation, and execution
of returned reader actions stay in 8vo.
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
owned 32-pixel stride to ground0 sprites. Focused host regressions hash
the frozen 18 by 32 page-caret pixels and exercise both keyboard and pointer
gutter navigation; no host line-art fallback is permitted.

## Current exclusions

No PDF backend, generic document interface, library database, cloud sync,
shared library framework, shared
persistence, renderer, decoded-image cache, or native accessibility adapter is
moved into readerview0. Features absent from the current re10 EPUB reader—such
as later Kindle-gap reading controls—remain deferred until re10 and 8vo
share and stabilize this Stage 1 surface. Unsupported, missing, oversized,
corrupt, and cache-full image rows render bounded alt-text fallbacks.
Simple-grid adoption waits for a second host that presents table cells as
independent geometry; 8vo does not invent a table UI merely to exercise
the foundation API.
