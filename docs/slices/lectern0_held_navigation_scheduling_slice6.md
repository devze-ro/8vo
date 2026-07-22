# Lectern0 held-navigation scheduling recovery slice 6

Date: 2026-07-21

Status: host implementation and clean two-process acceptance complete at
implementation commit `01f77de`; Reader0 dependency reconciled at `8eb1db6`;
no promotion performed

## Reproduced host scheduling problem

The previous held-navigation repair prepared and rasterized the next page on
Lectern0's page-repeat timer before checking whether a repeat action was due.
That work could occupy the interval immediately preceding an accepted action at
the reported 1917-by-1137 viewport. The production-loop audit also found that
queued native repeats could delay timer/paint work, the render gate cleared
before stable surface presentation, and the 250 ms progress debounce could
write state and catalog data synchronously while the key remained held.

The first queue-loop repair still used successful presentations as its clock.
It counted 24 complete presentations before the first repeat and three more
before every subsequent repeat. A real Lectern0 presentation costs roughly
20--35 ms at the acceptance viewport, so the nominal three-frame interval became
roughly 60--100 ms. That was the remaining host-only reason Lectern0 felt slower
than Re10: the scheduler rendered idle frames merely to advance its counter.

Independent review also closed these correctness gaps:

- only an invalid-region `WM_PAINT` owned by the main Lectern0 window may be
  reserved for synchronous presentation; auxiliary-window and null-region
  paints must be dispatched normally;
- Ctrl, Alt, system-key, focus-loss, and deactivation transitions cancel the
  active stream and suppress stale repeats for its former key until key-up;
- the previous presentation gate lived inside active-repeat state, so key-up
  or direction reversal could admit another Reader0 move before the accepted
  frame was visibly stable;
- repeat could be armed after a handled key route even when the immediate page
  move failed; Shift was not part of the cancellation contract; and
- a move remains outstanding until rendering, surface presentation, Reader
  View action application, and any required follow-up frame are complete; and
- the deferred persistence proof covers the paired state-plus-catalog host
  save, not only the state write; each file is replaced atomically, without a
  cross-file transaction or rollback claim.

Reader0 still owns every canonical page transition. This slice changes only
Lectern0's Win32 scheduling, presentation gate, queue ownership, and host
persistence timing.

## Locked scheduling contract

Lectern0 retains ownership of Win32 key handling and repeat timing. For an
eligible Left or Right key:

1. The first physical keydown routes through the existing Reader View focus and
   action path and moves immediately. Repeat is armed only when Reader0 returns
   `Ok` and Lectern0 captures a valid nonzero canonical page/frame.
2. The host arms a monotonic wall-clock due time at 24/60 second (400 ms). It
   does not render or present idle frames to count that delay.
3. A repeat action is emitted only after its due time and only when the previous
   physical or repeated action has reached complete, stable presentation.
   Therefore no more than one page action is outstanding.
   This gate is independent of active-repeat lifetime and survives key-up,
   focus/modifier cancellation, and direction changes. At most one new physical
   page action is retained in a bounded pending slot and is executed only after
   the preceding presentation; key-up disables repeat arming for that pending
   action without discarding the requested tap.
   The gate identity binds the Reader document id and generation, layout
   generation, exact canonical spine/page byte range, Reader frame generation,
   and host capture generation. Image-only pages additionally bind the decoded
   visual-unit/placement signature because their canonical page uses a sentinel
   range rather than visible UTF-8 bytes.
4. After an action is emitted, the next due time is rebased from that actual
   emission at 3/60 second (50 ms). A late frame never advances the deadline
   repeatedly and never causes a catch-up burst.
5. `MsgWaitForMultipleObjectsEx` waits for either the due time or new input.
   Only native repeats for the active key and direction are coalesced. Other
   messages keep their normal dispatch behavior.
6. The queue drain is bounded. A main-window paint is reserved only when that
   window owns a real update region; a real visible auxiliary window and a
   posted null-region main-window paint lock the two pass-through cases. Before
   optional Reader0 logical tail preparation, the host peeks without removing;
   any queued message returns control to this same FIFO drain. This preserves
   its cap, ordering, paint ownership, and accounting, while queued key-up or
   cancellation clears the now-useless pending preparation.
7. `timeBeginPeriod(1)` and `timeEndPeriod(1)` remain paired around the active
   loop so a 50 ms deadline is not stretched by the coarse platform default.
8. Ctrl, Shift, Alt/system-key, focus-loss, and deactivation transitions cancel
   repeat and consume stale repeats for the cancelled key until key-up, without
   clearing the independent action-to-presentation gate.
