# Lectern0 library v1

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

Status: implemented and validated on the dedicated
`codex/lectern0-library-v1` branch, then promoted fast-forward-only to the
local `main` branch on 2026-07-20.

## Exact inputs

- Lectern0 base: `97d5211af7bc648e7e1048c9fc3c7b6bfe4911c2`
- Lectern0 implementation: `e9340de4d3cd19e7da15820ab04f34dc82128b68`
- split-Reader0 parity support: `f38f05dc97d11e8cefaf8306d224359e6feeae60`
- Reader0 author metadata/API 4: `c7b63d9cb38829219f41795ae2c89bf80707b2cf`
- UI0 API 91: `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`
- Readerview0 API 3: `26d836390fce2de64198430fa82d6f660fc7fc07`
- zero_foundation 0.4.1/Presentation Engine API 1:
  `3eab21c06c4aa0b4915f9e7fcb3830ba1688451f`

Lectern0 advances to `0.4.0-dev`. Reader0 advances to API 4 and
`0.4.0-dev` only to expose bounded EPUB author metadata; Re10 remains correctly
pinned to Reader0 API 3 at
`3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`.

## Product result

The application flow is now:

`Launch -> Library -> open/resume EPUB -> Close Book -> Library`

Window Close and Alt+F4 still exit the application. Any EPUB that opens
successfully is added automatically. Add EPUBs uses the native multi-select
picker for intentional import. The library presents cover cards ordered by
most recently opened, with title, author, progress, and last-opened date.
Opening a card resumes its saved canonical Reader0 location.

Missing entries remain visible and retain their metadata and cached cover.
Locate lets the user select a replacement EPUB and preserves the local catalog
entry identity. Remove deletes the catalog entry and host thumbnail only; it
never deletes or moves the source EPUB. The empty state provides one coherent
Add EPUBs call to action rather than an empty Reader surface.

## Ownership and boundaries

Lectern0 owns the library-first shell, catalog, normalized paths, local IDs,
file fingerprints, MRU policy, picker, import/locate/remove behavior,
responsive card composition, focus and input routing, accessibility adapter,
and thumbnail-cache policy.

Reader0 owns EPUB parsing and supplies title, author, cover resources, and
canonical spine/href/byte locations. zero_foundation supplies atomic-file,
image-decoding, draw, and rendering mechanisms. UI0 and Readerview0 are
unchanged: no library package or general document framework was added, and
Readerview0 remains the open-book experience.

Re10 can later add a host-specific Books destination using the same Reader0
metadata and location concepts. It does not need to share Lectern0's shell,
catalog file, file-path policy, sorting, or visual composition. A shared
library package remains deferred until two real integrations demonstrate a
stable common contract.

## Bounded catalog

`library.v1` is a Lectern0-owned, versioned binary file under
`%LOCALAPPDATA%\lectern0`. It has a 2 MiB compile-time cap and at most 512
entries. The written prefix contains the magic, version, entry count, next
local ID, and exactly the live fixed-size records. Writes use
`os_write_entire_file_atomic`.

Each entry contains:

- a monotonically allocated local entry ID;
- added and last-opened Windows file times;
- normalized absolute UTF-8 source path;
- file size and modified-time fingerprint;
- algorithm-tagged optional 32-byte digest (`None` initially, SHA-256 reserved);
- title and author with explicit availability flags;
- cover resource identity and cover-availability flag;
- canonical progress spine href, fallback spine index, byte offset, and
  display percent; and
- runtime-only missing status, recomputed from the filesystem and zeroed on
  disk.

Path matching is normalized and case-insensitive on Windows. Ordering is
deterministic: last-opened time descending, added time descending, title, then
local ID. The previous `state.v1` reading position seeds one catalog entry only
when a catalog is absent, preserving upgrade continuity even when that source
is missing.

This is not a database. It introduces no callbacks, vtables, provider tables,
event bus, dependency injection, hidden catalog allocation, or process-global
mutable state.

## Cover policy

Reader0 identifies the cover resource. Lectern0 fetches it through Reader0 and
uses zero_foundation's existing WIC-backed decoder. The host writes at most 48
thumbnails, with a 24 MiB in-memory budget and maximum dimensions 256 by 384.
Files are keyed by local entry ID and validated against the source size and
modified time. Nearest downsampling is explicit. Missing source files can
continue to display the already validated thumbnail.

The cache remains bounded and host-owned; the removed shared decoded-image
cache is not revived.

## Interaction and accessibility

The responsive grid resolves one to eight columns. The acceptance captures
lock a 1100 by 760 wide state and a 520 by 720 three-column compact state.
Cards truncate long title and author lines with an ellipsis while preserving
the full accessible name.

