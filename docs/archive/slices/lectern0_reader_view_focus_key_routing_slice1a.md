# Lectern0 Reader View Slice 1A: post-action horizontal key routing

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

Date: 2026-07-18

Status: promoted to local `main`; publication had not occurred at that time

## Scope and ownership

This slice restores native Left/Right page navigation in Lectern0 after a
Reader View action retains ordinary semantic focus. It is the Lectern0 host
counterpart to re10's promoted Slice 1A and does not change reader0,
zero_foundation, UI0, readerview0, dependency pins, EPUB layout, persistence,
or shared Reader View architecture.

The branch `codex/lectern-reader-view-focus-key-routing-slice1a` started at
Lectern0 commit `501a007143508fabd73a4a540f49b3fdbef78b3d`. At the time, this was
a local integration record.

Readerview0 correctly retains bounded semantic focus. Lectern0 owns the Win32
key boundary and must distinguish a control that intentionally owns horizontal
movement from an ordinary focused button or row. No callback, provider table,
vtable, event bus, dependency injection, generic document interface, hidden
allocation, or process-global mutable state is introduced.

## Reproduced cause

`lectern0_reader_view_route_keydown` previously sent every focused shared
Left/Right key to `ReaderViewInput.move_horizontal_delta`. Readerview0 API 3
consumes that value only for its focused Find/Note text inputs and Progress
slider. A Bookmark row, Note row, font control, gutter, or other ordinary
focus therefore swallowed the key before Lectern0's concrete page route could
run.

The existing `lectern0_reader_view_keyboard_input_routing_regression` encoded
that defect by expecting horizontal shared deltas for Find Clear and Next Page.
After changing those assertions to require ordinary-focus page ownership while
retaining text and Progress ownership, the starting executable failed with
`keyboard=0`. This is the focused red checkpoint for the promoted local base.

## Bounded routing contract

Lectern0 now treats horizontal movement as shared only when:

- the Find SearchBox owns focus;
- the Note TextArea owns focus; or
- the Progress slider owns focus.

All other retained shared focus runs the existing concrete reader0 page move.
Vertical semantic navigation, PageUp/PageDown range movement, Enter/Space
activation, host Exit focus, and Tab traversal remain unchanged.

The focused host regression now explicitly locks Find and Note editing,
ordinary buttons/rows, Progress, activation, and PageUp/PageDown. The canonical
Reader View smoke reports
`horizontal_routing=text_progress_or_page`.

## Exact-book app workflow

The deterministic command
`--reader-view-post-action-arrow-smoke epub-path output-prefix` opens the real
book in a persistence-disabled Lectern0 application at 1400 by 780, light
theme, text-size index 3, spacing index 0, and Georgia. It uses actual shared
controls and the production native key router to execute:

- create and activate a Bookmark row, Right to the next page, then Left back;
- create and activate a Note row, Right to the next page, then Left back;
- open Font, activate a real enabled alternate font option, Right to the next
  page, then Left back; and
- open Find, enter and commit `Paran`, press Left, type `X`, and require
  `ParaXn` without page movement.

The exact EPUB is:

- path: `<external-fixture>\gotm_new.epub`;
- size: 955125 bytes; and
- SHA-256:
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.

`scripts/win32_lectern0_post_action_arrow_routing_smoke.ps1` hashes that book,
builds with the repository's strict dependency/audit gate, runs the workflow
twice, requires ten rendered frames to repeat exactly, converts the second set
to PNG, and writes a machine-readable summary.

The initial green run reports:

- Bookmark: spine 2 byte 0 to spine 3 byte 0, then exact return;
- Note: spine 2 byte 0 to spine 3 byte 0, then exact return;
- Font: spine 3 byte 0 to spine 4 byte 0, then exact return; and
- Find: `ParaXn`, unchanged page.

All ten PNGs were inspected directly. Bookmark and Note focus remain visibly
on the activated annotation row while page content advances and returns. Font
focus remains on the toolbar control with no stale menu. The Find field shows
`ParaXn` and remains focused. No selection-menu or popup artifact is present.

