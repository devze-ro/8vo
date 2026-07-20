# Lectern0 publisher typography parity slice 4

Date: 2026-07-19

## Scope and ownership

This slice restores the Lectern0 host adapter at the concrete Reader0 frame to
Text Engine/render boundary. It does not move publisher-style ownership out of
Reader0, add a shared document abstraction, or change Reader View chrome.

Reader0 already supplied the required concrete data in
`EpubReaderFrameStyleRow` and `EpubReaderFrameStyleFragment`: block and inline
style flags, publisher family/face, scale, color, text alignment,
`block_last_row`, margins, indentation, and line-height inputs. Lectern0 was
discarding the inline fragments and painting, measuring, hit-testing, and
selecting each row with only the block style. It also projected justify records
without distributing the row slack in the concrete painter.

The spacing action path remained host-owned and structurally intact:
`ReaderViewAction_SelectSetting` updates the Lectern0 setting, rebuilds the
Reader0 pagination inputs, and persists the selected value. The missing gate
had allowed its rendered effect and persistence to go unverified with a real
book.

## Baseline and exact dependencies

The dedicated worktree and branch are:

- branch: `codex/lectern-publisher-typography-parity-slice4`;
- base: `ea4679b8653f86b47c6f8652fd4246a475ce60de`;
- implementation: `83e9f5a151b123e68670ae430598ede58aa89637`;
- Reader0: `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`;
- UI0: `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`;
- Readerview0: `26d836390fce2de64198430fa82d6f660fc7fc07`;
- exact Lectern0 zero_foundation pin:
  `eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c`.

The canonical zero_foundation repository had independently advanced to
`3eab21c06c4aa0b4915f9e7fcb3830ba1688451f`; this slice continued to build
against the exact Lectern0 dependency pin rather than absorbing that unrelated
advance.

The final fresh-fetch gate found Re10 at
`4d197e23cdaea8b9979c064593fbf8210ffd2d1d`, equal to `origin/main`, clean,
and 0/0 divergent. Reader0, UI0, Readerview0, and zero_foundation were also
clean and 0/0 divergent from their upstreams. Re10 retained the expected HTTPS
origin `https://github.com/devze-ro/re10.git`. The protected extraction
worktree remained clean on `codex/reader-core-extraction-slice3` at
`420068fc1509fcf9c9b9eae7f4dcacfe2e046f54`.

## Exact-book reproduction

All real-book work used:

- path: `C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub`;
- size: 955125 bytes;
- SHA-256:
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.

At 1536 by 912, light theme, Georgia, default text size, and the Prologue page
containing `1154th Year of Burn's Sleep`, the pre-fix Lectern0 rendering showed:

- the publisher-emphasized date lines in an upright face;
- ordinary prose with ragged row endings where the equivalent Re10 rows were
  justified; and
- insufficient observable proof that all three spacing choices changed the
  final row geometry and survived navigation/restart.

The book's exact text says `1154th Year`; the earlier reported `1161st Year`
belongs to a different book revision or location and was not used as a
book-specific match condition.

## Implementation

Lectern0 now adapts each Reader0 style row into bounded
`TextEngineDisplaySpanRow` geometry. App-owned fixed-capacity arrays hold the
display spans, grapheme stops, and resolved span styles; the change introduces
no allocation, callback table, vtable, provider registry, dependency injection,
or process-global mutable state.

The adapter:

- combines block and inline style flags with the existing underline and caps
  override semantics;
- resolves per-fragment scale, publisher family/face, and color;
- uses the same shaped span stops for painting, hit-testing, selection,
  highlights, search highlights, and note markers;
- applies the same bounded paragraph/blockquote justification eligibility and
  slack caps used by the Re10 benchmark path; and
- keeps ordinary non-justified spans as one shaped run, splitting at spaces
  only when justification actually distributes slack.

That last rule was discovered by the cross-host reconciliation. An initial
diagnostic matrix was deterministic but differed in all 15 cases: the default
synthetic rows were `Default/Left` in both hosts, yet Lectern0 split every row
at every space and caused DirectWrite to reshape word runs independently. The
correction restored ordinary one-run shaping while retaining space splitting
for genuinely justified rows.

## Exact-book application regression

`scripts/win32_lectern0_publisher_typography_spacing_smoke.ps1` guards the
private fixture identity, performs a strict MSVC C11 `/W4 /WX` build, executes
the real Reader View setting action for all three choices, and captures decoded
rendered evidence at 1536 by 912.

Final summary:

