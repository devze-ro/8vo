# reader0 dependency

Current pinned reader0 consumer dependency:

- mode: `sibling-source`
- version: `0.5.0-dev`
- API version: `5`
- commit: `bcd683160a2b34afc787b99b98509a75e8c506cb`

This directory contains dependency metadata, not a source snapshot. Lectern0
locates the live reader0 checkout through `LECTERN0_READER0_DIR` or
`../reader0`, adds `reader0/code` to its include path, and compiles the
supported `reader0.c` unity source exactly once.

The strict dependency guard requires the exact clean reader0 commit and
matching API/version metadata. Reader0 owns EPUB parsing, canonical pagination,
page transitions, semantic navigation, and bounded navigation preparation.
Lectern0 retains Win32 input and scheduling, presentation, persistence, and its
bounded host-owned raster cache.
