# Lectern0 Reader0 navigation-recovery adoption

Date: 2026-07-21

Status: Lectern0 host adoption and clean two-process acceptance complete at
implementation commit `01f77de`; Reader0 dependency reconciled at `8eb1db6`;
no promotion performed

## Objective

Adopt the same Reader0 canonical navigation repair as Re10 and combine it
with Lectern0's bounded action-first held-key scheduling repair.

The current exact Reader0 dependency is
`8eb1db66786c588bbe963552d7e78a7cb8fbdacc`. It contains the independently
audited recovery contract and is the fixed input to the clean connected
cross-spine reversal and timing gates. Reader0 remains API 5 and version
`0.5.0-dev`.

## Ownership

Reader0 owns canonical predecessors, cross-spine page selection, prepared
source lifetimes, raw-location normalization, and the bounded reusable page-
boundary tail. Lectern0 consumes those results directly.

Same-spine preparation results pass Reader0's public exact-adjacency validator.
A cross-spine result is owned by Reader0's private bounded prepared ring and
cannot be re-proved through the active pagination, so Lectern0 accepts it only
when the public result is `AdjacentSpine` or `AlreadyReady`, its range is
nonempty, and its spine moves strictly in the requested document direction.
The host does not recreate Reader0's cross-spine ownership or boundary proof.

Lectern0 still owns Win32 event capture, its queue-aware 60 Hz frame loop, the
24-frame initial hold delay, three-frame repeat cadence, one-action-per-
successful-presentation gate, invalidation, rendering, progress persistence,
and the ordinary idle presentation cache. Active/same-key native repeats are
coalesced into that one stream. The action gate is independent of active repeat
and survives key-up/cancellation; one bounded physical page action may wait for
the prior frame, and repeat is armed only after an immediate Reader0 move and
frame capture succeed. Ctrl, Shift, Alt/system-key, focus, and deactivation
transitions cancel repeat, and stable presentation includes any Reader View
follow-up frame required after applying returned actions. Reader0-only logical
preparation may use post-presentation deadline slack, but any already-queued
message returns first to the existing bounded FIFO drain, so key-up or
cancellation clears the pending tail. The held path does no speculative frame
or raster warming and defers synchronous persistence until
release or another repeat-stop condition. The real queue lock covers Ctrl,
Shift, and Alt separately and proves the deferred save against temporary, atomically
written state/catalog files without touching user AppData. No pagination rule
is duplicated in the host, and no Readerview0, UI0, or zero_foundation API is
changed.

The host gate does not infer page identity from paint timing. It binds the
committed Reader document id/generation, layout generation, exact canonical
page range, Reader frame generation, and host capture generation. Text pages
must match their exact visible byte range. Image-only pages match the exact
canonical Reader page plus their decoded visual-unit/placement signature; the
cover and three map pages are explicit regression cases. Frame `page_index` and
`page_count` summaries remain optional Reader output metadata and cannot
override the committed canonical page record.

While that identity is outstanding, seven real host mutation routes are
rejected or boundedly deferred, including open and close. Capture failure keeps
the mutation gate closed until the same canonical page has a fresh valid frame;
same-page refresh additionally requires a newer frame/capture epoch. A
successful Reader open remains a successful catalog/import transaction even if
the first capture fails, and subsequent capture recovery makes it visible.

## Acceptance

- exact dependency guard, architecture audit, and strict MSVC C11 `/W4 /WX`
  build pass;
- the exact `gotm_new.epub` page-turn runner completes exactly 64 forward and
  exactly 64 backward moves (`direct_traversal=64+64_exact`), including chapter
  boundaries, without a halt, skip, crash, zero authoritative canonical page,
  zero frame, or incomplete text-row coverage. On long-form spines with at
  least 128 source bytes, it additionally rejects orphan one-character frames
  and pages beginning inside a source word; these are checked independently
  against raw active-spine UTF-8 bytes rather than accepted from a replay of
  the navigation path. Short publisher headings remain valid only when their
  exact canonical page/frame identity agrees;