- path:
  `C:\Users\ankur\Documents\Codex\2026-07-18\please-resume-the-epub-reader-regression\artifacts\typography_slice4_final2\summary.json`;
- SHA-256:
  `6819BF2E69E9580F607450E4B2340799FAB82302B1944CE82759126D2ECEF240`;
- result: pass;
- italic publisher fragment rendered in all three settings;
- justified rows rendered in all three settings;
- line heights: 26, 31, and 36 pixels;
- presentation hashes:
  `7a1862a2b097c38a`, `b788d73942ad2d31`, and
  `fd79d74ca5be45fe`;
- page navigation persistence: pass; and
- restart persistence: pass.

The directly inspected PNGs are:

- Compact:
  `C:\Users\ankur\Documents\Codex\2026-07-18\please-resume-the-epub-reader-regression\artifacts\typography_slice4_final2\gotm_spacing_0.png`,
  SHA-256
  `DD324B254C710DB229E8C423C2593CE1957AB940703BC3D68B2E8EA535677B5E`;
- Comfortable:
  `C:\Users\ankur\Documents\Codex\2026-07-18\please-resume-the-epub-reader-regression\artifacts\typography_slice4_final2\gotm_spacing_1.png`,
  SHA-256
  `BB690C94488B2905823E4FCB64E94B86A4478E7D7BDDDFC15BF4B1BE73B09ECE`;
- Spacious:
  `C:\Users\ankur\Documents\Codex\2026-07-18\please-resume-the-epub-reader-regression\artifacts\typography_slice4_final2\gotm_spacing_2.png`,
  SHA-256
  `C759C0D6ED9B1941E830AA04760B493EE997F169E4BF73D86A6A7530BA90CD09`.

Inspection confirmed the italic date lines, justified prose, visibly distinct
row spacing, and fewer visible rows as spacing increases.

## Regression and architecture evidence

The final strict build passed the exact dependency guards and Lectern0
architecture audit. The final rebuilt executable also passed:

- complete Reader View smoke, repeat 2, stable hash
  `bbc068e6c290fee1`;
- post-action arrow workflow, repeat 2, summary SHA-256
  `81417B9881439B3B63003DB8C134C25ABF64F7C8C2EDB5504ADFC99F7ECF6A76`;
- six-theme active Find contrast, summary SHA-256
  `952503925E3DCAAFD20407479C5932D0321F2CE51E6C3C7B259B37D50E81C56B`;
- exact-book Find snippet context, summary SHA-256
  `808DDF6F9AED7D8BB7E084C5ADA35FF3EEA38D4FE05FD61E3E10A66DA11F2A7C`;
- selection geometry/menu recovery, repeat 2, summary SHA-256
  `FD39746FC96E4800ADE62B0478418C46B07FE2979B272C9EBE45A3F669A24A47`;
- synthetic cover/inline image decoding, repeat 2.

The clean 15-case Re10/Lectern0 matrix rebuilt both hosts in-harness at the
exact commits above:

- manifest:
  `C:\Users\ankur\Documents\Codex\2026-07-18\please-resume-the-epub-reader-regression\artifacts\typography_slice4_cross_acceptance\manifest.json`;
- manifest SHA-256:
  `B7FF27B181D05CB3DE860434CBA02C628FE50E98545BDB4332BC7AFF8AB7C616`;
- deterministic: 15/15;
- decoded-pixel exact: 15/15;
- exact records: 9/15, with the six already legitimate host-record
  differences remaining pixel-exact;
- clean-tree enforcement: pass;
- in-run builds: pass;
- exact dependency enforcement: pass; and
- acceptance eligible: true.

The frozen 28-case Re10 reference matrix was not rerun because this slice does
not change Re10 or any of its dependencies. The affected cross-host matrix and
all Lectern host workflows were rerun.

## Why prior gates missed the failures

The 15-case matrix uses unstyled synthetic paragraphs whose default alignment
is left. It exercised shared chrome and deterministic page pixels but contained
no real inline emphasis fragment and no publisher-justified prose. The image
and row records could therefore agree while Lectern0 discarded the concrete
style fragments and failed to distribute publisher alignment.

The existing setting regression checked Reader View records rather than the
complete action to host update to Reader0 rebuild to rendered-geometry path.
The new exact-book gate covers that boundary and verifies both navigation and
restart persistence.

## Deferred real-book cover and map sizing

The newly reported small cover and maps were reproduced separately in Lectern0
with the same book and 1536 by 912 viewport. The decoder is healthy; the fault
is later, in Lectern0's host presentation geometry.

