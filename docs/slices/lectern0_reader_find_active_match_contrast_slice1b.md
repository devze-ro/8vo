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

The final candidate adds a distinct `search_hit` field to that host content
theme. Inactive Find rendering no longer reuses `selection`; the existing text
selection value and draw path remain unchanged.

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

Direct review of the first candidate led to a user-directed semantic model:
active uses a theme-primary shade, inactive uses a neutral dormant shade, and
selection remains separate. The exact six-theme values are recorded in re10's
paired Slice 1B record and are identical here.

The wrapper requires active/inactive OKLab distance of at least 0.12,
active/selection distance 0.08, inactive/selection distance 0.05,
inactive/page distance 0.075, active hue within 25 degrees of primary, active
chroma at least 0.10, inactive chroma at most 0.02, and text contrast on both
Find fills of at least 4.5:1.

Measured active/inactive distance is 0.128 to 0.194, active hue distance is
0.97 to 23.99 degrees, active chroma is 0.101 to 0.144, inactive chroma is
0.004 to 0.019, and text contrast is 4.68:1 to 12.50:1.

## Rendered evidence

`scripts/win32_lectern0_find_active_contrast_smoke.ps1` hashes the exact book,
runs the strict dependency/build gate, invokes the real workflow, checks the
quantitative thresholds, converts all six captures to PNG, and writes a JSON
summary.

The historical first-pass amber/olive summary is
`local/validation/reader-find-active-contrast-slice1b/summary.json`, SHA-256
`E8A0EDFA8CF55DACE6C771216BB08132043B8665846FAA14F5D9019211B41440`.
It is superseded by the primary/neutral model.

The final pre-commit local summary is
`local/validation/reader-find-primary-neutral-slice1b-final-local/summary.json`,
SHA-256
`263D322C25F0A9526033A1EB8C528FF4CFD68C012B19C1DDE325223F77491C29`.
Every theme reports one active and one inactive range/draw with exact rendered
pixels. The CLI regression additionally requires zero selection-colored page
draws and pixels while Find is active. All six Lectern0 and six re10 captures
were inspected directly.

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

## Clean candidate closeout

The implementation, exact-book gate, and initial record were committed without
history rewriting as:

- commit: `4a69da4aa4bd58e1a3de2b30fbc3d5ec100ad230`;
- subject: `Strengthen Lectern active Find highlights`;
- author and committer: `devze-ro <devze_ro@outlook.com>`; and
- ancestry: one commit directly ahead of local-only promoted `8a6652ea`.

The post-commit exact-book run rebuilt Lectern0 with its strict dependency and
architecture gate, then passed all six themes:

- summary:
  `C:\Temp\lectern0_find1b_exact_clean_20260719\summary.json`;
- summary SHA-256:
  `674123758BAC869B228B88E1B6EFF3D6A9910CD47271DD568E1CF5F276D6FFCE`;
- executable SHA-256:
  `FA3AA6A24751D7B07BD7643D11B0312D11B163DB4D49F96DB2D6B6AD33A52F4C`;
- result line:
  `lectern0_reader_view_find_active_contrast result=pass checkpoint=5 query=Paran active_index=2 themes=6`; and
- every theme retained one active and one inactive range/draw with exact
  rendered pixels and passed the OKLab/text-contrast floors.

The final clean-tree re10/Lectern0 matrix rebuilt both implementation commits:

- manifest:
  `C:\Temp\re10_lectern_find1b_15_clean_20260719_retry1\manifest.json`;
- manifest SHA-256:
  `25B7E2341028AB7D97940B9853051282458396321A0727AF7652223511A1B48A`;
- re10 commit: `6d5756585ff9fc0c5806f384fd6e585a3a0bc74e`;
- Lectern0 commit: `4a69da4aa4bd58e1a3de2b30fbc3d5ec100ad230`;
- 15/15 cases deterministic with exact decoded-pixel parity;
- both hosts rebuilt inside the run from the exact dependency pins; and
- acceptance eligibility: true.