- the real Win32 queue executes one 13-page forward traversal followed by a
  13-page reverse traversal from the same endpoint, crosses the spine boundary
  both ways, returns to the exact canonical start, and proves strict
  action/presentation alternation;
- replay-derived queue ranges are accepted only as an ordering check; the
  frozen Re10/cross-host canonical-range oracle remains independently required;
- 26/26 stable surface presentations and 22/22 visible intervals satisfy the
  frozen 60 Hz, 24-frame initial-delay, and three-frame cadence contract;
- all seven outstanding-presentation mutation attempts, the bounded cancel,
  page/same-page/open capture failures, and cover-plus-three-map image identity
  gates pass without host-side navigation recovery or filtering;
- held input remains coalesced and render-gated, with no held-path speculative
  host frame/raster warming, exactly 12+12 Reader0-only logical tail calls for
  13+13 presentations, exactly 0+2 cross-spine `AlreadyReady` results, and
  bounded action-plus-render time;
- Re10 and Lectern0 agree on canonical byte ranges for equivalent layout
  inputs; and
- library, Reader View, post-action arrow, cover/image-fit, persistence, and
  clean-tree guardrails remain green.

The superseded Reader candidate
`3343c3157211a9310ca25dfe362f69c174a9f894` is deliberately retained only as
failed diagnostic history. Against the exact GOTM fixture, the direct runner
failed on backward step 5: the retained predecessor was
`18:10924-11857`, while reverse recovery rephased to `18:11686-11857` and
returned `WindowRebuildFailed`. The real queue likewise failed its connected
13-forward/13-backward round trip. Those failures exposed two omissions from
the accepted Re10 recovery: widening the same-spine reverse window through five
earlier starts followed by an immediate-anchor forward retry, and applying the
staged first-page decorative-leadin reservation before split-page starts are
derived. Those repairs advanced the diagnosis but did not yet close every
retained-boundary phase.

The subsequent candidate
`9c944411174e7afdc48b63c49fe723262a57d2bc` failed deterministically in two
fresh Lectern0 processes at backward step 7 from `18:10052-10924`. Its
six-page widened rebuild placed the exact predecessor `18:9265-10052` in the
final incomplete-tail slot. The next checkpoint restored the accepted
contextual sequence: seven-page widened and immediate-predecessor attempts
followed by a six-page target publication, with exact ownership compaction.
Reader0 `a7af0fa618d7a8a5f4008207e9d548670ea6f200` then closed zero-byte
physical visual-page ownership, but remains an intermediate failed checkpoint
because it did not yet restore the frozen source-page boundary ring and exact
retained cross-spine promotion.

Reader0 `d0cb70ddbea6822c23c23cc4746590cbab5c62fb` is a historical recovery
checkpoint. It captures source-page identity while the originating pagination
still owns it, commits the bounded ring entry only after a successful move,
and materializes retained pages from their exact first and end bytes. The
subsequent dependency history is `82754e8` for sustained prepared-navigation
streams, `093b113` for exact prepared-navigation boundaries, and `6b8722a` for
exact reverse-pagination boundaries.

Reader0 `721981e340977bba559702c0d43f0428475f54a0` additionally rejects
an incomplete resident pagination as a semantic-restore fast path, requires a
complete byte-zero focused-spine candidate with complete rows and exact page
ownership, and retains that complete active pagination rather than compacting
it to a six-page recovery owner. Reader0
  `9e0f3317f0836668396dbe53ccd3700558e62695` then restored cold contextual
  cross-spine recovery but retained a probe-local residual phase and only one
  prepared page. Reader0
  `e362378feb772dd27ac85a6af25dd95283fc4eba` restores the stable cold
  cross-spine producer: two reverse-capacity evidence anchors drive a forward
  build capped at seven pages, whose exact terminal page plus up to five
  predecessors are copied into prepared ownership before movement. Retained
  history publishes the same bounded exact suffix. Failed staging, probing, or
  final publication preserves the current page, frame, prepared state, and
  retained ownership atomically. The current pin
  `8eb1db66786c588bbe963552d7e78a7cb8fbdacc` additionally revalidates older
  cross-spine provenance only after an exact page command commits and retains
  a valid reverse breadcrumb plus its linked complete suffix ahead of ordinary
  full-ring speculation.
