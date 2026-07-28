# Lectern0 host images Slice 5A

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

Date: 2026-07-15

## Objective

Prove the shared encoded-image mechanism through the real standalone reader
host by rendering both an EPUB cover and an inline resource. Keep cache and
presentation policy in lectern0, not reader0 or zero_foundation.

## Dependency pin

- zero_foundation
  `3a1a73b17036b03e18d487d8d107cbc807e248c1`, `0.2.99-dev`;
- reader0
  `63a66083765cde537e1a31c21bd249518818456a`, API 3,
  `0.3.0-dev`;
- UI0 `f8de965c193a6278d330193c34948bfec09e592b`, API 89.

Lectern0 is `0.3.0-dev`. All dependencies remain sibling source packages with
exact commit/API/version and clean-tree guards.

## Ownership

zero_foundation owns the explicit decoder context, Win32 WIC backend, decoded
limits/status, and caller-arena BGRA8 output. reader0 owns EPUB resource bytes,
image metadata, pagination, and the canonical frame.

Lectern0 owns:

- the decoder and pixel-arena lifetimes;
- a 64-entry cache keyed by `DocDocumentId` and resource index;
- media-type exclusions, cache-full status, failed-attempt retention, and reset
  on successful document open;
- decoder-to-frame status mapping and pixel-view attachment;
- aspect-fit clipped sprite rendering and alt-text fallback rendering.

The cache performs no per-frame decode after an entry exists. It introduces no
callback table, provider interface, DI layer, event bus, process-global
document/cache state, or reader0 dependency on zero_foundation's platform
backend.

## Evidence

`scripts/win32_lectern0_image_smoke.ps1` creates a two-spine EPUB with a PNG
cover and a PNG inline resource. It invokes the real host twice, requires both
resources to be loaded, writes two BMPs, and requires stable process-level
pixel and file hashes.

- cover loaded: 1;
- inline loaded: 1;
- cache entries/lookups/hits/misses: 2/4/2/2;
- cover pixel hash: `c11264a084d0e04f`;
- inline pixel hash: `41eef32ffd3bd41d`;
- C11 `/W4 /WX` build: pass;
- exact dependency and architecture guards: pass;
- existing API 3 TOC/Find/cross-spine host smoke: pass,
  `d7f4448c51b3fbd1`;
- existing repeatable visual smoke: pass, `df9d9534bb1c2f06`.

Visual inspection confirms the cover is centered and aspect-fitted and the
inline image is drawn between the canonical styled text rows.

## Non-goals

No shared readerview0 API, shared reader chrome, themes, annotation UI,
accessibility redesign, PDF support, generic document interface, cache
extraction, image editing, animation, or Linux decoder backend is part of this
slice.

## Next

Compare the concrete re10 and lectern0 host integrations, then define the
smallest readerview0 API 1 for shared UI0 reader chrome/view composition without
moving document, persistence, platform, or image-cache policy into the shared
view package.