The six pixel-exact record-difference cases are unchanged: dark Contents,
light Contents, Find, Bookmark/right panel, annotation filter popup, and Note
editor. This slice changes neither their classification nor shared semantic
records.

The first clean matrix attempt recorded a single nonrepeatable re10 Contents
progress-control draw, outside this slice and absent on the fresh full retry.
The Find case was deterministic and pixel-exact in both attempts. Both
manifests are retained in the re10 record for transparency.

This evidence closes the superseded amber/olive checkpoint. The final
primary/neutral refinement is recorded below.

## User-directed primary/neutral refinement

The refinement adds one explicit host-owned inactive Find value, keeps all
selection values unchanged, and uses theme-primary active values. Reader0
continues to publish active identity and Readerview0 continues to own bounded
Find actions; neither dependency changes.

Strict compilation, dependency and architecture gates, the exact-book
six-theme workflow, generated host smoke, deterministic Reader View smoke, and
direct twelve-image review pass at the pre-commit checkpoint.

The refinement was committed without history rewriting as:

- commit: `2a9d2e87302c4bf05f92b17dbbc1a690f826f15c`;
- subject: `Separate Lectern active and dormant Find fills`; and
- author and committer: `devze-ro <devze_ro@outlook.com>`.

The clean post-commit exact-book gate rebuilt that commit and passed all six
themes:

- summary:
  `C:\Temp\lectern0_find1b_primary_neutral_clean_20260719\summary.json`;
- summary SHA-256:
  `4932FC4CBEAF9CB258533B516C484D88B35C1516839DE0D5E3E9415FC7C7BED8`;
- executable SHA-256:
  `0A79D2FDC73696D1E19030DE38CBB31239BAB236F8ACAF84C03CB774A213D3A1`;
- result line:
  `lectern0_reader_view_find_active_contrast result=pass checkpoint=5 query=Paran active_index=2 themes=6`; and
- every theme retained exact active and inactive page pixels with zero
  selection-color Find draws or pixels.

The clean 15-case re10/Lectern0 matrix rebuilt both refinement commits:

- manifest:
  `C:\Temp\re10_lectern_find1b_primary_neutral_15_clean_20260719\manifest.json`;
- manifest SHA-256:
  `D63F70577210F6FDA3AA36394D071FB4A3068CC57E690289791066653092DB0C`;
- re10 commit: `733c45f0672f015fbe0d63ca215b6458e990b778`;
- Lectern0 commit: `2a9d2e87302c4bf05f92b17dbbc1a690f826f15c`;
- result: 15/15 deterministic with exact decoded-pixel parity; and
- clean-tree, in-run-build, exact-dependency acceptance eligibility: true.

This is the final validated, unpromoted local-only candidate. Lectern0 still
has no remote, and nothing in this record authorizes local-main promotion,
remote creation, or history rewriting.

## Reconciled re10 cross-host evidence

Re10's Find slice was subsequently replayed, with explicit user authorization,
onto canonical re10 editor head
`9a695470681190f7605f42a8711d8a586137ab2f`. The reconciled tested re10 commit
is `228a38c49768fedae4beaf8c648db2ba2778e11f`. Lectern0 required no replay or
code change and remained at tested commit
`22ddc6f154d0e51b3e96ceb24b1f51cb1e6077ee`.

The clean cross-host harness rebuilt those exact tips:

- manifest:
  `C:\Temp\re10_lectern_find1b_primary_neutral_reconciled_15_clean_20260719\manifest.json`;
- manifest SHA-256:
  `7B2BEFEE6C1DA46E939107CB908CCB7E7B2683AC5AD881F50CB1B385CB8D5670`;
- result: 15/15 deterministic with exact decoded-pixel parity; and
- clean-tree, in-run-build, exact-dependency acceptance eligibility: true.

The reconciliation therefore preserves Lectern0 parity with the combined
editor-plus-Reader re10 candidate. Lectern0 remains local-only and unpromoted.