- Tab and Shift+Tab traverse Add EPUBs, visible books, and missing-file actions.
- Arrow keys move spatially through the grid; Home and End select boundaries.
- Enter or Space activates the focused control.
- Delete removes the selected book from the catalog only.
- `L` invokes Locate for a missing selected book.
- Pointer activation requires press origin and release inside the same
  semantic control; leaving cancels the armed action.
- Focus remains visible after keyboard navigation and returns to a valid
  library target after Close Book or Remove.
- The Win32 MSAA root is named `lectern0 Library` on the library surface.
  Add, book, Locate, and Remove are bounded host semantic buttons with stable
  entry-backed source keys.
- The open-book host control is named Close Book and returns to Library;
  it remains between shared Find and Fullscreen in the accepted Reader View
  order.

## Future sync seam

Kindle-like synchronization is still deferred, but the implementation avoids
making it needlessly difficult. Local entry IDs, normalized paths, file size,
and modified time are explicitly local facts and are not treated as
cross-device book identity. The algorithm-tagged digest field can later hold a
bounded local content fingerprint without changing the v1 record shape.

A future sync slice must separately define account identity, remote work
identity, canonical-location reconciliation, annotation conflict behavior,
privacy, deletion semantics, offline queues, and server contracts for both
Lectern0 and Re10. No network client, cloud catalog, shared database, or sync
framework is present in this slice.

## Dedicated library acceptance

Exact book:

- path: `<external-fixture>\gotm_new.epub`
- size: 955125 bytes
- SHA-256:
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`

Clean summary:

- path:
  `<historical-evidence-not-retained>`
- SHA-256:
  `8E41BD584C7D45AA25D227F6EBA38CD06162C7F305BCF24646F5B7F162536400`
- clean tree: pass
- strict MSVC C11 `/W4 /WX`: pass
- repeatability: 2/2
- internal repeat pixel hash: `e6e4bd3b5b3bf871`
- empty BMP SHA-256:
  `F0CC36085349CEC7D74CF347AA4B58781DFBACA74481BB38FF3487A439EBC02C`
- populated BMP SHA-256:
  `957BDA50E2FDA69F5C0178A250D0F982B8A0AB843F0ED20EDECC52D0D340CB13`
- restart and repeat BMP SHA-256:
  `B5ED8EC411104B4625961187B69296EEDD27BE9152F199A36076BA92782401F5`
- compact BMP SHA-256:
  `13C186524BDB60D53A8C6B622A0A8A1355586DE171642598D097C26F1C2DE111`
- missing BMP SHA-256:
  `5E7892645DF6E22B3308E641F87C0563326CAFE66670C22FB0FBAF87CC753E31`

The harness covers bounded atomic catalog I/O, MRU ordering, legacy migration,
Reader0 title/author/cover metadata, thumbnail persistence, canonical progress,
Close Book return, restart, pointer cancellation and pointer/keyboard open,
missing Locate/Remove semantics, source preservation, compact layout, host
accessibility records, and the reserved digest state.

All six rendered states were inspected directly.

## Regression and parity evidence

The following pass on the exact dependency pins:

- dependency-current guard and architecture audit;
- host smoke;
- empty-library startup pointer/keyboard smoke;
- Win32 MSAA accessibility smoke, 2/2;
- Reader View smoke, 2/2, hash `bbc068e6c290fee1`;
- visual smoke, 2/2, hash `df02d5d2dd061128`;
- cover/inline image smoke, 2/2;
- publisher typography/spacing smoke;
- cover plus three image-only map pages, 4/4 and 2/2;
- post-action arrow routing, 2/2;
- active Find contrast across all six themes;
- Find snippet context; and
- selection-action menu recovery, 2/2.

Clean cross-host manifest:

- path:
  `<historical-evidence-not-retained>`
- SHA-256:
  `89E2B28193F7B490292438DCAB812D1C4285264E29EF53AA5F9AB99FF7127CE5`
- in-harness Re10 and Lectern0 builds: pass
- clean-tree enforcement: pass
- exact split Reader0 pins: pass
- deterministic: 15/15
- decoded-pixel exact: 15/15
- exact records: 9/15
- accepted pixel-exact record differences: 6/15
- acceptance eligible: true

## Promotion state

Promotion was explicitly approved and completed on 2026-07-20 in dependency
order using fast-forward-only updates:

- Reader0 `main` and `origin/main` advanced from
  `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd` to
  `c7b63d9cb38829219f41795ae2c89bf80707b2cf` (API 4,
  version `0.4.0-dev`); and
- local Lectern0 `main` advanced from
  `97d5211af7bc648e7e1048c9fc3c7b6bfe4911c2` through the library
  implementation and evidence commit
  `b385543836897b651458dfa9563aa1891a51113e`, followed by this
  documentation-only promotion record.

At that time, Lectern0 had not been published. Re10, UI0, zero_foundation, and readerview0 were
not advanced. The isolated reader-core reference checkout and the retired
image-fit worktree were verified clean at their locked commits before
promotion and were not modified.
