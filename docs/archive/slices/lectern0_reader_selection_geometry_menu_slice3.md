# Lectern0 Reader selection geometry and menu Slice 3

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

Date: 2026-07-19

Status: validated local reconciliation; subsequently promoted

## Scope and ownership

This slice adopts Readerview0's recovered compact selection menu and fixes the
two Lectern0 host boundaries that prevented it from working: selection-release
lifecycle routing and concrete page-text geometry. It does not change
publisher typography, justification, italics, row-spacing policy, Reader0, or
zero_foundation.

Readerview0 remains responsible for the bounded popup, shared interaction
state, semantics, and actions. UI0 remains responsible for the swatch-strip
drawing records and focus/selected/remove visuals. Reader0 owns the concrete
selection. Zero Foundation owns the allocation-free display-row source/x
mapping mechanism. Lectern0 constructs the display-row input from exactly the
block-style text it currently renders and executes the resulting actions.

The current host still draws each row with its block style rather than inline
publisher style fragments. Migrating body drawing and geometry together to
span rows belongs to the later publisher-typography slice; no italics or
justification workaround is folded into this one.

No callback, provider table, vtable, event bus, dependency injection, generic
document interface, hidden allocation, or process-global mutable state is
introduced. The app owns a fixed 1024-stop scratch array.

## Commits

The dedicated branch `codex/lectern-selection-geometry-menu-slice3` starts at
local main commit `44f2dee48780296db9e6674e754c583114fdd83a`.
The code was committed without amendment or history rewriting as:

1. `843869e749290f5ac70ad5bbc2fad548415f8852`,
   `Fix Lectern selection geometry and menu lifecycle`;
2. `bef5f9c40e6a8a82ca7cb53ffbd8821252e0e164`,
   `Preserve exact note marker anchoring`.

Both commits use `devze-ro` as author and committer.
Repository publication occurred later as separate project work.

## Reproduced causes

The supplied Lectern0 screenshot was assigned during live reproduction. The
menu briefly appeared and disappeared on button release because the host:

1. finalized Reader0 selection during `WM_LBUTTONUP`;
2. built SelectionTools on the next paint; and
3. replayed that same pointer-release into the newly created popup, which UI0
   correctly interpreted as an outside release.

Selection hit testing divided x proportionally across the Presentation row's
content rectangle, while selection fills measured prefixes separately. That
made selection identity and rendered rectangles disagree with the glyphs the
host actually drew.

## Implementation

Lectern0 now:

- withholds the Reader View selection projection while a concrete drag is in
  progress;
- marks the release that finalized selection and suppresses only that release
  from the newly created shared popup;
- builds bounded `TextEngineDisplayRow` stops at UTF-8 grapheme boundaries
  using the same family, face, scale, alignment, and block-style measurement
  as the current text draw;
- maps pointer x to concrete Reader0 source bytes through the Zero Foundation
  display-row helper;
- derives active-selection and retained-highlight rectangles from the same
  source/x mapping;
- keeps zero-length note-marker anchors at the exact caret x rather than using
  the display helper's minimum 1 px range rectangle; and
- clears concrete Reader0 selection before forwarding Escape from
  SelectionTools.

The shared pin is exactly
`26d836390fce2de64198430fa82d6f660fc7fc07`, Readerview0 API 3. Each swatch is
given the host's resolved theme-aware highlight color.

## Exact-book regression and rendered evidence

The exact file is:

- `<external-fixture>\gotm_new.epub`;
- 955125 bytes; and
- SHA-256
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.

`scripts/win32_lectern0_selection_menu_recovery_smoke.ps1` runs the actual
host press-drag-release workflow twice. It selects rows 14 and 15 at source
bytes 649..747, verifies both rendered rounded rectangles against exact
glyph-stop range rectangles, proves the selection release is not replayed,
checks the compact/clamped shared menu, applies Pink by pointer, removes the
selected Pink highlight by keyboard, and verifies Escape cleanup.

The clean post-commit summary is:

- path:
  `local/validation/reader-selection-menu-slice3-clean/summary.json`;
- SHA-256:
  `94A9AA3E56BB02B23CF6CB6CB399DAC12FF1F788895F46F784D87314D5FED6A4`;
- executable SHA-256:
  `1345D53BDBD363912D43EFACE88693A25EDD3456FBAB8D13D0B9B91497E98C23`;
- source: clean `bef5f9c40e6a8a82ca7cb53ffbd8821252e0e164`;
- light multi-line BMP SHA-256:
  `FB8587A30EF4AE5016EDCA2D3214DAE8A9BEB4EB42C5DEBC6943D2D91308B572`;
