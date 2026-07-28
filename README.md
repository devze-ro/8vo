# 8vo

**8vo is a focused reader for your digital library.** EPUB is the first
supported format; the product name and public interface are intentionally not
tied to a particular file format.

The name `8vo` is the bibliographic abbreviation for *octavo* and is
pronounced *octavo*.

The current Windows application is a concrete host for the EPUB-focused
reader0 backend, readerview0, UI0, and ground0. It has no re10 source
dependency and does not define a generic document-provider layer.

8vo stores application data under `%LOCALAPPDATA%\8vo`. On first launch it
copies recognized state, library, settings, annotation, export, and thumbnail
files from the former `%LOCALAPPDATA%\lectern0` directory when present. The
legacy directory is retained, migration is atomic per file, and a completion
marker prevents a later launch from overwriting 8vo data.

Version 0.4 starts in an 8vo-owned library rather than an empty Reader
surface. Successfully opened EPUBs are added automatically and intentional
multi-file import uses the native picker. The bounded, versioned catalog keeps
normalized source paths, local entry IDs, optional algorithm-tagged digest
space, metadata, cover-thumbnail identity, most-recently-opened ordering, and
canonical progress. Missing files remain visible with Locate and Remove;
Remove never deletes the source EPUB. Close Book returns to Library, while
window Close and Alt+F4 exit the application. Catalog and bounded thumbnail
cache files remain local host policy and are written atomically without a
database. The reserved digest field is a future sync seam, not a cross-device
book identity or a sync implementation.

The integrated host provides a native window, an EPUB file picker and
command-line open, canonical-frame rendering, concrete reader0 API 4 page and
semantic navigation, resize repagination, and host-owned position, settings,
bookmark, highlight, and note persistence. Reader View API 3 composes the
accepted reference UI0 chrome shared with re10: top toolbar, page gutters, progress
seek, history, TOC/Find, font family/size/spacing/theme settings, bookmarks,
annotations, selection tools, note editing, and fullscreen. 8vo projects
concrete host state into that package and executes
its bounded action records; readerview0 does not own the EPUB engine or durable
records.

Annotation rows preserve the accepted reference information hierarchy:
section heading, primary content, then `Bookmark`, `Highlight`, or `Note`
metadata in the exact `<kind> - re10 loc <N>` format. Bookmark and Highlight
primary content is the captured excerpt; Note primary content is its persisted
note body, matching frozen re10. The displayed location is the stable one-based
128-byte global extracted-text location used by the reference. Note bodies
remain 8vo-owned and are also supplied to note-edit actions; they are not
repurposed as metadata.
Highlights and their notes are separate stable rows over the same host record:
each keeps its own star/delete meaning, an attached Note inherits the
highlight rail, and only a Note immediately following its visible Highlight
suppresses the inter-row gap. Find excerpts retain reader0's exact byte match;
the host supplies a fixed-cap values-only table of system-UI codepoint advances
and paints the accepted measured match background before the full one-line
excerpt. Basic Latin is pinned, current text outranks placeholder, committed,
and history text, stale dynamic values are evicted first, and generation wrap
ages every dynamic entry without callbacks or allocations.
The bounded host candidate array orders mixed annotations by spine, byte,
Bookmark/Highlight/Note kind, and stable record ID before projecting the
shared rows; it allocates nothing and does not alter persistence order.
Every Bookmark row projects as starred because the durable bookmark is itself
the frozen reference's starred state. Invoking that inline star removes the
bookmark through the normal persistence helper with one revision increment.
The version-3 annotation file stores Highlight identity separately from an
attached Note. Version-1 and version-2 records migrate as active Highlights.
Deleting a Highlight with a Note demotes the record to Note-only; deleting the
final Note removes it. Bookmark add/remove, Highlight and Note stars, row
deletes, and note mutations commit transactionally: a failed atomic write
restores the exact records, IDs, stars, counts, and annotation revision.

Opening an existing Note from Annotations retains its exact host record identity
without navigating, selecting text, or changing history. A successful Save or
Delete is acknowledged through `reader_view_close_note_editor`; stale revisions
or failed persistence leave the draft open and the host record unchanged.
Annotation-origin Save, Delete, and Cancel release only the retained annotation
target and preserve an unrelated concrete document selection, anchor, location,
and history; selection-origin completion still releases the selection owned by
that flow. Creating a Note from a new selection persists its provisional
Highlight and Note in one atomic mutation and rolls both back on failure.
Persistent note text and all mutations remain host-owned.

