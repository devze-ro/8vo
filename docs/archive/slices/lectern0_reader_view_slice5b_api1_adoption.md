# Lectern0 Reader View Slice 5B API 1 adoption

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

Date: 2026-07-16

## Objective

Make lectern0 the first production consumer of the common Reader View API 1
boundary and bring the current re10 EPUB reader feature surface into the
standalone host. This is Stage 1 convergence work. It does not add features
that are also missing from re10; those remain in the later Kindle-gap phase.

## Exact dependency pins

- readerview0
  `f59c9d59e0cc128327812ad1a20edeadfd828d58`, API 1,
  `0.1.0-dev`;
- reader0 `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`, API 3,
  `0.3.0-dev`;
- UI0 `f8de965c193a6278d330193c34948bfec09e592b`, API 89,
  `0.1.0-dev`; and
- zero_foundation `a87938edcd16c6c09c8d423a42b6d86122d85a73`,
  `0.4.0-dev`, Presentation Engine API 1.

Lectern0 source-consumes `readerview0.c` exactly once and links UI0 and the
UI0-compatible zero_foundation source closure exactly once in the final unity
build. Readerview0 has no reader0 or direct zero_foundation dependency.

## Production feature route

Lectern0 now projects and executes the current shared re10 reader surface:

- Open, previous/next page gutters, Back/Forward history, and progress seek;
- responsive full, compact/overflow, and minimal top-toolbar composition;
- TOC rows and reader0 fragment activation;
- Find draft, status, results, previous/next match, and activation;
- font-family, font-size, line-spacing, and Light/Sepia/Dark theme choices;
- current-page bookmark add/remove plus stable bookmark rows and stars;
- highlight and note rows, filters, activation, editing, stars, deletion, and
  export;
- text selection, copy, highlight colors, notes, dictionary lookup, web lookup,
  and translation;
- shared distraction-free state and host-native fullscreen transitions; and
- empty, unavailable, loading, ready, and error surface projections.

Reader0 still performs every page, TOC, Find, location, selection, pagination,
and history transition. The history adapter normalizes history-only `None` and
page reasons to reader0's concrete `Location` semantic reason before a
suppressed-history restoration; this keeps Forward valid without changing the
reader0 API or fabricating a shared document interface.

## Ownership and lifetime

| Surface | Owner and lifetime |
|---|---|
| EPUB frame, navigation, search, selection, pagination | reader0; concrete host lifetime |
| projected labels, TOC/Find/right rows, choices, status | lectern0; borrowed for one build |
| `ReaderViewState` and fixed-capacity drafts | lectern0 storage; readerview0 interaction semantics across frames |
| frame/action/draw/text/semantic arrays | lectern0-owned `ReaderViewFrameStorage`; overwritten by the next build |
| settings and current location | lectern0 versioned files; application lifetime |
| bookmarks, highlights, notes, stars, and stable IDs | lectern0, 128 bookmarks and 128 highlight/note records per book |
| decoded images and failure/cache policy | lectern0 64-entry cache over the zero_foundation decoder |
| EPUB drawing and Presentation Engine inputs | lectern0 over reader0 frames and zero_foundation mechanisms |
| native window, fullscreen, clipboard, URLs, and accessibility objects | lectern0/Win32 |

Every persistent mutation is executed by lectern0 after a bounded action is
returned. Readerview0 retains no host row, invokes no callback, allocates no
storage, and performs no optimistic database mutation.

## Viewport, focus, and accessibility

`ReaderViewLayout.viewport_rect` is used directly for reader0 pagination and
canonical-frame rendering. Shared gutters and docked panels reserve geometry;
overlay panels do not establish a second document layout. Layout-affecting
state changes recompute the rectangle and repaginate at the current
reader-owned location.

Stable shared semantic IDs connect pointer, keyboard, and accessibility input.
Lectern0 owns a Win32 MSAA `IAccessible` adapter over the current semantic
records, services `WM_GETOBJECT`, publishes native focus/reorder events, and
routes default actions back through `reader_view_accessibility_invoke`. Native
objects and action execution do not move into readerview0.

## Deterministic evidence

- exact dependency/API guards and architecture audit: pass;
- strict C11 MSVC `/W4 /WX` build: pass;
- readerview0 strict API tests and source-closure audit: pass;
- existing reader0 API 3 navigation hash: `d7f4448c51b3fbd1`;
- Reader View action/state/persistence hash, repeated in fresh processes:
  `eb01590287b7d981`;
- native accessibility: 19 nodes, shared focus/action routes, keyboard progress
  seek, native fullscreen, and shared distraction-free checks: pass twice;
- visual hash: `49500d694f7390f6`;
- unchanged Presentation Engine geometry hash: `32d577b44e359941`;
- image cover/inline hashes: `114e631953bc1578` and `ff31be3d41c42a0c`;
- image cache entries/lookups/hits/misses: `2/5/3/2`.

The additional image-cache hit is the expected first shared-view render that
establishes the smaller central viewport. Entries and misses remain `2/2`, so
both images are still decoded exactly once and cache ownership is unchanged.

## Deferred work

At the time, Readerview0 and lectern0 remained local; publication occurred
later. Re10 adoption required a new fetch and explicit editor-work
reconciliation before any branch or source change. Visual comparison with re10
and promotion of a re10 dependency are therefore the next Stage 1 gate, not
part of this lectern0 commit.

PDF support, a generic document provider, shared persistence/rendering/image
cache policy, and features absent from current re10 are not introduced.
