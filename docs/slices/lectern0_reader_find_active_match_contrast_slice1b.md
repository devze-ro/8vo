# Lectern0 Reader Find Slice 1B: perceptible active-match distinction

Date: 2026-07-18

Status: implementation candidate; unpromoted; Lectern0 has no remote

## Scope and ownership

This slice makes the active EPUB Find match perceptibly different from other
visible matches in Lectern0. It does not change Reader0 matching or identity,
snippets, page geometry, Reader View architecture, persistence, dependency
pins, or any dependency repository.

The dedicated branch `codex/reader-find-active-contrast-slice1b` starts at the
local-only promoted Lectern0 commit
`8a6652eab6da1be53ca1e58ad95c423f34912b81`. Lectern0 remains local-only;
no remote or GitHub repository is created.

Reader0 already marks the correct active match, and Lectern0 already renders
inactive ranges first and the active range last. Readerview0 owns shared Find
interaction state and chrome, not EPUB page-content colors. UI0's generic
theme profiles also exclude product/domain Reader colors. Lectern0 therefore
retains its explicit host Reader-content theme and applies the same bounded
palette policy as re10 without adding a shared callback or provider boundary.

No callback, provider table, vtable, event bus, dependency injection, generic
document interface, hidden allocation, or process-global mutable state is
introduced.

## Exact real-book workflow

The exact private EPUB is:

- path: `C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub`;
- size: 955125 bytes; and
- SHA-256:
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.

The new command
`--reader-view-find-active-contrast-smoke epub-path output-prefix` creates a
1400 by 780 persistence-disabled app, opens the shared Find control, enters and
commits `Paran`, and activates semantic result row 2 through the actual Reader
View action path. The resulting page contains one active and one inactive
match. For each supported theme the command verifies range identity, inactive
before active draw order, exact host palette colors, exact rendered pixels,
and writes a BMP.

## Palette and acceptance

Inactive fills remain unchanged. The active fill is `#FFD166` for light,
coral-light, and blue-light, and `#705E18` for dark, coral-dark, and blue-dark.
The wrapper requires active/inactive OKLab distance of at least 0.10 and Reader
text contrast on the active fill of at least 4.5:1.

The six measured distances range from 0.109 to 0.181 and text contrast ranges
from 5.39:1 to 12.17:1. This corrects the technically different but
imperceptible pairs inherited from the frozen palette without changing the
inactive-match appearance.

## Rendered evidence

`scripts/win32_lectern0_find_active_contrast_smoke.ps1` hashes the exact book,
runs the strict dependency/build gate, invokes the real workflow, checks the
quantitative thresholds, converts all six captures to PNG, and writes a JSON
summary.

The initial green summary is
`local/validation/reader-find-active-contrast-slice1b/summary.json`, SHA-256
`E8A0EDFA8CF55DACE6C771216BB08132043B8665846FAA14F5D9019211B41440`.
Every theme reports one active and one inactive range and draw, with exact
rendered pixels for both fills. All six Lectern0 captures and all six matching
re10 captures were inspected directly; the active match is clearly visible in
every light and dark profile.

## Why prior gates missed it

The 15-case matrix established cross-host pixel parity for its synthetic Find
state. It did not establish that the shared weak palette was usable, did not
measure active/inactive perceptual distance, and did not exercise the exact
real-book result-selection workflow in all six themes. Because both hosts
rendered the same low-distinction colors, pixel equality could pass while the
human-visible distinction failed.

The missing regression is an app-level exact-book workflow with simultaneous
active and inactive page matches, semantic result activation, draw-order and
pixel checks, six-theme quantitative thresholds, and direct visual review.

## Dependency and diagnostic closure

The exact dependencies remain:

- Reader0 API 3 at `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`;
- UI0 API 91 at `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`;
- Readerview0 API 3 at `27e2ac64bc9db87412cf076eac313dea902792eb`;
  and
- zero_foundation at `eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c`,
  Presentation Engine API 1.

Completed before the candidate commit:

- exact dependency guard and architecture audit;
- strict MSVC C11 `/W4 /WX` Lectern0 build;
- generated-fixture Reader View smoke, repeat 2, hash
  `bbc068e6c290fee1`;
- concrete generated host smoke, hash `cd460506f219d652`;
- exact-book six-theme app-level smoke and direct screenshot review; and
- diagnostic 15-case cross-host matrix with all cases deterministic and
  pixel-exact.

The standard synthetic Reader View smoke remains fixture-specific and passes
with its canonical generated EPUB. Supplying `gotm_new.epub` to that unrelated
fixture gate violates its fixed gutter expectation; real-book Find coverage is
provided by the new dedicated gate.

Clean candidate commits and clean-tree closeout evidence are recorded below
after they exist. Nothing in this record authorizes local-main promotion, push,
remote creation, or history rewriting.

