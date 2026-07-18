# Lectern0 Reader Find Slice 2: visible result-snippet context

Date: 2026-07-19

Status: implementation and acceptance candidate; local-only, unpromoted, and
unpushed

## Outcome and ownership

Lectern0 now displays and highlights `Paran` in the first Location 26 Find row
for the reported real book. The row visibly reads
`COMMAND Ganoes Stabro Paran, a`, while the parent semantic result retains the
complete Reader0 excerpt.

This is adoption of the shared Readerview0 correction, not a separate Lectern0
snippet algorithm. Reader0 owns full context, match bytes, and search/navigation.
Readerview0 owns the common one-line result geometry and selects a borrowed
natural-word slice using Lectern0's existing values-only system-UI advances.
Lectern0 retains concrete full-string measurement, rasterization, theme paint,
action execution, and native integration.

The dedicated local branch `codex/reader-find-snippet-context-slice2` starts at
Lectern0 commit `885e23f08a2419dabeadf7959cc9103d5b15453d` and pins Readerview0 API 3
exactly at `6ff78cf47258ff21f79fa3473973a85066fea899`. Reader0 remains
`3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`, UI0 remains API 91 at
`cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, and zero_foundation remains
`eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c`.

Lectern0 remains local-only with no remote. This slice does not create or
authorize a GitHub repository. No API version changes and no callback, provider
table, vtable, event bus, dependency injection, allocation, generic document
interface, or mutable process-global state is introduced.

## Exact native app regression

The exact book is
`C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub`, 955125 bytes,
SHA-256
`D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.

`--reader-view-find-snippet-context-smoke` opens the actual EPUB at 1400 by
780 with the Light theme and default size 3 / spacing 0 / family 2, opens Find,
edits and commits `Paran`, activates result index 0 through the shared semantic
action path, and verifies:

- 128 Reader0 matches and a projected first result;
- the exact complete source excerpt on the projection and semantic row;
- a UTF-8-safe natural-word child binding inside that source;
- `Paran` wholly inside the binding at remapped byte range 22..27;
- one exact host user-highlight draw in the first row;
- 586 exact decoded highlight pixels in that row; and
- a written 1400 by 780 BMP/PNG for direct visual inspection.

`scripts/win32_lectern0_find_snippet_context_smoke.ps1` hashes the book,
strict-builds with MSVC C11 `/W4 /WX`, enforces every exact dependency and the
Lectern0 architecture audit, invokes the native smoke, validates its evidence,
and converts the screenshot for inspection.

Pre-commit candidate evidence:

- summary:
  `local/validation/reader-find-snippet-context-slice2-precommit/summary.json`;
- summary SHA-256:
  `DAAA369DE87C0A8DBAA16D40713D19B951B53D76530F7A3F3A496BB2F92CB9A8`;
- screenshot:
  `local/validation/reader-find-snippet-context-slice2-precommit/gotm_paran_first_result.png`;
- screenshot SHA-256:
  `9C90E15155FBA8D22EE7239ADC948156DCEDC50B40C2C03A45F839198043BA7E`;
- visible binding: 30 bytes, match start 22, match size 5;
- highlight evidence: one draw and 586 decoded pixels; and
- direct screenshot inspection: pass.

The Re10 companion gate shows the same first-row text and the same 586 exact
highlight pixels from the same shared dependency.

## Final clean acceptance

The committed implementation candidate is
`fe8cfcae68835d2c4dd4d298115e689965609003`. Its clean-tree exact-book run is:

- summary:
  `local/validation/reader-find-snippet-context-slice2-final-clean/summary.json`;
- summary SHA-256:
  `F6C3AD48C88087E76C3504BA12537E080F1E321A9C6B4044753AFFDDCCFCA753`;
- screenshot SHA-256:
  `9C90E15155FBA8D22EE7239ADC948156DCEDC50B40C2C03A45F839198043BA7E`;
- visible binding: 30 bytes with `Paran` at remapped bytes 22..27;
- highlight evidence: one UI draw and 586 decoded pixels; and
- manifest-recorded Git status: empty.

The clean cross-host manifest is
`C:\Temp\re10_lectern_find_snippet_final_clean_20260719_0300\manifest.json`,
SHA-256
`25B5131BBEC10153543F655302E44B55DBA9ADEDFD2878F47FA3310A09B60E4F`.
All 15/15 Re10/Lectern0 cases are deterministic and decoded-pixel exact; 9
also have exact shared records and 6 retain accepted legitimate host-record
differences. The harness enforced clean trees, exact pins, and in-run strict
builds against Readerview0
`6ff78cf47258ff21f79fa3473973a85066fea899`.

The canonical zero_foundation checkout gained one clean local-only commit while
validation was running. It was neither absorbed nor modified. Final acceptance
used the dedicated exact-pin validation worktree at
`eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c`, matching this host's dependency
metadata and unchanged `origin/master`.

## Why earlier gates missed it

The 15-case cross-host matrix used short synthetic Find text whose match was
already visible. It proved decoded-pixel agreement but did not exercise a
valid match beyond the first line's word-fit boundary. Cross-host equality
also could not expose a defect shared by both hosts.

The durable missing coverage is the exact-book native workflow above plus the
Readerview0 package regression for long-prefix, UTF-8, remapped-range, and
semantic-full-text behavior.

## Scope exclusions

This slice does not alter Reader0 search/snippet generation, page Find colors,
selection, annotations, line spacing, publisher typography, empty-document
composition, host persistence, PDF support, or Kindle-style feature-gap work.
