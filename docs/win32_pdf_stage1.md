# Win32 PDF Stage 1

This slice adds one concrete PDF reader to the existing 8vo Win32 product. It
uses Reader0 API 10 and Reader0's audited MuPDF 1.28.2 PDF-only core. EPUB keeps
its existing concrete `EpubReader`; the host does not introduce a generic
document vtable or an EPUB adapter.

## Product boundary

The host owns exactly one explicit document kind: None, EPUB, or PDF. A
successful cross-format replacement closes the previous concrete reader only
after the new candidate is usable. Failed PDF-to-PDF, PDF-to-EPUB, and
EPUB-to-PDF replacements retain the previous reader and path. Host state is
never published as None while either concrete reader remains open, and a
fallible PDF deinitialization runs before any other app teardown.

The picker accepts `.epub` and `.pdf`. Existing multi-EPUB import/library
behavior is unchanged; a PDF must be selected alone and is opened directly.
Android remains EPUB-only.

Stage 1 PDF actions are:

- fit-page rendering;
- Previous and Next;
- Back and Forward page history;
- direct page/progress seek;
- Open and Fullscreen through the shared Reader View chrome.

Reader View marks Contents, Find, reading settings, bookmarks, annotations,
selection tools, lookup, and export unavailable for PDF. Search, links as
interactive hit targets, ToC, persistence/sync, annotations, and Android PDF
are later slices; their absence is explicit rather than emulated.

## Raster and memory ownership

Reader0 renders one full-page `RGBA8Premultiplied` raster. 8vo reserves one
64 MiB Ground0 arena, allocates at most one four-byte raster, and swaps the red
and blue bytes in place for the existing BGRA sprite path. There is no second
full-page conversion buffer and no C-runtime allocation in `octavo_pdf.c`.
Fit geometry combines the viewport and Reader0 dimension limits with the exact
64 MiB pixel budget. Its overflow-safe predicate checks the rounded-up integer
dimensions by division before multiplication; if float conversion or `ceil`
crosses the byte boundary, a bounded search lowers scale to the greatest
representable value proven to fit. Large landscape displays therefore render
at a memory-aware fit instead of failing solely because their native viewport
would require a larger full-page raster.

Publication is transactional. An exact cached render may reuse the published
surface. Every cache miss withdraws the borrowed BGRA pointer and generation
before page-info, geometry, allocation, or backend work. The pointer is
republished only after Reader0 completes the full render and every pixel has
been swizzled. A failed or cancelled render therefore cannot expose partial or
stale pixels. Such a failure is presentation-local: while the concrete PDF is
still open, document state remains Ready so the next resize or navigation frame
can retry. If an accepted page action's real Win32 presentation repeatedly
fails in the PDF raster transaction, the next explicit Open, Close, page,
history, or progress-seek mutation abandons only that impossible presentation
identity, cancels its retry timer, and starts a fresh gated mutation. Ordinary
surface acquisition or present failures retain the existing retry gate.

## Exact build and provenance

`build\win32_build.bat` always initializes a fresh x64 Visual Studio toolchain,
clears ambient `CL`, `_CL_`, and `LINK` injection, and verifies every exact
clean dependency. Reader0 then verifies or rebuilds its pinned PDF core. 8vo
requires the freshly selected `cl.exe` file version and SHA-256 to exactly
match Reader0's verified core provenance before compiling the product. It also
requires the same VCTools/Windows SDK versions, selects `link.exe` from the
compiler's exact x64 directory, proves that PATH resolves that linker first,
and invokes both tools by absolute path.

The optimized product links with `/OPT:REF /OPT:ICF` and emits `8vo.map`.
MuPDF requires no additional Win32 system library beyond 8vo's pre-PDF link
set; the audit rejects speculative `kernel32`, `winspool`, `advapi32`, or ODBC
additions.
Reader0's final link-map audit rejects OCR, barcode, HarfBuzz, Extract, MuJS
regexp, and the unsafe structured-text search object. The safe
`fz_new_search` overlay is retained as an audit sentinel even though PDF search
is not exposed in Stage 1. The build writes
`build\win32\8vo_pdf.provenance.json`, including product/map hashes, the exact
compiler identity, Reader0 core fingerprint, dependency pins, build flags, and
hashes of the 8vo PDF source/build closure. The default release-profile build
first requires the complete 8vo Git tree to be clean, then verifies it again
while writing provenance. Provenance schema v2 records the exact commit/tree,
verified cleanliness, release eligibility, and porcelain status evidence; the
clean commit identity covers unity inputs beyond the individually hashed
closure. Pre-commit qualification may set
`OCTAVO_ALLOW_DIRTY_DEVELOPMENT_BUILD=1`, but that explicit override is always
recorded as `development-nonrelease` and never as release-eligible.

## Validation

After a strict build:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_pdf_stage1_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_octavo_host_smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\win32_pdf_product_source_state_smoke.ps1
```

The PDF smoke creates a deterministic three-page fixture with text, vector
graphics, a raster image, and a link annotation. It runs the product twice and
locks deterministic bitmap output, the in-place conversion, the 64 MiB cap,
an 8192-by-4320 landscape fit, render-failure recovery through both a smaller
resize and navigation, real-Win32 persistent-failure escape through page,
history, seek, open, and close, navigation/history/progress, unavailable Reader
View actions, all three
replacement-failure directions, explicit close/release invariants, and live
cancel-token teardown refusal. The existing host smoke remains the focused EPUB
regression.

## License boundary

MuPDF 1.28.2 is AGPL-3.0 unless a separate Artifex commercial license is used.
The linked Win32 PDF binary must be distributed only with the applicable AGPL
source/license offer and corresponding source closure (including the 8vo,
Reader0, and build material required by that license), or under a separately
obtained commercial license. Reader0's pinned source-package script is the
release mechanism; an ordinary developer checkout is not itself a compliant
binary distribution package.
