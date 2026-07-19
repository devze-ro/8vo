# Lectern0 full-page reader image-fit Slice 6

Date: 2026-07-19

## Objective and isolation

Restore the `gotm_new.epub` cover and three map pages to the large,
aspect-preserved full-page composition established by frozen Re10. Preserve
the extracted architecture and leave true in-flow publisher images unchanged.

- branch: `codex/lectern-reader-image-fit-slice6`;
- linked worktree:
  `C:\Users\ankur\Documents\Codex\2026-07-18\please-resume-the-epub-reader-regression\worktrees\lectern0-reader-image-fit-slice6`;
- base: `128ff1a6f471566b1eb31051de8ef8ebcdf0282b`;
- implementation: `1b309d0d06da194c15298d205ced74a708c84a01`;
- canonical Lectern0 `main` remained unchanged while this record was written;
  and
- Lectern0 remains local-only with no remote.

Exact dependency pins remained:

- reader0 `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`, API 3;
- UI0 `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, API 91;
- readerview0 `26d836390fce2de64198430fa82d6f660fc7fc07`,
  API 3; and
- zero_foundation `3eab21c06c4aa0b4915f9e7fcb3830ba1688451f`,
  `0.4.1-dev`, Presentation Engine API 1.

## Exact book and benchmark

Every real-book run used:

- path: `C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub`;
- size: 955125 bytes; and
- SHA-256:
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.

Frozen pre-extraction Re10 remains
`a6b1555ecb39c4948c735decda02cdc5a71f452c`. Current Re10 reference captures
were also inspected at the accepted Re10 tip
`4d197e23cdaea8b9979c064593fbf8210ffd2d1d` using the same 1182 by 713 outer
viewport, dark theme, Georgia, 22 px scale, 26 px line height, and compact
spacing. Reference BMP SHA-256 values were:

- cover:
  `1ED61EF78A4F9FBA92C288480926F508AE8FA7E2B73EAE9539A779536F4150C0`;
- map 1:
  `65F5A51745A132EF1E3B554AB423B1EECE00A5161CE181CD2A3629C332815F0D`;
- map 2:
  `4D722E2C3E6AAD228A2B85621C80AC43BD0853C674DB41506275D403C97BAD1F`;
  and
- map 3:
  `72C79BF4A0073488B714D914BF5036331D8C52318801340D3C4F787BC0F9E583`.

The broader Re10 GOTM diagnostic captured all four image pages successfully
but later stopped on an unrelated pre-existing fortification-highlight text
expectation. Those four inspected captures are reference evidence, not a claim
that the unrelated full diagnostic passed.

## Reproduction and ownership trace

The pre-fix exact-book regression failed on its first case with:

`cover body=556x483 media=556x320 fit=198x320 src=621x999 canonical=468`.

The baseline cover BMP SHA-256 is
`FA7ED23722259D5B6808601787B220709C5F7A5359E2971C1A4D5D3DC63B91F8`.
This directly reproduced the reported small image.

The trace crossed the following boundaries:

1. Reader0 correctly classifies the cover and maps as
   `SourceReaderLayoutImagePlacement_ImageOnly`.
2. Reader0's frame row carries canonical `visual_units`; when absent, the
   frozen Re10 adapter deliberately uses 18 units.
3. Re10 allocates media height as `visual_units * row_height` and gives it the
   full reader viewport width.
4. Lectern0 ignored the canonical vertical units, applied publisher display
   width where present, and clamped every image to 72 through 320 px.
5. zero_foundation decoded the source pixels correctly and Presentation Engine
   API 1 faithfully stacked the undersized host-provided media spec.

The defect therefore belonged to Lectern0 host presentation policy. It was not
a Reader0 parse/pagination failure, a zero_foundation decoder or geometry
failure, or a readerview0 shared-chrome issue.

## Implementation

`lectern0_resolve_presentation_image_box` now has two explicit policies:

- image-only rows consume full body width and exactly
  `(row.visual_units ? row.visual_units : 18) * layout_line_height` vertical
  pixels, failing closed if that canonical allocation cannot fit the body; and
- in-flow publisher images retain their existing publisher-size/aspect logic,
  72 through 320 px height bound, content offset, and 8 px row allowance.

Loaded image-only media no longer receives the synthetic rounded placeholder
card behind it. The decoded sprite remains centered and aspect-fitted within
the full media box. This path now emits
`draw_push_sprite_clipped_sampled(..., DrawSpriteSampleKind_Nearest)`
explicitly. Nearest sampling is intentional parity policy: frozen/current Re10
uses the legacy nearest helper. zero_foundation owns the sampling mechanism;
Lectern0 owns this host choice. A later quality-policy change would require its
own cross-host decision rather than entering this parity repair implicitly.

No callbacks, provider table, vtable, event bus, generic document interface,
hidden allocation, process-global mutable state, or shared image cache was
introduced.

## Exact-book regression

`scripts/win32_lectern0_reader_image_fit_smoke.ps1` guards the exact book hash
and size, performs an in-run strict build, opens the actual EPUB, captures the
cover, uses the real TOC navigation path to reach the maps, pages through all
three maps, and repeats the entire run in a fresh process.

For every case it requires:

- one loaded image-only canonical row and one Presentation Engine media row;
- full content width;
- canonical-unit media and row height greater than the removed 320 px cap;
- bounds inside the reader body;
- aspect-preserved fitted geometry;
- one exact source-pixel sprite command;
- explicit nearest sampling; and
- no loaded-image synthetic card background.

Final clean evidence:

- summary:
  `local/validation/reader-image-fit-slice6-final-clean/summary.json`;
- summary SHA-256:
  `C8490ED2A170539A124D28886456A2A1A0D84042FF2A1D7501FA18BB58C33FB0`;
- strict MSVC C11 `/W4 /WX`: pass;
- dependency and architecture guards: pass;
- repeatability: 2/2; and
- Git head recorded by the run:
  `1b309d0d06da194c15298d205ced74a708c84a01`, clean.

The body was 556 by 483 px and every media row was 556 by 468 px
(`18 * 26`). Per-case evidence was:

| Case | Source | Fitted | Pixel hash | BMP SHA-256 |
| --- | ---: | ---: | --- | --- |
| cover | 621x999 | 290x468 | `8e3ee298625ad30b` | `E3F9BFB4253A07D3D0DBD7C3FE6B2A361BB72C13FC16B1AED70495C052D502D0` |
| map 1 | 394x616 | 299x468 | `ac4a67355c8c007b` | `A94EE7A4B11BF984E6C649C81A57D86634D49AD76A3EC7502BD9F8E599301E32` |
| map 2 | 473x616 | 359x468 | `b04b6c74adac898f` | `0EE5F3056137F3E0283DF5F27174BE148D1CA27B8CBA610F015A4515706B1A96` |
| map 3 | 443x616 | 336x468 | `218f7232d8622b5c` | `A2671E9362632BEACB97B911D4726A67C3DB1F71A07BF8CDDAC96EC0A0C25E83` |

All four final PNGs were inspected directly. The cover and maps are large,
centered, aspect-preserved, unclipped, and free of the synthetic pale card.
Page chrome and progress remain correctly composed around them.

## Regression gates

The following rebuilt executable gates passed:

- synthetic host smoke: `cd460506f219d652`;
- synthetic image smoke, repeat 2: cover `349e02a6f3067ab9`, inline
  `17989905b776b21d`;
- deterministic visual smoke, repeat 2: `df02d5d2dd061128`, Presentation
  Engine hash `3a3cf46f0444a1bd`;
- complete Reader View smoke, repeat 2: `bbc068e6c290fee1`;
- empty-document startup interaction;
- exact-book post-action arrow workflow, repeat 2, summary SHA-256
  `80270104146D3239E2431FDE31BD396E4E9A6079A8A04766F8028CDB329C904C`;
- six-theme active Find distinction, summary SHA-256
  `B7F4A0FD3399E5371280FB531D4BCC895545FD0CD19A64A929A1461C89241D16`;
- exact-book Find snippet context, summary SHA-256
  `033FF50620335C59BC9584DD2F091246AC7FB9D2E57D5C5B982AF9AA518FD147`;
- selection geometry/menu, repeat 2, summary SHA-256
  `94265C8B282C030B8CE9CDAC29FCB62D0ECA318E5E1DDD3BA534B94C1B508E01`;
  and
- publisher typography and all spacing choices, summary SHA-256
  `12B469F27CE5FB6EB9762F1A6B4D8201C3216B7E7D58517C2E0B64506BBD5A13`.

The synthetic cover hash changed because it is intentionally an image-only
page. The synthetic inline image hash remained exact, proving that the in-flow
policy did not change.

## Cross-host acceptance

The clean-tree harness rebuilt both applications at Re10
`4d197e23cdaea8b9979c064593fbf8210ffd2d1d` and Lectern0 implementation
`1b309d0d06da194c15298d205ced74a708c84a01`:

- manifest:
  `C:\Temp\lectern0_image_fit_slice6_final_clean_20260719_1\manifest.json`;
- manifest SHA-256:
  `B929913567DCBF87959BB1D45A613596C85E7D56ABBAE60E372CF9BE54BBCB89`;
- deterministic: 15/15;
- decoded-pixel exact: 15/15;
- exact records: 9/15, with the six accepted host-record differences still
  pixel-exact;
- clean-tree enforcement: pass;
- in-harness builds: pass;
- exact dependency enforcement: pass; and
- acceptance eligible: true.

The frozen 28-case Re10 reference matrix was not rerun because neither Re10 nor
any Re10 dependency changed. Its existing acceptance remains applicable; the
affected cross-host and all Lectern0 image/Reader View/real-book gates were
rerun.

## Why prior gates missed the defect

The original synthetic image smoke asserted decoding, cache counts, visible
output, and determinism, but did not assert image-only canonical units, media
height, page-fill scale, or Re10 composition. The 15-case cross-host matrix has
no image-page case. The 28-case frozen matrix exercises Re10 only. The earlier
real-book workflows navigated text, Find, selection, and typography states but
did not capture the cover or maps. The new four-case exact-book regression
closes each of those coverage gaps.

## Promotion state and next work

This slice is committed but not promoted. Promotion requires new explicit
authorization. The next previously planned ownership slice is Lectern0-only
empty-document landing composition, followed by the expanded real-book
acceptance matrix. No Kindle-gap, PDF, reader0 Slice 4, shared-cache, or
architecture expansion belongs to this slice.
