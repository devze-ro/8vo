# Lectern0 Reader View recovery Slice 2

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

## Purpose

Recovery Slice 2 restores the accepted pre-extraction re10 reader-chrome
foundation in lectern0 without reversing the extraction. The frozen reference
remains re10 commit `a6b1555ecb39c4948c735decda02cdc5a71f452c`;
cross-host similarity alone is not an acceptance criterion.

This host adoption pins:

- reader0 `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`, API 3;
- UI0 `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, API 91;
- readerview0 `27e2ac64bc9db87412cf076eac313dea902792eb`,
  API 3; and
- zero_foundation `eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c`,
  Presentation Engine API 1.

## Adopted foundation

Lectern0 consumes readerview0's one atomic toolbar, viewport, page-surface,
content, gutter, panel, and progress layout snapshot. It projects the accepted
`EPUB Reader` chrome title, paints the reader canvas and page as one square
borderless theme surface, and uses UI0 API 91's canonical caller-rasterized
reader icons except for the explicit frozen Filter mapping described below.
No host ASCII or low-fidelity line-art icon substitute remains.

Reader View API 3 adds only finite portable text-style identities to its
caller-provided bindings. Lectern0 maps `ChromeTitle` through the accepted
system-UI text-box raster at scale 2 and `MenuItem`, `ChromeMetadata`, and
`Default` at scale 1. `NoteEditor` draft rows use zero_foundation's
`TextEngineEditableRow` shaped painter at the explicit 18-pixel Body height,
and the visible caret x comes from that same shaped row; their independent
25-pixel TextArea line advance remains layout geometry.
Find excerpts retain their dedicated measured match path. The package supplies
no font pointer, callback, provider table, allocation, or drawing backend.

When the editor is open, a separate caller-owned 256-entry values table covers
the current draft, same-frame typed and transfer text, then the localized
empty-editor placeholder. Unique scalar advances, fallback advance, 18-pixel
height, and measured 25-pixel line advance are borrowed for one build. Missing
scalars fall back deterministically when the cap is exhausted. The same
system-UI provider supplies both those values and the host raster; no provider,
callback, allocation, or retained borrowed pointer crosses the package seam.

UI0's generic `Filter` intent is the one bounded icon adaptation. At the
Reader View host boundary, lectern0 renders the frozen re10 SlidersVertical
24 by 24 source geometry with the same four-by-four supersampling policy. A
regression locks the exact 18 by 18, stride-32 pixel hash
`768785035519145851` for the Annotations toolbar case; all other shared icons
continue through UI0's canonical rasterizer. This does not affect EPUB content
drawing or any non-Reader View consumer.

The host projection preserves the accepted reference footer wording:
`<percent>%   Location <current> of <count>` when reader0 locations are
available, `Page <current> of <count>` when only page totals are available,
and `Page <current>` as the final fallback. Readerview0 places and styles this
borrowed label but does not calculate document progress.

The trailing Exit Reader slot remains a lectern-owned native-window action.
Its presentation is rebuilt through a nonquiet UI0 `IconButton` record and
UI0's canonical Close icon, so the host identity is preserved while its shell
matches the reference control path. A bounded `Lectern0HostControlRecord`
provides its explicit host identity, portable semantic node, focus state, and
MSAA child without moving the action into readerview0. It is inserted between
the shared Find and Fullscreen semantic identities for forward and reverse Tab
traversal. Activation requires a press that begins inside and a release inside;
leave, capture loss, cancellation, or a press elsewhere clears the caller-owned
armed state.
The focus-visible host path reserves all five UI0 draw records emitted by the
labeled nonquiet IconButton plus explicit Close glyph. Its outer focus ring is
clipped to the 38-pixel host reservation, not the 30 by 28 control, and the
startup regression checks the actual adapted Close sprite and rounded-stroke
geometry so semantic focus alone cannot mask missing pixels.

The combined focus regression locks the frozen-a6b order by semantic identity:
Contents, Find, host Exit, Fullscreen, Annotations, Size, Spacing, Font, Theme,
Bookmark, Previous gutter, Next gutter, Progress, then wrap. The disabled
Previous gutter remains focusable at the first page but cannot emit an action;
its shared caret and focus ring remain visible just as in the frozen reference.
Next advances the concrete reader and Previous returns to the exact starting
spine and byte offset through both keyboard and pointer activation. The host
bridge accepts UI0's full 32 by 32 raster bound and independently hashes the
frozen 18 by 32 left and right caret pixels, so a stale 18 by 18 adapter cannot
silently drop the gutter glyphs. Native MSAA may focus that disabled advertised
stop but rejects its default action, and changing between shared and host
children clears the other focus owner. MSAA logical child IDs apply the same
Find, host Exit, Fullscreen insertion; native `NEXT` and `PREVIOUS` navigation
are tested in both directions rather than appending the host child after the
entire shared tree.
That closed 13-stop wrap applies only with no open panel. Contents, Find, and
Annotations each publish a focusable tail after Progress; the host delegates
forward and reverse wrap at that boundary so the panel cannot become
keyboard-inaccessible. The panel-focus regression crosses both boundaries for
all three panels with visible focus and proves that traversal emits no action
or document navigation (`panel_focus=toc_find_annotations_progress_boundary`).

TOC row keys remain `nav_index + 1`, not visible-row position. A synthetic
non-contiguous projection (`7`, `42`) locks keys `8`, `43`, including nested
depth/current state, before restoring the concrete reader0 frame. A pointer
and keyboard integration chain opens Contents through its real toolbar control,
activates TOC key `2`, verifies concrete navigation plus History Back, switches
to Find, activates the second projected match, and closes the panel through its
real close control (`navigation_panels=space_toc_find`). Win32 Space activates
the focused shared semantic control; it advances the page only when there is no
shared focus. Text input is routed only when the focused semantic is FindInput
or the Note TextArea, so Tabbed Clear/results, Note actions, and gutter controls
remain keyboard-operable while their panels are open
(`keyboard_routing=focused_edit_or_activate`). Ctrl+F uses the same pending
FindInput focus transition as the shared toolbar action, clears any host focus,
and accepts typed text on the immediately following frame
(`find_shortcut=focused_input`).

Find editing matches the frozen reference interaction: a non-empty Changed
action updates only Reader View's bounded query and cannot rebuild search,
navigate, or change history. Commit performs one concrete reader0 search and
then activates its first result; selecting a result routes its stable projected
key to the exact match index. An empty Changed action still clears the committed
search immediately without moving the document or changing either history
stack, matching the accepted Clear control (`find_clear=immediate`).

The deterministic Reader View smoke now includes adapter probes for all four
bound text styles. Those probes require the frozen scale and command color in
the bounded zero_foundation draw buffer. A separate reference-edge probe locks
rectangular and partially rounded focus rings, checkbox and non-checkbox
indicator borders, paired
corner-masked fill borders, and the explicit stroke colors of toggle, scroll,
and slider visuals. All round-top/round-bottom combinations and the unchanged
ordinary focus/control-fill paths are included. These are the drawing seams
exercised by the expanded TOC, Find, and Annotations panels rather than merely
by the closed toolbar.

Annotation rows retain the accepted heading/primary/metadata hierarchy.
Bookmark and Highlight primary content is the captured excerpt; Note primary
content is its persisted note body, as in frozen re10. Their secondary text is
exactly `Bookmark - re10 loc N`,
`Highlight - re10 loc N`, or `Note - re10 loc N`, where `N` is the one-based
128-byte global extracted-text location. Lectern0 retains note bodies in its
host-owned annotation records; readerview0 only borrows that primary text for
the current frame and never owns or persists it.
Highlight and Note rows use distinct projection keys that map back to the same
host record, so activate, independent star, edit, Note-only delete, and
Highlight delete actions retain their exact meanings. The version-3 host file
records Highlight identity independently of Note identity; version-1 and
version-2 files migrate as active Highlights. Deleting a Highlight with a Note
demotes the record to Note-only, while deleting its last Note removes the
record. A Highlight without a Note is removed directly. Attached Notes inherit
the highlight rail and carry `ReaderViewRow_AttachedToPrevious` only while the
corresponding Highlight is the immediately preceding visible row.
Mixed rows are sorted in a caller-owned fixed-cap candidate array using the
frozen spine, byte, Bookmark/Highlight/Note, and stable-ID order. A synthetic
mixed-order regression proves the projected order and independent Highlight
and Note star routing after sorting without rewriting durable records.
Bookmark rows always project `ReaderViewRow_Starred`, since the bookmark is
itself the reference's starred state. Its inline star removes the exact record
through the existing persistence helper; the regression locks one removal and
one revision increment rather than toggling the legacy persisted flag or
double-saving the mutation (`bookmark_star=projected_remove_once`).
Bookmark add/remove, independent Highlight/Note stars, row deletes, and Note
mutations are transactional. An atomic-write failure restores the original
array, count, next ID, star values, and annotation revision; the deterministic
smoke locks each rollback path.

The right-panel projection also supplies four unfiltered totals: all visible
Bookmark/Highlight/Note identities, Bookmarks, Highlights, and Notes. The
active filter changes only the projected row set and its `total_count`; it does
not change those four menu-label totals. Lectern0 supplies the accepted
`All Highlight Colors` base label so readerview0 can append the projected
count without owning annotation storage or counting policy.

Each Highlight and attached Note projects its resolved rail color explicitly
while retaining its `color_key` identity. Light rails are `FFF2A6`, `FFD4EC`,
`CDE7FF`, and `FFDCA8`; dark rails are `4D4A16`, `47355C`, `2A4662`, and
`523F1C`. Bookmarks project no color key and a zero rail color. A bounded host
regression covers all four filters, stable totals and base labels, light and
dark rail colors, and the rule that a Note is attached only when its matching
Highlight immediately precedes it in the active row set.
The host smoke additionally drives the real shared accessibility/action seam:
Annotations opens and closes with focus restoration, the filter selects and
closes with one returned action, and a Note row menu closes before its Edit
action is executed. The popup's frozen membership is Go to, Edit, and Delete;
Note starring remains the inline star control. Direct host checks continue to
cover row activation, independent Highlight/Note stars, edit, Note-only delete,
and Highlight delete. A second regression drives the complete pointer seam:
open, filter-popup Escape, Bookmarks and All selection, row activation, inline
star round-trip, row-menu Edit, dirty Save, dirty Cancel, Delete, and panel
close. It restores the original host annotation record and persistence file
afterward when persistence is enabled
(`annotations_pointer=open_filter_escape_select_row_star_menu_note_lifecycle_close`).
The inline Star and row Menu/Edit pointer paths also lock the concrete reader's
spine, byte offset, Back stack, and Forward stack, so child-control activation
cannot conceal a simultaneous row-navigation action behind the same pixels.

Find-result bindings carry reader0's exact byte match. The host clips the
accepted one-line excerpt using real system-UI measurements at scale 1, paints
the measured reader-highlight rectangle first, and then paints the complete
primary-text excerpt. The regression rejects character-width approximation or
split/recolored match text. The same adapter locks the frozen Find seams that
are intentionally distinct from generic labels: row metadata is right-aligned
and uses the system-UI ascent baseline, while the result-count status uses the
font provider's vertically centered baseline and the reference's two
coincident text passes. A focused smoke asserts the exact x/y records and
record counts for both paths, including the one-pixel match-highlight cap.

Persistent Highlight, active Find-match, and selection ranges are positioned
from the concrete EPUB row's shaped typography measurements and block
alignment, rather than distributing bytes across the row rectangle. Persisted
Highlights use the frozen square fill. An attached Note marker is the exact
7-by-8 host raster at the shaped range end, with its two 3-by-1 page-colored
interior strokes. These remain lectern-owned document drawing; readerview0
does not receive concrete page-content rows or rendering text, font providers,
or annotation persistence. Its projected TOC, Find, and annotation strings
remain bounded reader-chrome inputs.

The measurement boundary is a caller-owned 256-entry values table rather than
a font callback. Basic Latin is pinned; current edit and transfer text outrank
the placeholder, committed query, and history; stale dynamic entries are
evicted before lower-priority same-frame values; and `UINT64_MAX` generation
wrap deterministically ages all dynamic entries. The host regression covers
UTF-8 scalar decoding, exact advances, retention, priority refresh, full-table
eviction, pinned-value survival, and wrap.

Opening a Note row stores its exact document/highlight/spine/range target and
opens the shared editor without document navigation, selection, or history
changes. Stale Save is rejected while preserving the dirty draft. Successful
Save and Delete mutate the exact host record and then acknowledge completion
with `reader_view_close_note_editor`; failed persistence restores the record and
leaves the editor and dirty draft open. A Note created from a new selection
persists its provisional Highlight plus Note in one atomic mutation and rolls
both back on failure. For an annotation-origin editor, Save, Delete, and the
already-shared-closed Cancel release only the retained annotation target and
preserve an unrelated concrete document selection, selected text, anchor,
location, and both history stacks. Selection-origin completion continues to
release the selection owned by that flow. The lifecycle regression covers all
paths, V1/V2 migration, V3 Note-only restart, and location/history stability.

## Validation

The following passed from the dedicated Slice 2 worktree against exact clean
readerview0 `27e2ac64bc9db87412cf076eac313dea902792eb`, UI0
`cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, reader0
`3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`, and zero_foundation
`eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c` checkouts:

