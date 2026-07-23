# Lectern0 image-filtering reconciliation

## Scope

This slice reconciles Lectern0 to the current source dependencies and applies
the zero_foundation image-resampling seam already proven by Gesture0. It fixes
jagged EPUB reader images and library covers without moving presentation or
cache policy into Reader0.

## Dependency checkpoint

- reader0: `0770b509d19cbefe86209c28045881993b65d105`, API 5,
  version `0.5.0-dev`
- zero_foundation: `fa7f680f933c23d84f9b74e15887a3b8bb78d2f9`,
  version `0.4.3-dev`
- Presentation Engine: API 1

The sibling-source dependency gate must pass against these exact commits before
build or smoke evidence is accepted.

Reader0's current prepared-forward-boundary reuse makes one forward and two
backward cross-spine targets ready during the held-navigation queue fixture.
Lectern0 therefore reconciles that frozen assertion from `0+2` to `1+2`; page
identity, move count, scheduling, and synchronous-rebuild expectations remain
unchanged.

## Ownership and policy

Reader0 continues to own EPUB resource identity, canonical image placement, and
visual units. Zero_foundation owns BGRA8 resampling. Lectern0 owns:

- a 16-entry, 64 MiB page-presentation cache keyed by page and layout geometry;
- area sampling whenever either target axis shrinks;
- linear sampling for enlargement and nearest sampling for exact-size draws;
- one-to-one submission of prepared reader surfaces;
- area-generated persistent library thumbnails; and
- thumbnail format version 2, which rejects legacy nearest-neighbor files and
  regenerates the selected startup cover.

Capacity or allocation failure falls back to an explicit sampled draw. It does
not turn a loaded image into a missing-image placeholder.

## Acceptance

- strict `/W4 /WX` Win32 build
- dependency and architecture gates
- reader image-fit smoke: four GOTM image-only pages, one filtered build plus
  one cache hit per case, zero fallbacks, repeatable pixels
- library smoke: current version-2 cover thumbnail, repeatable wide/compact
  states
- existing reader navigation and presentation regressions
- live candidate inspection of both a library cover and an EPUB reader image
