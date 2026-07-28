# Lectern0 Reader View Stage 2B-0 parity harness

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

Date: 2026-07-17

Lectern0 now aligns its exact zero_foundation source closure to promoted
revision `eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c` and source-consumes the
diagnostic-only readerview0 revision
`787bcbb27060505ef7ff64e07e2a6bdc8277b6c3`. Reader0 API 3 and UI0 API 89
remain on their previously promoted exact revisions.

The headless command
`--reader-view-parity-capture epub width height theme left right popup query evidence bmp`
creates a settled deterministic frame at an exact reader-client size and
records the same normalized projection/layout/control/draw/semantic/action
evidence as re10. Supported Stage 2B-0 states are light/dark, no left panel,
Contents, Find, closed/open/bookmark right panel, no popup/font menu, and a
bounded ASCII Find query (`-` means empty).

This command is test plumbing. It does not change production chrome
composition, host projection policy, rendering, persistence, focus,
accessibility, or reader0 execution. Existing parity differences are preserved
as the baseline that Stage 2B-1 and Stage 2B-2 will close.

`scripts/win32_reader_view_stage2b0_parity.ps1` creates one synthetic
redistributable two-chapter EPUB, runs six fixed state/size cases twice in both
hosts, crops re10 to its exact reader client, verifies per-host repeatability,
and writes a component comparison manifest. Cross-host differences are
reported but do not fail Stage 2B-0; nondeterminism, missing evidence, build
failure, or capture failure does.

## Initial baseline evidence

The six-case matrix passed twice per host with synthetic fixture SHA-256
`F7D9F95A174E0CA776E2BA808A6798D2DAF8B178CFF5D77270D225EC5DDA14D8`.
Every re10 evidence file/crop and every lectern0 evidence file/crop repeated
bit-for-bit within its host. Fixed ZIP entry timestamps also make the EPUB
itself repeat with that same SHA-256 across complete matrix invocations.

All six cases agree on reader-client bounds, responsive layout mode, toolbar
density, normalized viewport, empty returned-action hash, and action count.
The narrow case additionally agrees on control and portable-semantic record
counts. Projection, detailed layout, control, draw, semantic hashes, and
reader-client pixels remain different; the other wide cases also retain the
expected record-count differences. This is a recorded mismatch baseline, not
an exact-parity claim.

Manual inspection of the fixed 1400x780 light/default crops confirms the
Stage 2A findings: re10 has its centered bounded page, reference toolbar copy,
host exit slot, and book/chrome typography, while lectern0 retains the wide
page, extra Focus control, shortened labels, different gutter glyphs, and its
current drawing/provider behavior. These are inputs to Stage 2B-1 and 2B-2;
the harness did not alter them.
