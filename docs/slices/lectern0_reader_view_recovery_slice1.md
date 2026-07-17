# Lectern0 Reader View recovery Slice 1

Date: 2026-07-17

Status: implemented and validated locally; not committed or promoted

## Reference lock

The accepted re10 reader at
`a6b1555ecb39c4948c735decda02cdc5a71f452c` is the directional product
reference. Cross-host equality is not sufficient: extracted re10 must preserve
that reference before lectern0 parity can be accepted. This slice does not
redesign the Reader View or revert its extraction.

Lectern0 now pins readerview0 API 2 recovery commit
`7ff4ffdeec3c22746719c564f53c80e20585e1a9`, which preserves control state
across repeated zero-document frames so a native press followed by a native
release can emit `ReaderViewAction_Open`.

## Host recovery

Readerview0 is the single owner of projected empty, loading, unavailable, and
error status presentation. Lectern0 continues to paint the host-owned page
surface, but no longer paints a second `Open an EPUB to begin reading.` string
over the shared empty-state message.

The Win32 `WM_LBUTTONDOWN` and `WM_LBUTTONUP` paths now use the same bounded
host pointer-transition helpers as the executable recovery regression. The
new `--reader-view-startup-interaction-smoke` command starts with no document,
builds the real shared chrome, targets the semantic Open button, and requires:

- the press frame to retain that exact control ID as `active_id`;
- no Open action on the press frame;
- the release frame to return `ReaderViewAction_Open`;
- the document to remain empty because the headless smoke deliberately does
  not execute the action or open a native file dialog; and
- exactly one rendered empty-state status message.

`scripts/win32_lectern0_reader_view_startup_interaction_smoke.ps1` exposes that
contract as a standalone executable regression.

## Parity gate hardening

The two-host parity runner now treats any normalized decoded-pixel mismatch as
a hard failure after retaining its manifest and report for diagnosis. Internal
projection, control, draw, or semantic record-hash differences remain
diagnostic and non-fatal because legitimate host-owned identities and
lifetimes may differ.

Lectern0's six parity capture scenarios also assert their requested settled
postconditions before evidence is written: left-panel mode, right-panel state,
font-menu kind, committed Find query with results, and bookmark creation.
Merely locating and pressing a named control can no longer make a scenario
pass.

## Validation

Validated against:

- reader0 API 3 at `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`;
- UI0 API 90 at `fda99de484d50f1b019b1edfe3489f57fae57f9a`;
- readerview0 API 2 recovery at
  `7ff4ffdeec3c22746719c564f53c80e20585e1a9`; and
- zero_foundation at `eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c`.

The exact dependency guard, architecture audit, and strict MSVC C11 `/W4
/WX` build pass. Focused results:

- empty-document native-like press/release: pass, Open action on release;
- live Win32 startup acceptance: one empty-state status message, and a normal
  Open-button click displayed the native EPUB file picker;
- Reader View smoke: repeatable hash `6ccb4eaac405ffb9`;
- all six lectern0 parity scenarios: settled postconditions pass;
- MSAA accessibility smoke: pass with 18 semantic nodes;
- visual smoke: repeatable pixel hash `2506739365bfdfd0` and Presentation
  Engine hash `3efb0e3f4b571715`; and
- image smoke: repeatable cover/inline hashes `a976ba0298994bd4` /
  `8a575edbd2f99725`.

The combined cross-revision integration run also passed the hardened six-case
two-host gate. Both hosts were repeatable and decoded-pixel exact in every
case. Wide and narrow default plus the font menu had matching diagnostic
records; Contents, Find, and bookmark/right-panel retained their accepted
host-record differences.

The same new evidence then failed the directional pre-extraction re10 lock in
all six mapped states, as required at this checkpoint. Current cross-host
parity is therefore intact, but broader reference restoration remains
explicitly outstanding.