`lectern0_resolve_presentation_image_box` applies the publisher
`display_w_px` as a hard width ceiling and clamps every desired image height to
320 pixels, including image-only cover and map pages. This produces the small
centered boxes seen in Lectern0 while Re10 uses the available page content area
for those image-only pages.

This slice deliberately does not alter that boundary. The next bounded media
slice should distinguish image-only page media from in-flow publisher images,
fit the former to the available page content while preserving aspect ratio,
retain publisher dimensions for true inline content, and capture the exact
cover and every map page side-by-side with Re10. The existing synthetic image
smoke missed the defect because it proves decoding, cache behavior, and
determinism, not real-book page-fit scale or cross-host rendered size.

## Promotion

The user explicitly authorized promotion on 2026-07-19. Canonical Lectern0
`main` was fast-forwarded from
`ea4679b8653f86b47c6f8652fd4246a475ce60de` through the validated
implementation and evidence commits. This promotion record is documentation
only and is fast-forwarded on top of that approved tip.

Lectern0 remains local-only with no remote configured. No repository was
created and no push, merge commit, rebase, reset, amendment, cherry-pick, or
history rewrite was performed.

## 2026-07-20 regression addendum: font and pagination parity

After the library and shared navigation-preparation adoption, the exact
`gotm_new.epub` acceptance book reproduced two related Lectern0 regressions:

- choosing the same named font in Re10 and Lectern0 did not produce the same
  concrete font inputs because Lectern0 continued to permit the publisher's
  embedded face after an explicit user font-family selection; and
- the visible text bottom edge varied substantially by font, with Palatino
  leaving about 90 pixels of artificial empty space above the progress area.

The second defect was a host presentation error rather than a Reader0
pagination error. Reader0 uses `line_height_permille == 0` to mean the normal
1000-permille line height. Lectern0 instead passed that zero through a
minimum-of-one calculation before applying its pixel clamp. For Palatino,
Reader0 paginated at 31 pixels while Lectern0 presented each row at 26 pixels.
The five-pixel discrepancy accumulated over 18 rows and created the apparent
font-dependent lower boundary.

The bounded repair keeps ownership unchanged:

- Lectern0 now interprets an unspecified row height as 1000 permille before
  presentation scaling, matching Reader0's canonical meaning;
- publisher top and bottom margin units derive from the unexpanded base font
  line height, so user row spacing no longer inflates publisher margins;
- an explicit Lectern0 font-family choice is persisted as a user override and
  disables publisher-embedded fonts in the Reader0 typography inputs;
- the Lectern0 settings file advances to version 3, while version 1 and 2
  24-byte records migrate to the pre-existing explicit-host-font behavior; and
- adjacent-page cache identity includes the embedded-font policy so a font
  policy transition cannot reuse stale presentation data.

Lectern0 intentionally retains its standalone full-window reader viewport.
Re10 places the Reader destination inside a shell-owned ten-pixel inset. Copying
that Re10 shell inset into Lectern0 would make one text rectangle identical at
1536 by 912, but it also reduces the canonical image capacity and regresses the
frozen cover/map media box from 556 by 468 to 556 by 442. Host-specific shell
composition therefore remains outside Reader0 and Readerview0; parity is
defined by shared typography semantics for equivalent content geometry, not by
forcing the two hosts to have identical outer chrome.

The extended exact-book regression now exercises the real font-family action,
version 3 restart persistence, version 2 migration, all locally available
families, line-height and margin geometry, page ranges, row counts, and decoded
render evidence. Final pre-commit evidence at 1536 by 912:

- summary:
  `local\v\typefinal4\summary.json`;
- summary SHA-256:
  `20BC99E4C8BE500B088B0C8E3EFCF63616609692127C24CD55906D242588674C`;
- exact-book identity:
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`;
- line heights: 26, 31, and 36 pixels;
- margin units: 1000, 839, and 722 permille;
- publisher vertical margin: 52 pixels for all three spacing choices;
- Palatino: 18 rows, range 0 through 873, zero-pixel bottom gap;
- Georgia and Times New Roman: six-pixel bottom gaps;
- Book Antiqua: 44-pixel bounded carry gap, below the two-line acceptance
  ceiling and attributable to canonical row/page carry rather than mismatched
  presentation geometry;
- explicit family override persisted: pass; and
- embedded publisher fonts disabled after explicit selection: pass.

The retained image-only exact-book regression was rerun after rejecting the
shell-inset experiment. Cover plus three maps remain 4/4, repeat 2/2, with the
frozen 556 by 468 media geometry. Physical held-key navigation performance is
not altered by this typography repair and is handled in a separate bounded
Lectern0 slice.
