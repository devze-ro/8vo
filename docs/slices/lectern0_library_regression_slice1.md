# Lectern0 library regression Slice 1

Date: 2026-07-20

## Objective and isolation

Close the five regressions reported after Lectern0 library v1 without changing
the extracted repository boundaries or beginning the Re10 Books destination:

1. library cards did not communicate hover or press;
2. a migrated catalog could show no cover on its first library frame;
3. opening a card left the Reader View arrow keys blocked until a gutter click;
4. the Reader View toolbar disappeared on first forward traversal of some
   newly paginated GOTM pages; and
5. ordinary adjacent page turns were materially slower than Re10.

Work was isolated on `codex/lectern0-library-regression-slice1` from Lectern0
`813afe546de4642f607e09d25c7a834e62577f6a`. Canonical local-only `main`
remained unchanged while this record was written. The retired image-fit
worktree and protected Reader0 extraction worktree were not reused or changed.

Exact dependencies remained:

- Reader0 `c7b63d9cb38829219f41795ae2c89bf80707b2cf`, API 4;
- UI0 `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, API 91;
- Readerview0 `26d836390fce2de64198430fa82d6f660fc7fc07`,
  API 3; and
- zero_foundation `3eab21c06c4aa0b4915f9e7fcb3830ba1688451f`,
  `0.4.1-dev`, Presentation Engine API 1.

The canonical zero_foundation checkout advanced independently after the gate.
Lectern0 used an isolated, clean snapshot of its exact recorded dependency;
none of the parallel zero_foundation work was absorbed.

## Exact book

All real-book diagnosis and acceptance used:

- path: `C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub`;
- size: 955125 bytes; and
- SHA-256:
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.

## Diagnosis and implementation

### Card affordance

The library card itself had selected/focused styling but no pointer-state
styling. Lectern0 now resolves a card-local hover and armed press state while
excluding the nested Locate and Remove controls. Hover uses a raised shadow,
warmer surface, and stronger border. Press removes the shadow and uses a
deeper surface and accent border. The policy is Lectern0 composition; no UI0
primitive was needed.

### First-frame metadata and cover

Library v1 migration intentionally seeded a catalog record from `state.v1`,
but the library surface did not open that record to inspect Reader0 metadata
until the user opened the book. A new persisted `Inspected` metadata flag
distinguishes "not inspected yet" from "inspected and no cover available"
without changing the catalog record shape or version.

At startup, Lectern0 hydrates at most the selected/MRU, present, uninspected
entry. It opens the EPUB through Reader0, reads title, author, and Reader0's
cover resource identity, decodes through zero_foundation, writes the bounded
host thumbnail, closes Reader0, clears the transient decoded-image cache, and
atomically saves the catalog before the first library frame. This is bounded
to one legacy entry per launch. Normal book opens use the same metadata helper.

### Card-to-reader focus transfer

The card remained the active host focus record after its pointer action opened
the EPUB. Reader key routing therefore treated Left and Right as host-control
input until another pointer action cleared that state. A successful open now
clears host focus visibility and all host pointer arms before entering the
open-book surface. Immediate Right then Left is asserted without any gutter
interaction.

### Toolbar continuity

The exact-book scan reproduced the disappearing toolbar at forward step 26.
Reader0 had produced a valid new-window frame whose display page index was 2,
while total pagination and canonical location were temporarily unavailable.
Lectern0 projected that nonzero index with a zero count. Readerview0 correctly
rejected the inconsistent progress record with
`ReaderViewFrameError_InvalidProgress`; Lectern0 then skipped the whole shared
chrome frame.

When a location or page count is unavailable, Lectern0 now projects bounded
zero indices and retains the human label (`Page 2`). A 64-page forward and
63-page backward GOTM scan asserts a ready shared frame, visible toolbar, no
Reader View error, and no draw overflow on every visited page. Readerview0 and
Reader0 were unchanged.

### Page-turn latency

Two synchronous host costs were on the arrow-key path:

- every move atomically rewrote both `state.v1` and `library.v1`; and
- the newly visible page shaped and rasterized all text during the first
  foreground paint.

Canonical progress is still updated in memory immediately. Durable state and
catalog writes are now coalesced by a 250 ms host timer, while open, close, and
shutdown still flush synchronously.

After a successful page change, Lectern0 uses idle timer work to build the
next same-pagination Reader0 frame and warm shaped text in bounded batches.
It then records one pixel-exact, next-page raster in an explicit host-owned
buffer. The buffer is capped at 4096 by 4096 pixels, covers only image-free
pages, and is keyed by document/frame content, selection, highlights, notes,
annotations, viewport geometry, typography, and theme. Image pages continue
through the existing Reader0 resource plus zero_foundation decoding path.

The cache is ephemeral presentation policy. It is not the removed shared image
cache, is never serialized or synchronized, and is invalidated by incompatible
page, layout, annotation, or theme state. Exact-book A/B acceptance requires
the warmed and cold full-frame pixel hashes to match.

The reviewed two-run sample measured 16 exact adjacent pages per run:

| Run | Warm render average | Cold render average | Exact rasters |
| --- | ---: | ---: | ---: |
| 1 | 9.035 ms | 63.734 ms | 16/16 |
| 2 | 8.745 ms | 64.942 ms | 16/16 |

This closes the ordinary adjacent-page rendering cost. The diagnostic also
observed an occasional roughly 0.5 second cold Reader0 pagination boundary;
this slice does not begin Reader0 Slice 4 or claim to eliminate every cold
cross-window/cross-spine boundary. If that remains user-visible after this
change, it should be a separately measured host-preparation slice using
existing Reader0 mechanisms.

## Ownership and future sync

All product changes are Lectern0-owned:

- library hover/press composition and host focus transfer;
- bounded startup catalog hydration and thumbnail policy;
- progress projection into the existing Readerview0 contract;
- persistence coalescing; and
- the bounded one-page presentation cache.

Reader0 remains the owner of EPUB metadata, canonical locations, pagination,
and concrete frames. zero_foundation remains the owner of decoding, drawing,
rendering, fonts, and atomic-file mechanisms. UI0 and Readerview0 required no
new API or implementation.

Future Kindle-like sync remains compatible with this repair. Durable canonical
progress continues to reach `state.v1` and the catalog, close and shutdown
force a flush, and the existing local-ID/digest seam remains unchanged. A
future sync queue should consume durable canonical state, not the debounce
timer or ephemeral page raster. No account, network, shared catalog, cloud
identity, or conflict policy was introduced.

## Acceptance

The dedicated library harness now covers:

- migrated-entry hydration before the first library frame;
- title, author, and cover availability plus persisted thumbnail;
- distinct deterministic idle, hover, and pressed card states;
- pointer cancellation;
- pointer card open followed immediately by Right and Left;
- keyboard card open;
- canonical progress, Close Book, and restart;
- missing Locate/Remove and source preservation;
- wide/compact layout and host semantics; and
- repeatability across two runs.

The exact-book page-turn harness guards the book hash and size, scans forward
and backward across the reported chapter-start states, compares 16 warmed and
cold full frames per run, repeats in a fresh process, and rejects Reader View,
draw, raster-cache, or run-cache errors.

Strict MSVC C11 `/W4 /WX`, exact dependency guards, and the architecture audit
pass. Existing host, Reader View, image-fit, post-action arrow, selection-menu,
and accessibility fixtures also pass. The running application was inspected
directly: the first library surface showed the real cover, card hover/press
were distinct, a card open accepted Right immediately, and the toolbar
remained visible on the next and restored pages.

Implementation is confined to Lectern0 and awaits fast-forward promotion.
No Re10 Books work is part of this slice.