9. Reader0-only direction-aware logical preparation may run after complete
   stable presentation when deadline slack remains. Same-spine results pass
   Reader0's public exact-adjacency validator. Cross-spine results remain
   Reader0-owned prepared-ring facts; the host requires a public
   `AdjacentSpine` or `AlreadyReady` result, a nonempty range, and strict
   movement in the requested document direction rather than incorrectly
   re-validating that private owner through the active pagination.
10. Directional page-frame construction, adjacent raster warming, and
    synchronous persistence do not run speculatively while the key is held.
    Ordinary bounded idle warming and the existing persistence debounce resume
    after key-up or cancellation.

The presentation gate is bounded host state, not mirrored reader navigation.
It is set after an accepted action and cleared only after the production render
path produces a complete stable presentation. No callback, vtable, provider
table, process-global mutable state, or shared scheduling API is introduced.
Capture failure cannot silently release the gate: a failed page capture retries
the same canonical page, and a failed same-page refresh requires a newer host
capture and Reader frame epoch before presentation can complete. Opening a book
remains a successful catalog/import transaction even when its first capture
fails; the host retains the open-book state and recovers the frame instead of
leaving a half-imported catalog entry or rolling back a successful Reader open.

## Real Win32 queue and cadence lock

The dedicated regression locks the exact 955125-byte GOTM EPUB and SHA-256 in a genuinely
visible offscreen 1917-by-1137 Win32 window and exercises the production window
procedure and message loop. For each direction it requires:

- one connected forward traversal followed by a true reversal from its endpoint;
- one immediate physical move plus twelve sustained repeated moves per direction;
- 13/13 preflight page ranges per direction, crossing
  the Chapter 1/2 spine boundary in both directions and returning exactly to the
  starting canonical range;
- exactly thirteen complete action presentations per direction, strict
  action/presentation alternation, zero overlapping actions, and zero idle
  presentations;
- stable-visible timestamps for the immediate page and every repeated page,
  with 26/26 stable presentations and 22/22 visible-interval samples; timing is
  measured when the production surface is actually stable, not when an action
  or frame is merely emitted;
- 26/26 exact canonical GOTM page/frame identities with complete row coverage
  and zero authoritative page counts as hard failures;
- an independent fixture-scoped raw-source oracle (not a replay-derived range)
  applies to long-form text spines whose active source contains at least 128
  bytes. There it rejects a canonical `first_byte` on a UTF-8 continuation byte
  or inside an ASCII/non-ASCII word, including after typographic apostrophes and
  hyphens, and requires at least eight non-whitespace bytes across at least one
  completely covered style row. Short publisher headings are accepted only with exact canonical
  page/frame identity and complete row coverage; image-only pages additionally
  require their exact visual identity;
- eleven measured repeat intervals per direction, recording minimum, average,
  and maximum;
- native repeated `WM_KEYDOWN` coalescing throughout the stream;
- exactly twelve optional Reader0 logical preparation calls per direction for
  thirteen presented canonical pages. Queued key-up suppresses the useless
  terminal tail, preparation failures must remain 0+0, the two backward
  cross-spine preparations must be structurally reported `AlreadyReady`
  (`navigation_prepare_cross_spine_ready=0+2`), and logical-tail time remains
  separately reported from the strict action move/frame budget and unchanged
  visible-cadence limits;
- one real auxiliary-window invalid-region paint and one real main-window
  null-region paint dispatched through the production queue; and
- a bounded drain batch no larger than 32 messages.

The queue's replay-derived expected ranges prove only queue ordering and are
reported as `queue_range_oracle=self_derived_order_only`. They are never
accepted as page-boundary truth. The frozen Re10/cross-host canonical-range
oracle remains independently mandatory and is deliberately reported as not
evaluated by this host-only runner:
`independent_range_oracle=external_frozen_re10_required_not_evaluated`.

One real-queue mutation pass attempts seven document/page mutations while a
presentation is outstanding: native picker/open, history navigation, settings
change, location seek, Reader View next-page action, explicit repagination, and
book close. All seven must be dropped or boundedly deferred without changing
the committed Reader page, and cancellation must occur exactly once. Targeted
failure passes also require page-capture recovery, same-page fresh-epoch
recovery, successful open/catalog recovery, and exact gate identity on the
cover plus all three map pages (`image_page_gate=4/4`).

Five additional real-queue passes cancel with focus loss, Ctrl, Shift,
Alt/system-key, and app deactivation. Each queues one stale repeat after cancellation and
requires it to be consumed before key-up clears the suppressed key.

Every pass uses unique temporary state and catalog paths seeded through the
production save path. During the hold, both files must retain the same modified
time and bounded content hash and the paired-save counter must remain
unchanged. After stop, exactly one paired host save must advance the
counter, both modified times, and both content hashes. This is required across
the two directions, five cancellation routes, and mutation-gate pass: 8/8
paired saves, 8/8 unchanged holds, and 8/8 post-stop advances. Cleanup must
remove the sandbox so the probe never selects user AppData.

