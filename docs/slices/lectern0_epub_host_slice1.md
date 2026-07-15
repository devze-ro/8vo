# Lectern0 EPUB host Slice 1

Date: 2026-07-15

## Objective

Create the first real standalone application consuming the concrete reader0
boundary. The host must open an EPUB, render a canonical page, move forward and
backward across spine boundaries through reader0 API 2, repaginate on resize,
and retain one last-book location without importing re10.

## Dependency pins

- reader0 `f3af1ca1b74ac756d7f22ed50cc8c0018a439663`, API 2, `0.2.0-dev`;
- UI0 `f8de965c193a6278d330193c34948bfec09e592b`, API 89;
- zero_foundation `91ee04f51959ec301ad6db568112e915a02420c0`, `0.2.99-dev`.

All are sibling-source dependencies guarded by exact commit, API/version, and
clean-working-tree checks. Lectern0 includes `reader0.h` and `ui0.h` and
compiles each package unity source exactly once.

## Host responsibilities

The application owns native window/input/file-picker code, viewport-derived
layout values, toolbar composition, canonical-frame presentation, status text,
and an atomic versioned state file. Reader/frame/layout/arena lifetimes remain
caller-owned. The headless smoke uses the same application aggregate and direct
API 2 operation without a window or persistence side effect. It proves an exact
forward/back/forward cross-spine transition, containing-byte preservation after
resize repagination, and a visible failure result for a missing EPUB.

## Non-goals

Slice 1 deliberately excludes PDF, generic providers, callbacks/vtables,
annotations, search/TOC panels, shared chrome, image decoding, accessibility
redesign, and optional reader extraction work.

## Validation

- exact reader0, UI0, and zero_foundation commit/API/version guards: pass;
- reader0/UI0 source-closure and concrete-boundary audit: pass;
- native MSVC C11 `/W4 /WX` build: pass;
- cross-spine forward/back/forward and resize-repagination host smoke: pass;
- missing-EPUB visible failure path: pass; and
- canonical-frame text evidence: `d7f4448c51b3fbd1`;
- repeatable generated-fixture visual evidence: `df9d9534bb1c2f06`;
  and
- repeatable real 77-section EPUB visual evidence: `279fa27e7878d091`.

The native interaction pass verified the blank state, EPUB picker, real-book
open, keyboard cross-spine navigation, mouse Next action, maximize
repagination, and restart/location restoration. It exposed three host defects:
an empty title caused by a non-ASCII source literal, stale resize status, and a
render-cache fallback font whose metrics differed from reader0 measurement.
The title is now ASCII, repagination refreshes status from the rebuilt frame,
and the host registers reader-resolved providers and shaped commands in its
render cache. Rows longer than the bounded draw-command text capacity are
submitted as measured grapheme-safe chunks. Two-process, byte-identical
post-fix bitmaps show complete book-serif rows and matching toolbar/footer
location.

A post-fix foreground title-bar capture is not claimed because the Windows
control helper twice returned the fresh lectern0 window but failed to activate
it. No alternative foreground automation mechanism was used.
