# reader0 dependency

Current pinned reader0 consumer dependency:

- mode: `sibling-source`
- version: `0.5.0-dev`
- API version: `5`
- commit: `b604556723c5a196ed7d2b1249f56bd3d976edb4`

This directory contains dependency metadata, not a source snapshot. 8vo
locates the live reader0 checkout through `OCTAVO_READER0_DIR` or
`../reader0`, adds `reader0/code` to its include path, and compiles the
supported `reader0.c` unity source exactly once.

The strict dependency guard requires the exact clean reader0 commit and
matching API/version metadata. Reader0 owns EPUB parsing, canonical pagination,
page transitions, semantic navigation, and bounded navigation preparation.
8vo retains Win32 input and scheduling, presentation, persistence, and its
bounded host-owned raster cache.

Saved catalog and legacy positions explicitly request Reader0's bounded
six-page restore before resolving the persisted byte.