- dark selected/focused BMP SHA-256:
  `4F38A3C584556C8DA8073E7D2F4B2E668D7072D7FC5F534659C0BEF3C5679CDE`;
- repeatability: 2/2; and
- source worktree status in the summary: clean.

Both rendered images were inspected directly.

## Computer Use verification

The final worktree binary was launched with the exact book and controlled
through the Windows Computer Use surface. The live workflow verified:

- a two-line selection across shaped text;
- a single-line selection ending in the middle of a row;
- exact visible fill endpoints;
- popup persistence after release;
- above-anchor placement near the lower page region;
- Tab focus on Pink with a visible ring;
- Return activation, selected-swatch/remove overlay, and `Edit note` state;
- Escape dismissal and restoration of the normal Reader focus tree; and
- removal of the temporary test highlight before the test window closed.

## Matrix-discovered zero-length edge case

The first clean 15-case run at Lectern0 commit `843869e7` was individually
repeatable but differed in the two annotation cases:

- manifest:
  `<historical-evidence-not-retained>`;
- SHA-256:
  `AEE0A3ADDE38FD67014BA95C2017559BC8D1106D67EFA4FA5339E4E44D4BB323`.

The difference was exactly 20 swapped pixels in bounds 536,201..543,208: the
same 7×8 note marker shifted one pixel right. The highlight fill itself was
exact. The cause was using `range_rect.x + range_rect.w` for an empty range,
where the product-neutral helper intentionally returns a minimum width of one.
The follow-up commit uses the exact caret x for zero-length anchors.

The fresh clean retry then passed:

- manifest:
  `<historical-evidence-not-retained>`;
- SHA-256:
  `D4E35D5EE62548B51E12DBEDFB9FB5BDE69C00875B54D9119A2B09D8C04A5955`;
- Re10 `c61c24726d5893dec15706151c4e6cad55290a2d`;
- Lectern0 `bef5f9c40e6a8a82ca7cb53ffbd8821252e0e164`;
- Readerview0 `26d836390fce2de64198430fa82d6f660fc7fc07`;
- 15/15 deterministic, exact decoded-pixel parity; and
- clean-tree, in-run-build, exact-dependency acceptance eligibility: true.

## Why prior gates missed it

The existing matrices never performed an actual page pointer drag or opened
SelectionTools. Their annotation cases created a selection through commands,
immediately converted it to a stored annotation, and captured shared chrome.
Internal Reader View selection records could therefore agree while the host's
rendered selection rectangle and release lifecycle remained broken.

The added gate covers the missing concrete boundary: pointer-to-source
mapping, single/multi-row rendered rectangles, release sequencing, lower-edge
clamping, pointer and keyboard actions, selected action identity, and Escape.

## Verification

The final commits pass:

- strict MSVC C11 `/W4 /WX` builds;
- exact Reader0, UI0, Readerview0, and zero_foundation dependency guards;
- Lectern0 architecture audit;
- startup interaction and repeatable Reader View host suites;
- the repeatable exact-book selection-menu regression;
- direct rendered-image and Computer Use inspection; and
- the final 15-case clean cross-host matrix.

## Reconciliation with Re10 Reader lifecycle

Before this slice was promoted, canonical Re10 intentionally replaced its
host-owned Exit Reader/presentation action with the explicit Close Book action.
The cross-host visual harness still expected enum value 1
(`TogglePresentationMode`) at that root slot, so it stopped before capturing
the final four cases even though neither host's shared layout had changed.

The dedicated branch
`codex/selection-menu-lifecycle-harness-reconcile-slice3` keeps all production
code at `cab1d5bb3dce178946442683593b9c0fa339578e` and changes only the Re10
focus expectation in `scripts/win32_reader_view_stage2b0_parity.ps1`:

- harness commit: `e4a0aa48ba9383c162a1b7367c160b0463389a99`;
- Re10 uses `UISourceReaderControlAction_CloseDocument` value 37;
- Lectern0 retains its product-specific Exit Reader focus request; and
- the visual root position and focus ring remain exact across hosts.

The clean reconciled matrix rebuilt both applications and passed all 15 cases:

- manifest:
  `<historical-evidence-not-retained>`;
- manifest SHA-256:
  `111252B8F71A232D42557FFC77060BC8E423B763EF6276FD5DF52E992E7482A0`;
- Re10: `606c1d5274943e19458543a80be30b09662c88cb`;
- Lectern0: `e4a0aa48ba9383c162a1b7367c160b0463389a99`;
- Readerview0: `26d836390fce2de64198430fa82d6f660fc7fc07`;
- deterministic exact decoded-pixel parity: 15/15; and
- clean-tree, in-run builds, exact dependencies, and acceptance eligibility:
  true.

The validated changes were promoted locally at the time; repository publication
occurred later.
