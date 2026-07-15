# Lectern0 reader navigation Slice 4B

Date: 2026-07-15

## Objective

Reconcile re10 and lectern0 on reader0 API 3 before extracting any shared
reader chrome. The concrete core owns EPUB fragment resolution, TOC and Find
destination transitions, navigation history, and canonical page selection.
Each host continues to own viewport inputs, persistence, status, panels,
commands, rendering, and platform integration.

## Dependency pin

Lectern0 exact-pins reader0
`63a66083765cde537e1a31c21bd249518818456a`, API 3, `0.3.0-dev`.
UI0 and zero_foundation pins are unchanged.

## Lectern0 boundary

Lectern0 adds two concrete adapters: navigate to an EPUB nav point and navigate
to an active EPUB search match. They pass caller-owned layout inputs to
reader0, capture the resulting canonical frame, update host status, and save
the reader-owned location. They do not introduce a provider table, callback
surface, generic document model, duplicated navigation algorithm, or reader
state mirror.

The native toolbar remains the Slice 1 Open/Previous/Next host. TOC and Find
controls are intentionally not built locally in 4B: their first UI0
implementation belongs in readerview0 so re10 and lectern0 can consume one
view/chrome package after their semantic behavior is reconciled.

## Evidence

The generated EPUB smoke includes an NCX TOC fragment targeting the second
spine and a Find phrase in the first spine. The headless host must navigate to
the resolved TOC anchor, navigate back through reader0's Find-result API, retain
history, then pass the existing cross-spine and resize-repagination checks.
The deterministic render smoke remains unchanged.

## Non-goals

No PDF, generic document interface, shared chrome implementation, annotations,
theme implementation, image-cache extraction, or accessibility redesign is
part of 4B.
