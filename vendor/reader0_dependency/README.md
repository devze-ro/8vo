# reader0 dependency

Current pinned reader0 consumer dependency:

- mode: `sibling-source`
- version: `0.7.0-dev`
- API version: `7`
- commit: `58ec6d11575c36176eb85511759d39dc93acb78b`

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
Reader0 API 7's presented-navigation primitive validates the actual canonical
destination before 8vo exposes a history entry; failed or interrupted frames
leave both the prior history and durable position authoritative.

Saved catalog and legacy positions explicitly request Reader0's bounded
six-page restore before resolving the persisted byte.
Structural jumps and history moves remain provisional in 8vo until their
matching frame is successfully presented; only then may a durable reading
position or progress choice advance.
