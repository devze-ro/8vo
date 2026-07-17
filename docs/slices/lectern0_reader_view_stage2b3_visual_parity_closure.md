# Lectern0 Reader View Stage 2B-3 visual parity closure

Date: 2026-07-17

Status: implemented and validated locally; lectern0 remains local-only

## Outcome

Lectern0 now renders the re10 EPUB Reader reference look and feel pixel for
pixel in the deterministic Reader View matrix while retaining its own host
records, persistence, native window, accessibility adapter, renderer, and
reader0 action execution.

The local branch `codex/reader-view-stage2b3-parity-closure` starts from the
accepted Stage 2B-2 tip `255bcee7c99c7ccd01c086cd550f354b0676dbce`.
No lectern0 or readerview0 GitHub repository was created and nothing was
pushed.

## Implementation

The production host alignment is deliberately bounded:

- reader0-derived text measurement and font metrics now drive pagination;
- the default Georgia/22-pixel reading policy, heading emphasis, alignment,
  line geometry, and shaped text raster path match the reference;
- Presentation Engine rows and readerview0 API 2 content/page rectangles are
  used consistently for layout and painting;
- the host exit control uses the reference geometry, colors, and a bounded
  caller-owned Lucide close-icon raster buffer;
- progress uses the shared zero-based projection contract;
- bookmark excerpts retain the section title when the reference does;
- Find paints reader0 frame search ranges behind text, uses the reference
  active/inactive colors, and hides those overlays when Find is closed; and
- the parity capture drives real shared controls, text input, submission, and
  returned actions.

No callback/provider table, event bus, hidden allocation, process-global
mutable state, shared persistence, shared image cache, shared renderer, or
shared native accessibility adapter was introduced.

## Exact closure and retained host distinctions

The final six cases are all decoded-pixel exact: wide and narrow defaults,
dark Contents, Find with `alpha`, bookmark/right panel, and the font popup.
Default, responsive, and font-popup normalized records are also fully exact.
Contents and Find retain internal control/draw-record differences while their
portable semantic records and pixels are exact. The bookmark case retains
host-owned projection/control/draw/semantic details while its layout, actions,
counts, and pixels are exact.

The parity runner now compares normalized decoded `Format32bppArgb` pixels and
records `pixel_sha256` per host. Whole BMP-file hashes remain available as
container evidence but no longer stand in for pixel equality.

## Validation

The exact dependency guard and architecture audit pass for:

- UI0 API 90 at `fda99de484d50f1b019b1edfe3489f57fae57f9a`;
- readerview0 API 2 at `75c9ab4b622ba79a6d3c0761464a3d50eb25cc8c`;
- reader0 API 3 at `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`;
  and
- zero_foundation at `eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c`.

Strict MSVC C11 `/W4 /WX` build and all focused host validations pass:

- host/navigation hash: `7bee48a794c1ad09`;
- repeatable Reader View API 2 hash: `6ccb4eaac405ffb9`;
- repeatable visual hash: `2506739365bfdfd0`;
- Presentation Engine hash: `3efb0e3f4b571715`;
- cover/inline image hashes: `a976ba0298994bd4` /
  `8a575edbd2f99725`; and
- repeatable host-owned MSAA adapter evidence over 18 shared semantic nodes.

The final matrix is deterministic and reports `exact_visual_parity: true` for
fixture SHA-256
`F7D9F95A174E0CA776E2BA808A6798D2DAF8B178CFF5D77270D225EC5DDA14D8`.

WSL is not installed, so native Linux validation is not claimed.
