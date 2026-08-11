# reader0 dependency

Current pinned reader0 consumer dependency:

- mode: `sibling-source`
- version: `0.12.0-dev`
- API version: `12`
- commit: see the authoritative sibling `COMMIT` file (the coordinated final
  pin is advanced atomically with that file)

This directory contains dependency metadata, not a source snapshot. 8vo
locates the live reader0 checkout through `OCTAVO_READER0_DIR` or
`../reader0`, adds `reader0/code` to its include path, and compiles the
supported `reader0.c` unity source exactly once.

The strict dependency guard requires the exact clean reader0 commit and
matching API/version metadata. Reader0 owns EPUB parsing, canonical pagination,
page transitions, EPUB 2/3 structural navigation interpretation, canonical
location/percentage and meaningful-page targets, semantic navigation, bounded
navigation preparation, and the session-bounded back/forward model. 8vo retains
host input and scheduling, presentation transactions, lifecycle policy,
persistence, accessibility adaptation, and bounded host-owned caches.
Reader0 API 10's EPUB-compatible presented-navigation primitive validates the actual canonical
destination before 8vo exposes a history entry; failed or interrupted frames
leave both the prior history and durable position authoritative.

Reader0 API 10 also exposes bounded encoded-resource extraction and
the concrete PDF boundary used only by the Win32 host.
Reader0 stats the selected ZIP entry in the same opened archive and returns a
distinct limit result before output allocation or entry decompression. Android
uses the remaining presentation byte budget for every image request; the
Windows reader uses its existing 32 MiB encoded-resource ceiling.

API 11 appends exact SHA-256 PDF document identity to successful open
transitions, readers, and frames using Ground0's allocation-free hash path.
8vo does not yet consume or persist that identity. API 12 appends canonical,
Arena-owned PDF text selection snapshots with counted UTF-8, finite quads, and
document/publication/selection generations. Only the Win32 host consumes that
addition; Android remains on the backward-compatible EPUB surface.

Saved catalog and legacy positions explicitly request Reader0's bounded
six-page restore before resolving the persisted byte.
Structural jumps and history moves remain provisional in 8vo until their
matching frame is successfully presented; only then may a durable reading
position or progress choice advance.
