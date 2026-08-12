# Win32 PDF selection and Copy

This milestone adds bounded, transient PDF text selection and Copy to the
existing Windows PDF reader. It deliberately adds no durable highlight, note,
bookmark, reading-position, annotation, or synchronization record. Android
remains EPUB-only, and all existing EPUB selection and annotation contracts
remain separate.

## Ownership boundary

Reader0 API 12 owns MuPDF structured-text extraction, endpoint snapping,
canonical counted UTF-8, canonical page-space quads, validation, cancellation,
and document/publication generation checks. A successful snapshot is copied
transactionally into the caller's Ground0 Arena; no MuPDF or Reader0 borrowed
pointer crosses the publication boundary.

8vo owns pointer gesture policy, the transient selection generation, screen
projection, Reader View state, accessibility projection, lifecycle retirement,
and the Windows clipboard handoff. Readerview0 supplies only the existing
selection-tools chrome and semantic records. It does not extract PDF text or
own a selection, clipboard, annotation, or persistence record.

## Bounded publication

8vo keeps separate candidate and published 2 MiB Ground0 Arenas. Its stricter
publication limits are 1 MiB counted UTF-8, 262,144 Unicode scalars, and 384
quads. Every candidate must match the active document generation, Reader0
publication generation, page index, and caller selection generation; contain
valid UTF-8; and contain only finite endpoints and finite, nondegenerate,
publishable quads. Zero-area or malformed geometry is rejected. Cancellation,
a limit, allocation failure, malformed output, or a stale generation clears
both Arenas and publishes zero text and zero quads.

The published Reader View selection advertises exactly `Active | CanCopy`.
Highlight, note, bookmark, lookup, web lookup, and durable actions are absent.
Copy revalidates every generation and passes the full counted UTF-8 snapshot to
the existing platform-owned clipboard conversion. It does not use the EPUB
selection's fixed-size display buffer. `Ctrl+C` enters the same validated
handoff. Reader View's Copy action, `Ctrl+C`, and the MSAA Copy menu item's
default action each make one production clipboard attempt; the smoke verifies
the resulting counted text through exact `CF_UNICODETEXT` transfer and
readback. Harness-only setup first publishes and verifies distinct sentinels,
so successful Copy must replace one sentinel while a stale-key action must
preserve another. Clipboard preservation and restoration use bounded,
diagnosed availability retries; the saved scope is released only after a
checked restore succeeds.

## Geometry and input

One finite page-to-screen transform owns centered raster placement and inverse
mapping for link hit testing, selection endpoints, selection-tool anchoring,
and quad overlays. A pointer press arms a candidate. Movement below the
four-pixel Euclidean threshold remains a click; a matching link therefore has
click priority. Reaching the threshold retires the link activation and promotes
the gesture to selection. A successful link activation explicitly cancels the
selection candidate before dispatch.

Page, history, and direct-seek mutations; successful reload or replacement;
suspend or deactivation; capture loss; Escape; close; and application release
retire the selection. Escape also retires an armed or promoted drag before its
mouse release can publish. A stale snapshot is never projected, painted, or
copied.

## Qualification boundary

`scripts\win32_octavo_pdf_selection_smoke.ps1` creates a deterministic two-page
fixture and repeats the real product smoke twice. It covers multiline Unicode
text ownership, byte/scalar/quad limits, cancellation with zero publication,
nondegenerate quad validation including a zero-area rejection, two-viewport
transform round trips, overlay geometry, the four-pixel threshold, link click
priority and link-drag promotion, Copy-only semantics, stale action rejection,
active-drag Escape retirement, the real Reader View/keyboard/MSAA Copy routes,
exact Windows clipboard readback, and lifecycle retirement. The architecture
audit rejects C-runtime allocation in the selection module and locks its API
12/Arena, geometry, real-Copy, and accessibility boundary.

Passing Windows development tests is not product release qualification. Native
Linux/arm64 evidence, the product-level corresponding-source/license/install-
information bundle, and an explicit AGPL-3.0-or-later or Artifex commercial
licensing basis remain separate release requirements.