The timing contract remains strict:

- nominal first repeat: 400 ms;
- nominal repeat interval: 50 ms;
- nominal twelfth-repeat emission: 950 ms;
- first/end tolerance: the existing 200 ms aggregate bound;
- interval range: 25--84 ms while minimum, average, and maximum remain
  explicitly reported; and
- complete action-frame budget: less than 64 ms;
- sustained move/frame preparation: less than 16.667 ms;
- sustained software render: less than 48 ms; and
- sustained surface present: less than 16.667 ms.

The smoke also reports bounded per-action diagnostics. It separates the first
presentation from steady repeated actions and records move/frame preparation,
software render, and surface-present averages and maxima. These metrics remain
acceptance evidence; they do not change the scheduling policy.

## Old-pin isolation result

Structural verification currently uses a clean detached Reader0 dependency at
`4d521e19a1c9c708ea76010e6b113bf9a1401a98`. The strict MSVC C11 `/W4 /WX`
build and architecture audit pass. On that provisional pin, the real queue
reaches 13/13 exact forward ranges, reports all seven mutation exclusions, all
three capture-recovery cases, and the four image-only identities, but the strict
preparation/render/visible cadence budgets are red and therefore the backward
timing leg is not accepted. Independent Re10 evidence established that the
eight-byte, one-row Prologue heading is legitimate, so the corrected direct
runner now completes the exact 64-forward/64-backward canonical traversal on
the provisional pin. It subsequently remains red at checkpoint 44 because the
required prepared adjacent-page cache reuse is absent. These results are
retained as provisional performance evidence; the host does not skip or
synthesize pages.

The later Reader candidate
`3343c3157211a9310ca25dfe362f69c174a9f894` is also retained only as failed
diagnostic history. The exact GOTM direct runner failed at backward step 5:
the retained predecessor was `18:10924-11857`, but reverse recovery rephased to
`18:11686-11857` and returned `WindowRebuildFailed`. The real queue likewise
failed its connected 13-forward/13-backward round trip. Reader0
`9c944411174e7afdc48b63c49fe723262a57d2bc` restored the widened-window/
immediate-anchor sequence and fixed split-page staging, but two fresh Lectern0
processes still failed at backward step 7 from `18:10052-10924`. Its six-page
widened rebuild placed the exact predecessor `18:9265-10052` in the final
incomplete-tail slot. Reader0
`f74ce20bacf10ebb02f3054972c6d73c686c0361` restored the historical seven-page
context attempts and compacted the accepted result into the bounded six-page
publication. Reader0
`a7af0fa618d7a8a5f4008207e9d548670ea6f200` additionally preserves semantic
navigation across zero-byte physical visual pages and proves bounded active or
prepared ownership in both directions, but is retained as an intermediate
failed checkpoint. Reader0
`d0cb70ddbea6822c23c23cc4746590cbab5c62fb` then restored the frozen bounded
source-page boundary ring, success-staged exact-range retention, exact retained
page materialization, prepared inverse preference, and failure-atomic wide-owner
compaction across arena rotation. It is a historical recovery checkpoint, not
the current dependency. It was followed by
`82754e8` (sustained prepared-navigation streams), `093b113` (exact prepared
navigation boundaries), and `6b8722a` (exact reverse pagination boundaries).
Reader0
`721981e340977bba559702c0d43f0428475f54a0` additionally required a complete,
byte-zero focused-spine pagination for semantic restore and retains that
complete owner instead of compacting it to a six-page recovery publication.
It rechecks retained history after proven empty or suppressed split spines.
Reader0 `9e0f3317f0836668396dbe53ccd3700558e62695` restored cold contextual
cross-spine recovery but still accepted a probe-local forward phase and
  published only the terminal page. Reader0
  `e362378feb772dd27ac85a6af25dd95283fc4eba` instead requires two
  reverse-capacity evidence anchors, rebuilds forward with a seven-page cap, and
  publishes the exact terminal page plus up to five predecessors. Retained
  cross-spine truth uses the same bounded suffix ownership, so later held PageUp
  turns remain resident. Arbitrary unanchored tails remain forbidden and failed
  final publication preserves both rings and the visible owner. The current
  exact commit `8eb1db66786c588bbe963552d7e78a7cb8fbdacc` revalidates only older,
  exact committed-source provenance and prioritizes the retained reverse
  breadcrumb plus its linked suffix over ordinary prepared-ring speculation.

## Current-pin dirty diagnostic