## Why previous gates missed it

The 15-case cross-host matrix compares settled Reader View records and decoded
pixels. It covers focused gutter states but does not send a native Left/Right
key after an action retains a Bookmark row, Note row, or font-control focus.
The canonical Lectern0 smoke separately tested shared activation and gutter
actions, while its keyboard-routing unit asserted the wrong ordinary-focus
delta behavior.

The missing coverage was an app-level real-book chain through the actual
action, retained semantic focus, native key router, concrete page move, and
rendered result, plus negative Find/Note/Progress ownership cases. This slice
adds that coverage without claiming document-layout parity from internal
records or synthetic captures.

## Exact dependency closure

- reader0 API 3 at `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`;
- UI0 API 91 at `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`;
- readerview0 API 3 at `27e2ac64bc9db87412cf076eac313dea902792eb`;
  and
- zero_foundation at `eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c`,
  Presentation Engine API 1.

## Clean candidate closeout

The implementation, app-level gate, and initial record were committed without
history rewriting as:

- commit: `7bbdcec3e2db3abaa454120a3667b82144a280af`;
- subject: `Restore Lectern arrow routing after actions`;
- author and committer: `devze-ro`; and
- ancestry: one commit directly ahead of local main `501a0071`.

The post-commit exact-book run rebuilt the candidate with strict warnings as
errors from a clean tree and completed twice:

- summary:
  `local/validation/rv-focus1a-final/summary.json`;
- summary SHA-256:
  `D17C274C3553E4C66B684948C5EB95AC887123D25274943C711145BB414924F1`;
- executable SHA-256:
  `9C41AD0FCE29C1EB2610C3B42AB927ED941F07F1D1F30C0FBBC15BE0E881DCF0`;
- ten rendered BMPs repeated exactly across both runs; and
- each Bookmark, Note, and Font before-frame hash equals its after-Left hash,
  while each after-Right hash is distinct.

The full focused validation also passed:

- exact dependency guard and architecture audit;
- strict MSVC C11 `/W4 /WX` build;
- concrete EPUB host smoke, hash `cd460506f219d652`;
- deterministic Reader View smoke twice, hash `bbc068e6c290fee1`;
- startup press/release, host Exit, and empty-state interaction smoke;
- native MSAA accessibility smoke twice over 18 nodes;
- canonical-frame visual smoke twice, pixel hash `df02d5d2dd061128`
  and Presentation Engine hash `3a3cf46f0444a1bd`; and
- cover/inline image smoke twice, hashes `c2cca908e35dd8d1` and
  `17989905b776b21d`.

The final clean-tree re10/Lectern0 matrix completed with both hosts rebuilt
inside the run:

- manifest:
  `<historical-evidence-not-retained>`;
- manifest SHA-256:
  `698FA58018FF472713255EBD97CB633F6B63B68F5A77E8D9FE2FE77B0F87FC4D`;
- re10 head: `3e475cfaeb90f8a6c02ae7a8a887952fb1a48f2c`;
- Lectern0 head: `7bbdcec3e2db3abaa454120a3667b82144a280af`;
- 15/15 cases repeatable with exact decoded-pixel equality;
- acceptance eligibility: true; and
- no dirty-tree or skip-build diagnostic allowance.

The six pixel-exact record-difference cases are unchanged: dark Contents,
light Contents, Find, Bookmark/right panel, Annotations filter, and Note
editor. They remain legitimate host-record distinctions and are not affected
by this native key-routing correction.

## Promotion

On 2026-07-18, local Lectern0 `main` was fast-forwarded from
`501a007143508fabd73a4a540f49b3fdbef78b3d` to the
validated candidate `d4e3f4e0dfe058c4fe1dd3f5037a0b1cf82469ca`. This
promotion record is the only post-validation change and is itself applied by a
second fast-forward. No merge commit, rebase, reset, amendment, replay, or
history rewrite was used.

Repository publication occurred later. This closed the slice as promoted local
work.
