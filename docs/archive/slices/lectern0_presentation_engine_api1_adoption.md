# lectern0 Presentation Engine API 1 adoption

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

## Scope

This slice is the first production host adoption of zero_foundation
Presentation Engine API 1. It advances the exact dependencies to:

- zero_foundation
  `a87938edcd16c6c09c8d423a42b6d86122d85a73`, `0.4.0-dev`,
  Presentation Engine API 1;
- reader0 `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`, API 3,
  `0.3.0-dev`; and
- UI0 `f8de965c193a6278d330193c34948bfec09e592b`, API 89,
  `0.1.0-dev` (unchanged).

## Production route

Lectern0 resolves each reader0 canonical style row into explicit pixel height,
before/after margins, content insets, source identities, role, alignment, and
opaque row indexes. Image rows additionally produce sorted row-relative media
specs with resolved host image boxes. All spec and output arrays are owned by
`Lectern0App` and are capped only by reader0's existing 96-row/16-image
canonical-frame contract.

`presentation_engine_block_flow_build` performs the checked vertical stacking
and media placement. The draw adapter consumes the returned row/content/media
rectangles; the previous second vertical-placement loop is removed from the
production path. A failure, truncation, storage mismatch, overflow, or frame
that exceeds the reader body remains visibly incomplete.

## Ownership

- reader0 retains EPUB parsing, document/layout/pagination semantics,
  canonical-frame records, navigation, search, and selection;
- zero_foundation owns only callback-free caller-storage geometry;
- lectern0 retains pixel-policy resolution, rendering, font-cache registration,
  image decoder/cache lifetime, aspect fitting, persistence, window/input, and
  UI0 adaptation.

There is no callback/provider table, vtable, event bus, global presentation
state, reader0 API change, shared persistence, or readerview0 dependency. The
simple-grid API is compiled as part of the promoted module but is not routed
until lectern0 has a real independently presented table-cell path.

## Evidence

- strict MSVC C11 `/W4 /WX` build and exact dependency/API guards;
- architecture audit requiring one Presentation Engine unity source and one
  production block-flow route;
- unchanged navigation host hash `d7f4448c51b3fbd1`;
- unchanged repeatable text visual hash `df9d9534bb1c2f06` plus deterministic
  presentation geometry hash `32d577b44e359941`;
- unchanged cover hash `c11264a084d0e04f` and inline hash
  `41eef32ffd3bd41d`;
- exact image cache telemetry `2/4/2/2` for entries/lookups/hits/misses.

A metadata-only diagnostic using the new dependencies and old presentation
loop retained the same image hashes. The adopted route also matches them
exactly and remains stable across fresh-process repeats.