- exact dependency guard;
- lectern0 architecture audit;
- strict MSVC C11 `/W4 /WX` build;
- startup interaction smoke, including empty-document single-status ownership,
  press/release Open action timing, Exit press-origin cancellation, valid Exit
  activation, the Find/Exit/Fullscreen keyboard seam, and exact adapted Close
  sprite plus expanded host focus-ring geometry;
- deterministic Reader View smoke twice, hash `bbc068e6c290fee1`, including
  concrete TOC/Find actions, keyboard/pointer gutter round-trips, bounded Find
  metrics, bounded proportional 18-pixel Note-editor metrics, shaped draft
  raster and same-row caret placement, native
  Save/Delete/Cancel note acknowledgement, V1/V2-to-V3 annotation migration,
  unrelated-selection preservation for annotation-origin Save/Delete/Cancel,
  Note-only demotion/restart, and failed-write rollback
  for notes, Bookmarks, and independent Highlight/Note stars;
- concrete EPUB host smoke, hash `cd460506f219d652`;
- cover and inline-image smoke twice, hashes `c2cca908e35dd8d1` and
  `17989905b776b21d`;
- canonical-frame visual smoke twice, pixel hash `df02d5d2dd061128` and
  Presentation Engine geometry hash `3a3cf46f0444a1bd`; and