Against exact Reader0 `8eb1db6`, the strict dependency guard, architecture
audit, and MSVC C11 `/W4 /WX` build pass. The dirty direct executable completes
64 forward plus 64 exact reverse moves, returns to its starting range, and
produces 128/128 nonempty canonical frames with zero zero-page, orphan-text, or
invalid-word-start findings. Its held 2+2 action proof remains render-gated with
a 27.245 ms maximum action-plus-render time.

The corrected real queue makes 12+12 logical preparation calls: terminal
post-key-up work is suppressed, builds are 4+1, already-ready results are 8+11,
cross-spine already-ready results are exactly 0+2, and preparation failures are
0+0. Two consecutive accepted processes complete
13+13 pages, 26/26 stable presentations, four cross-spine transitions, and no
synchronous window rebuild or adjacent measurement. Their forward/backward
visible-interval maxima are 60.250/63.688 ms and 62.144/62.305 ms; navigation-
preparation maxima are 33.636/10.900 ms and 33.832/9.964 ms.

The pre-`8eb1db6` cold diagnostic already had the corrected 0+0 functional
result but reached a 102.509 ms backward visible interval. It is not accepted
evidence: the unchanged 84 ms ceiling rejected it, and no timing or failure
budget was loosened. The clean two-process wrapper remains the authoritative
final host gate.

The earlier two-move, 28-presentation result and its 448-repeat evidence are
superseded. They proved the first queue repair but could not detect the
presentation-count clock defect or sustained outliers.

## Final acceptance gate

Against the exact Reader0 pin
`8eb1db66786c588bbe963552d7e78a7cb8fbdacc`:

All six prescribed steps passed. The dependency guard, architecture audit, and
strict MSVC C11 `/W4 /WX` build were green; dirty direct and queue diagnostics
passed; implementation commit
`01f77dea1a519475dbf43c639c34a7c15f5779e0` was created without promotion;
and the clean page-turn wrapper then ran two queue plus two direct processes.

The queue runs each reported calls `12+12`, builds `4+1`, already-ready
`8+11`, cross-spine already-ready `0+2`, failures `0+0`, and prepared moves
`5+4`. Forward/backward visible maxima were 63.515/65.960 ms and
64.732/63.536 ms; logical preparation maxima were 32.643/10.307 ms and
33.462/10.504 ms. Both direct runs completed exact 64+64 traversal, and their
maximum held action totals were 23.341 ms and 28.150 ms. No timing, failure,
queue, frame, render, or present budget was loosened.

The exact dependency set is Reader0 `8eb1db66786c588bbe963552d7e78a7cb8fbdacc`,
UI0 `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, Readerview0
`26d836390fce2de64198430fa82d6f660fc7fc07`, and zero_foundation
`3eab21c06c4aa0b4915f9e7fcb3830ba1688451f`. The rebuilt executable SHA-256 is
`AD9E160EC3BB3AA07D0ECA4DDE475FF3769974343C72446BB4BBF9D57674DA72`.

Final evidence directory:
`local\validation\navigation-recovery-slice6\lectern0_01f77de_8eb1db6_final`

Queue summary SHA-256:
`9FD9A048996486DA3E8525FB16991871F5EBB250E416FE786DDA8C9464D174DB`

Final summary SHA-256:
`1F5F80CCC7A153352FC4D2ADD0592602CCD581964E8F55F16015C4DEA339E82D`

The implementation and evidence records remain separate non-promoted commits,
and promotion still requires explicit approval.

## Final reduced-Reader qualification

The scheduling contract is unchanged at final Reader0
`df65b516a095bba42b5310dc287d2b392ed9d52e`. From clean Lectern0
`af4e701116ae4dd79a4eddb2d66f14be64bec549`, the strict guard, architecture
audit, and `/W4 /WX` build passed. The wrapper then used `-SkipBuild` solely
to keep its two queue and two direct runs on that one already-hashed binary.
The exact binary hash is
`EADD05D5FE1EEB5AD61D5E6BB7D5B92FD22AFD42998E92C78667B462E9561E7E`.

Each queue run retained the locked `12+12` calls, `4+1` builds, `8+11`
already-ready results, exact `0+2` cross-spine readiness, `5+4` prepared
moves, zero failures/recovery, and 26/26 stable presentations. Visible maxima
were `78.919/72.383 ms` and `68.921/72.768 ms`; direct held-action maxima were
`37.917 ms` and `34.204 ms`. No interval, frame, render, present, failure, or
ownership budget changed.

Evidence directory:
`local/validation/navigation-reduction/final_df65b51_2`

Queue summary SHA-256:
`B85959292FF814CC91CBBC58601169475CFBD06DA073DA4206C07325AEF3E045`

Combined summary SHA-256:
`092BDF70F477EB59B1EE34A975BE7BC64A6071D3F58C97C072A57B5389DDB37B`

This completes the clean held-scheduling gate; promotion still requires the
user's explicit approval.
