# Lectern0 Reader View recovery Slice 2

## Purpose

Recovery Slice 2 restores the accepted pre-extraction re10 reader-chrome
foundation in lectern0 without reversing the extraction. The frozen reference
remains re10 commit `a6b1555ecb39c4948c735decda02cdc5a71f452c`;
cross-host similarity alone is not an acceptance criterion.

This host adoption pins:

- reader0 `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`, API 3;
- UI0 `0ad8a43b658f67715720602abe779fb0d33052e1`, API 91;
- readerview0 `c5516aec47466bf47a7ffa182ce144df8b028acb`,
  API 3; and
- zero_foundation `eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c`,
  Presentation Engine API 1.

## Adopted foundation

Lectern0 consumes readerview0's one atomic toolbar, viewport, page-surface,
content, gutter, panel, and progress layout snapshot. It projects the accepted
`EPUB Reader` chrome title, paints the reader canvas and page as one square
borderless theme surface, and uses UI0 API 91's canonical caller-rasterized
reader icons. No host ASCII or line-art icon substitute remains.

Reader View API 3 adds only finite portable text-style identities to its
caller-provided bindings. Lectern0 maps `ChromeTitle` to the accepted
system-UI text-box raster at scale 2 and maps `MenuItem` and
`ChromeMetadata` to that raster at scale 1. Unstyled text continues through
the generic resolved-typography adapter. The package supplies no font pointer,
callback, provider table, allocation, or drawing backend.

The host projection preserves the accepted reference footer wording:
`<percent>%   Location <current> of <count>` when reader0 locations are
available, `Page <current> of <count>` when only page totals are available,
and `Page <current>` as the final fallback. Readerview0 places and styles this
borrowed label but does not calculate document progress.

The trailing Exit Reader slot remains a lectern-owned native-window action.
Its presentation is rebuilt through a nonquiet UI0 `IconButton` record and
UI0's canonical Close icon, so the host identity is preserved while its shell
matches the reference control path. Press/release handling is still executed
by `lectern0_host_pointer_press()` and `lectern0_host_pointer_release()`.

The deterministic Reader View smoke now includes adapter probes for all three
reference text styles and the unchanged generic path. Those probes require
the expected scales and command color in the bounded zero_foundation draw
buffer.

## Validation

The following passed from the dedicated Slice 2 worktree with exact clean
dependency checkouts:

- exact dependency guard;
- lectern0 architecture audit;
- strict MSVC C11 `/W4 /WX` build;
- startup interaction smoke, including empty-document single-status ownership
  and press/release Open action timing;
- deterministic Reader View smoke twice, hash `ced26e512ff35685`;
- concrete EPUB host smoke, hash `cd460506f219d652`; and
- native MSAA accessibility smoke twice, including shared focus/action
  convergence and native fullscreen ownership.

The frozen-a6b replay passed all six reference cases against re10, including
toolbar and Font-popup keyboard focus. The two-host Stage 2B matrix then passed
all six wide/narrow, light/dark, Contents, Find, bookmark/right-panel, and Font
menu cases with repeatable exact normalized decoded pixels. The final two-host
manifest is retained at
`C:\Temp\re10_lectern_rv2_slice2_final\manifest.json`.

The wide dark Contents and wide light Find cases retain different host-owned
control/draw hashes. The bookmark/right-panel case also retains different
projection and semantic hashes. These are the previously accepted host
identity/lifetime differences: pixels, geometry, record counts, and action
meaning are equal. They are not erased merely to make internal hashes equal.

## Boundary retained

Readerview0 owns only shared UI0 composition, bounded transient interaction
state, portable records, and returned action records. Reader0 remains the
concrete EPUB engine. Lectern0 continues to own document rendering,
persistence, annotations, bookmarks, native accessibility, the native window,
Exit Reader execution, image-cache policy, and application status policy.
This slice adds no callbacks, providers, vtables, event buses, dependency
injection, generic document interface, hidden allocation, or process-global
mutable state.