Recovery Slice 2 pins UI0 API 91 and readerview0 API 3. 8vo selects all six
shared chrome profiles, maps its legacy three-theme settings file explicitly,
reserves and renders the host-owned exit slot, and uses the shared page/content
geometry for pagination and EPUB presentation. Its draw adapter consumes every
UI0 operation with resolved typography, canonical reader icon pixels, and
clipped native draw commands. Chrome titles, ordinary panel labels, and
metadata use the accepted system-UI text-box placement path at their frozen
scales. Note-editor draft rows instead use ground0's
`TextEngineEditableRow` shaped painter at the explicit 18-pixel Body carrier,
and the visible caret x comes from that same shaped row. A separate bounded
values table measures the current draft, incoming/transfer text, and
localized placeholder for proportional wrap, hit, selection, and caret
geometry while keeping its 25-pixel line advance distinct from font size. The
dedicated measured Find excerpt remains separate. UI0's generic
Filter intent is the sole icon adaptation: 8vo maps it to the frozen re10
SlidersVertical raster and locks the exact 18 by 18 pixels. No callback or font
provider crosses the package boundary. The host-owned Close Book action is
painted as the same nonquiet UI0 IconButton shell and canonical Close icon used
by the reference; the former host-specific icon substitutions are gone. One
explicit host record inserts that native-window action between Find and
Fullscreen for the frozen reference's complete semantic keyboard order and
MSAA. The disabled first-page Previous gutter remains a focus stop with its
reference caret/ring but cannot invoke navigation. The bounded host raster
bridge preserves UI0's exact frozen 18 by 32 left/right caret pixels, and the
Reader View smoke locks both keyboard and pointer Next/Previous round-trips.
Native `NEXT`/`PREVIOUS` traversal scans enabled focusable children, so disabled
Back/Forward controls do not displace the required Find, host Close Book, Fullscreen
sequence; the disabled Previous gutter remains represented by its shared
semantic identity and keyboard focus contract.
When Contents, Find, or Annotations is open, the combined host/shared Tab seam
delegates at Progress so panel controls remain reachable in both directions;
the frozen 13-stop wrap applies only when no panel tail is present.
Pointer activation is armed only by a press inside the Exit slot and is
cancelled by leave/capture loss, so a drag into the control cannot close the
reader. Its focused nonquiet UI0 shell retains all five bounded fill, border,
text, focus-ring, and Close-icon records; the host reservation clips the outer
ring without clipping it to the 30 by 28 control. Startup evidence inspects the
actual adapted sprite and rounded focus-stroke geometry.

TOC keys retain reader0's source `nav_index` even when projected indices are
non-contiguous. Find edits remain transient until Commit (while Clear is
immediate), and result keys route to exact reader0 match indices. The host
smoke opens Contents and Find through the native routed Space-key path, executes a real
TOC navigation/history return and a real Find-row activation, then closes the
panel. It also exercises Annotations open/close focus restoration, filter
Escape/selection, row activation, star/menu interaction, real note
Save/Cancel/Delete buttons, and final panel close before restoring the original
host-owned annotation record.
The two-host parity runner carries both dark Contents coverage and a wide-light
Contents case that completes the same-theme a6b-to-re10-to-8vo visual chain.
It also carries real seeded Highlight+Note cases for the
Annotations list, row-action popup, and Note editor in addition to the
filter-popup case, and retains focused Exit plus disabled Previous, enabled
Previous, and enabled Next gutter cases.
It locks fixture SHA-256
`F7D9F95A174E0CA776E2BA808A6798D2DAF8B178CFF5D77270D225EC5DDA14D8`
and rejects dirty host or dependency trees for final evidence. Final evidence
must use a fresh output root and rebuild both hosts in-run; `-AllowDirty` and
`-SkipBuild` exist only for diagnosis and cannot produce acceptance evidence. These captures
prove decoded-pixel parity and repeatability; the per-host Reader View, host,
startup, and accessibility smokes prove action execution and native behavior.

Slice 5A's 8vo-owned document/resource image cache remains over
ground0's caller-owned decoder, so EPUB cover and inline PNG, JPEG, BMP,
and first-frame GIF resources render as decoded pixels. Unsupported, missing,
oversized, corrupt, or cache-full resources retain visible alt-text fallbacks.

The Presentation Engine API 1 adoption routes canonical EPUB row and image-box
vertical geometry through ground0's callback-free block-flow builder.
8vo still resolves reader/host metrics, owns caller storage, draws the
returned records, and retains every cache, persistence, and product decision.
Its native MSAA adapter exposes readerview0's portable semantic records plus
the bounded host Close Book record while keeping platform objects, screen-reader
events, native Close Book invocation, and shared-action execution in the application.

## Build and validate

From a linked worktree, point the dependency variables at exact clean
checkouts:

```powershell
$env:OCTAVO_READER0_DIR = 'C:\path\to\reader0-api5-worktree'
$env:OCTAVO_UI0_DIR = 'C:\path\to\ui0'
$env:OCTAVO_READERVIEW0_DIR = 'C:\path\to\readerview0-api3-worktree'
$env:OCTAVO_GROUND0_DIR = 'C:\path\to\ground0'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\require_dependencies_current.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\audit_architecture.ps1
cmd /c build\win32_build.bat no_run
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_host_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_data_migration_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_library_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_reader_view_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_reader_view_startup_interaction_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_accessibility_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_visual_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_image_smoke.ps1
```

The former `LECTERN0_*` dependency and compiler environment variables remain
accepted as compatibility fallbacks during the rename.

## License

Unless otherwise noted, first-party source code and documentation in this
repository are licensed under the Mozilla Public License 2.0 (`MPL-2.0`). See
[LICENSE](LICENSE).

Bundled and source-consumed third-party materials remain under their original
licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
