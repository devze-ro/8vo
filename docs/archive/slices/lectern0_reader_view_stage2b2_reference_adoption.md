# Lectern0 Reader View Stage 2B-2 reference adoption

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

Date: 2026-07-17

## Revision closure

This local lectern0 slice starts from the accepted Stage 2B-0 checkpoint
`fea4245e3dadd905a0339840238c335e3c692e5f` and advances only the shared
reader-view dependencies:

- UI0 API 90 at `fda99de484d50f1b019b1edfe3489f57fae57f9a`;
- readerview0 API 2/version `0.2.0-dev` at
  `75c9ab4b622ba79a6d3c0761464a3d50eb25cc8c`;
- reader0 API 3 remains
  `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`;
- zero_foundation remains
  `eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c` with Presentation Engine API 1.

At the time, the work was local. No re10 source or dependency was changed, and
the preserved re10 Slice 3 worktree was not touched.

## Adopted reference policy

Lectern0 now projects the same common feature set, labels, first five available
font choices, four highlight labels, cross-spine page availability, bounded
row actions, and six shared reader-chrome themes as the reconciled reference.
Optional distraction-free behavior is not projected as mandatory common
chrome. Existing version-1 Light/Sepia/Dark settings load through an explicit
Light/Coral Light/Dark mapping; new files use version 2 and the six-profile
ordering.

Bookmark rows now carry separate bounded section and excerpt projections, so
the right panel does not repeat a section label or fall back to a generic row.
The host annotation file advances to version 2 and migrates version-1 bookmark
records without changing ownership, ID resolution, or the 128-record capacity.

The host reserves 38 pixels at the trailing end of the shared toolbar and owns
the rendered/interactive exit control. UI0/readerview0 do not own native window
exit behavior. Lectern0 continues to own fullscreen transitions, persistence,
EPUB records and actions, decoded-image caches, native accessibility, and all
content rendering.

Readerview0 API 2 resolves one value-only geometry contract from the current
viewport. Lectern0 uses the returned content rectangle for reader0 pagination,
Presentation Engine row/media geometry, selection/highlight painting, and text
clipping, and uses the page-surface rectangle for host page paint. The adopted
default policy is a 660-pixel maximum page, 24-pixel viewport inset, and 52/68
horizontal/vertical content insets.

The UI0 adapter now handles every public draw operation: paired rounded
fill/border shells, semantic typography and owner clipping, icons, focus,
indicators/check marks, toggles, segment joins, text selection/caret, scrolling,
and sliders. It contains no process-global mutable state and no longer replaces
reader controls with ASCII `<`, `>`, `X`, `...`, or `*` text.

## Deterministic evidence

The exact dependency guard, architecture audit, and strict MSVC C11 `/W4 /WX`
build pass. The Reader View smoke repeats byte-for-byte and reports API 2 hash
`6ccb4eaac405ffb9`; it also covers all UI0 draw-op branches with zero unsupported
operations, API 2 geometry, the host toolbar reservation, responsive overflow,
navigation/settings/find/bookmark/highlight/note actions, and versioned host
persistence.

The unchanged host/navigation hash remains `d7f4448c51b3fbd1`. The adopted
offscreen visual smoke repeats at pixel hash `8395bcab6817e857` and Presentation
Engine hash `47e10892739dd3df`. Image decode/cache evidence passes with cover hash
`98ede684ada05480`, inline hash `c9c279599d19c2e0`, and deterministic cache
telemetry. The host-owned MSAA adapter passes over the shared semantic/action
records with fullscreen native and distraction-free dormant.

The backward-compatible two-host harness accepts separate dependency roots for
the frozen re10 reference and the adopting lectern0 host. Its six cases use the
original byte-stable fixture SHA-256
`F7D9F95A174E0CA776E2BA808A6798D2DAF8B178CFF5D77270D225EC5DDA14D8`.
Both hosts repeat exactly in all cases. Every case now agrees on bounds,
responsive mode, toolbar density, viewport, layout hash, action hash, and
control/semantic/action counts. The machine-readable manifest remains under
`local/stage2b2_reader_view_conformance` and is intentionally ignored.

## Remaining closure gate

This slice does not claim cross-host pixel identity. The frozen re10 reference
still consumes UI0 API 89/readerview0 API 1, while lectern0 consumes the newly
approved shared theme and content-geometry policy. Projection/control/draw/
semantic hashes, one draw-record count, pagination, EPUB typography/raster
policy, and pixels therefore remain different. Visible shared chrome placement,
copy, color, and responsive structure now align, but the content page and book
typography still expose host-policy differences.

Stage 2B-3 should use this deterministic matrix to close those residuals. It
required a fresh fetch and reconciliation of re10 before any adoption, without
silently absorbing editor work. Supporting-repository publication occurred
later as separate project work.