- native MSAA accessibility smoke twice, including shared and host focus/action
  convergence, Find/Exit/Fullscreen enumeration, host Exit default invocation,
  enabled/focusable `NEXT`/`PREVIOUS` traversal, disabled-action guards, and
  native fullscreen ownership.

The preceding panel prototype passed the frozen-a6b and two-host Stage 2B
matrices recorded at
`<historical-evidence-not-retained>`. That historical result
does not by itself accept the final readerview0 `27e2ac6` pin. The root-owned
integration gate must rerun the expanded frozen-reference and two-host matrices,
including dark Contents plus a same-theme wide-light Contents chain, Find, the
Annotations filter plus seeded Highlight+Note list,
row-menu and Note-editor cases, and keyboard-focused Exit plus Previous/Next
gutters in disabled-Previous, enabled-Previous, and enabled-Next states,
before this recovery slice is promoted.
The runner requires canonical fixture SHA-256
`F7D9F95A174E0CA776E2BA808A6798D2DAF8B178CFF5D77270D225EC5DDA14D8`
and clean re10, lectern0, reader0, UI0, readerview0, and zero_foundation trees by
default. Final acceptance also requires a fresh output root and an in-run build
of both hosts. `-AllowDirty` and `-SkipBuild` are diagnostic-only and cannot
support final acceptance. The cross-host matrix proves repeatable decoded visual states; it
does not replace the per-host smokes above, which prove concrete TOC/Find and
Annotations actions, persistence, keyboard/pointer gutters, focus, and native
accessibility execution.

The wide dark and wide light Contents cases retain different host-owned
control/draw hashes; Find does too. Bookmark/right-panel retains projection and
semantic differences, the Annotations filter retains
projection/control/draw/semantic differences, and the Note editor retains a
projection difference. These are accepted host identity/lifetime differences:
pixels, geometry, record counts, and action meaning are equal. They are not
erased merely to make internal hashes equal.

## Boundary retained

Readerview0 owns only shared UI0 composition, bounded transient interaction
state, portable records, and returned action records. Reader0 remains the
concrete EPUB engine. Lectern0 continues to own document rendering,
persistence, annotations, bookmarks, native accessibility, the native window,
Exit Reader execution, image-cache policy, and application status policy.
This slice adds no callbacks, providers, vtables, event buses, dependency
injection, generic document interface, hidden allocation, or process-global
mutable state.
