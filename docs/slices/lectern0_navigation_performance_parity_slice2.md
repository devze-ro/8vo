# Lectern0 Reader0 navigation-performance parity slice 2

Date: 2026-07-20

Status: implemented, validated, and promoted fast-forward-only to the
local-only `main` branch on 2026-07-20

## Objective

Adopt the bounded Reader0 API 5 navigation-preparation contract in Lectern0
and apply the proven Re10 host pacing policy without merging the two hosts'
renderers, persistence, or input systems.

This slice follows the library regression repair at `5ecaa77`. It does not
start Reader0 Slice 4, add PDF support, change Readerview0 or UI0, or begin the
deferred Re10 Books destination.

## Shared Reader0 contract

Lectern0 pins Reader0 API 5 at `3b86a1f` and calls:

- `epub_reader_prepare_navigation` for one direction-aware same-spine or
  adjacent-spine preparation decision;
- `epub_reader_forward_page_range` for bounded already-prepared page lookup;
  and
- `epub_reader_build_page_frame` to copy a validated current or prepared page,
  including an adjacent-spine page, into caller-owned frame storage without
  moving or mutating the live reader.

Reader0 therefore owns page/window selection and canonical speculative frames.
Lectern0 no longer writes `current_page` or `view_byte_offset` temporarily to
construct an adjacent frame.

## Lectern0 host policy

Lectern0 retains the policies that cannot be shared with Re10:

- a 16 ms idle timer;
- a 12 ms first-open and 8 ms ordinary warming budget;
- four text commands per idle step;
- up to four same-spine forward pages and one cross-spine page;
- complete deferral while a page key is held;
- a 24-frame initial delay and three-frame repeat interval; and
- host-owned persistence debounce, invalidation, drawing, and progress status.

OS key-repeat messages are ignored for eligible page keys. The first keydown
still moves immediately; the host timer then emits at most one page action at
each accepted repeat point and stops on key-up, focus loss, Close Book, or
window destruction.

## Presentation cache boundary

Reader0 preparation does not replace Lectern0's previously accepted one-page
presentation snapshot. Removing that snapshot regressed prepared text-page
paint from roughly 9 ms to roughly 33 ms in the exact-book harness. Lectern0
therefore retains one explicit image-free raster capped at 4096 by 4096 pixels.
It is keyed by canonical frame content, annotation revision, page geometry,
viewport, typography, and theme, and its match is checked before redundant
presentation reconstruction.

The snapshot is renderer policy, not pagination state. It is bounded,
ephemeral, host-owned, never serialized, and never synchronized. Image pages
continue through Reader0 resource access plus zero_foundation decoding. A
cross-spine frame containing an embedded-font face is prepared but not
speculatively rasterized until the target spine's font state is active.

## Future sync

The slice does not alter the sync-ready ownership established by the library
reference lock. Durable catalog identity and canonical progress remain
Lectern0-owned records; preparation state, timers, font-cache entries, and the
page snapshot are ephemeral and must never enter a future sync protocol. Re10
can synchronize its own durable Books/progress records later while consuming
the same Reader0 canonical locations.

## Acceptance

The exact `gotm_new.epub` harness requires:

- exact size 955125 bytes and SHA-256
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`;
- 64 forward and at least 63 backward page turns with visible Reader chrome;
- the 24-frame/three-frame coalesced repeat contract;
- at least 16 prepared pages;
- 16/16 prepared snapshot hits and 16/16 warmed/cold pixel equality;
- prepared move max and prepared render max below 16.667 ms;
- warmed rendering materially faster than cold rendering; and
- zero draw, raster-cache, or run-cache overflow.

The official runner executes twice in a fresh process and records the exact
dependency pins, executable digest, clean Git state, and per-run timings.
Strict MSVC C11 `/W4 /WX`, dependency guards, the architecture audit, library,
Reader View, post-action arrow, and image-fit regressions remain required.

## Implementation evidence

The implementation commit is
`a5359e0b45d17f30f189c9024732b5d43193580f`. Its exact dependency check,
architecture audit, and strict MSVC C11 `/W4 /WX` build passed. The retained
library, post-action arrow, Reader View, and exact-book image-fit regressions
also passed; the library and post-action runners each repeated twice.

The clean-tree page-turn runner summary is
`local/validation/navigation-parity-page-turn-implementation/summary.json`
with SHA-256
`D5AC0EFD0ED9B9E246A7775B1EB55CEF3C7ADE6D162E29A222449AA74470C0BB`.
It records the expected Git head, an empty status, executable SHA-256
`2C50EC55BCD75E3DC72A4CCFB35A7BE7469C458D2B735531F8DF09BD5B604779`,
and two passing runs:

- run 1: 36 prepared pages, 16/16 snapshot hits, 16/16 pixel exact,
  0.328 ms prepared-move max, 11.021 ms warmed-render max, and 56.978 ms
  cold-render average;
- run 2: 36 prepared pages, 16/16 snapshot hits, 16/16 pixel exact,
  0.367 ms prepared-move max, 11.679 ms warmed-render max, and 54.565 ms
  cold-render average; and
- both runs: 64 forward, 63 backward, the 24/3 repeat contract, two emitted
  repeat moves, and zero draw, raster-cache, or run-cache overflow.

The four shaped-text overflow events are the existing bounded cache-eviction
counter observed while scanning this typography-heavy book. They neither
overflow drawing/raster/run storage nor affect the exact prepared-page output.

## Promotion state

Promotion was explicitly approved and completed on 2026-07-20 in dependency
order:

- Reader0 `main` and `origin/main` advanced fast-forward-only to
  `3b86a1f7c1eae281fbb6c0c5fdc65e3616317bea`;
- Re10 `main` and `origin/main` advanced fast-forward-only to
  `e504c87fee4d10f3c4c836925966c766971b1be8`; and
- local-only Lectern0 `main` advanced fast-forward-only through the regression
  repair, implementation, and clean evidence at
  `ca3257733ebfe02f0261936d311fe9da7f124b28`, followed only by this
  documentation completion record.

No Lectern0 remote was created. UI0, Readerview0, zero_foundation, and the
protected Re10 extraction worktree were not modified by promotion.