Dirty executable diagnostics now prove the connected production path at this
pin. The direct runner completes 64 forward plus 64 exact reverse moves with
128/128 nonempty canonical frames. Two consecutive accepted queue processes
complete 13+13 pages with four cross-spine transitions, 26/26 stable
presentations, 12+12 logical preparation calls, 4+1 builds, 8+11 already-ready
results, exactly 0+2 cross-spine already-ready results, and 0+0 preparation
failures. Terminal post-key-up preparation is suppressed. No synchronous
window rebuild or adjacent measurement occurs.
The host does not skip, synthesize, or filter a replacement page.

The pre-`8eb1db6` cold queue diagnostic already had 0+0 preparation failures
but exceeded the unchanged visible-interval ceiling at 102.509 ms. It remains
deliberately excluded. Two current-pin processes passed the original 84 ms
ceiling with forward/backward visible maxima of 60.250/63.688 ms and
62.144/62.305 ms; navigation-preparation maxima were 33.636/10.900 ms and
33.832/9.964 ms. The clean two-process wrapper remains required. No timing or
failure budget was loosened.

## Clean implementation evidence

The required implementation commit is
`01f77dea1a519475dbf43c639c34a7c15f5779e0`, **Lock retained cross-spine
repeat reuse**. From that clean tree, the page-turn wrapper rebuilt strict and
ran two fresh real-queue processes plus two fresh direct processes. Both
wrappers passed; their recorded `git_status` arrays are empty.

The exact dependency set was Reader0
`8eb1db66786c588bbe963552d7e78a7cb8fbdacc`, UI0
`cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, Readerview0
`26d836390fce2de64198430fa82d6f660fc7fc07`, and zero_foundation
`3eab21c06c4aa0b4915f9e7fcb3830ba1688451f`. The exact 955125-byte GOTM book
retained SHA-256
`D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.
The rebuilt 2484736-byte executable has SHA-256
`AD9E160EC3BB3AA07D0ECA4DDE475FF3769974343C72446BB4BBF9D57674DA72`.

Both queue processes reported calls `12+12`, builds `4+1`, already-ready
`8+11`, cross-spine already-ready `0+2`, failures `0+0`, and prepared moves
`5+4`. Their forward/backward visible maxima were 63.515/65.960 ms and
64.732/63.536 ms; logical preparation maxima were 32.643/10.307 ms and
33.462/10.504 ms. Both stayed below the unchanged 84 ms interval ceiling and
had no synchronous window rebuild or adjacent measurement. Both direct
processes completed exact 64+64 traversal; maximum held action totals were
23.341 ms and 28.150 ms.

The queue summary is 19279 bytes, SHA-256
`9FD9A048996486DA3E8525FB16991871F5EBB250E416FE786DDA8C9464D174DB`.
The combined summary is 30159 bytes, SHA-256
`1F5F80CCC7A153352FC4D2ADD0592602CCD581964E8F55F16015C4DEA339E82D`.
The frozen external Re10/cross-host canonical-range oracle passed separately;
it is not reclassified as queue-local evidence.

Current Reader0 SHA: `8eb1db66786c588bbe963552d7e78a7cb8fbdacc`
Final evidence directory:
`local\validation\navigation-recovery-slice6\lectern0_01f77de_8eb1db6_final`
Final summary SHA-256:
`1F5F80CCC7A153352FC4D2ADD0592602CCD581964E8F55F16015C4DEA339E82D`

No promotion or Lectern0 remote creation is authorized by this record.
