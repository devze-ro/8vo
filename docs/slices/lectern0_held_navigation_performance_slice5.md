# Lectern0 held-navigation performance slice 5

Date: 2026-07-20

Status: implemented and validated on a dedicated branch; not promoted

## Reproduced regression

With the exact `gotm_new.epub` acceptance book, a physical Left or Right
keydown moved immediately, but every timer-driven repeat rendered the target
page cold. Lectern0's adjacent-page worker returned without doing work while
`page_repeat_active` was set. The 24-frame initial repeat delay therefore
contained no preparation opportunity, even though it was long enough to
prepare the next canonical page without delaying an accepted repeat action.

The earlier performance evidence timed only the final shaped-text raster loop.
It did not include construction of presentation records and draw commands.
That hidden setup was expensive because the Lectern0 styled-row adapter:

- measured every growing grapheme prefix, producing quadratic shaping work;
- measured the final draw chunk even though no later chunk needed its width;
  and
- rebuilt shaped display geometry for highlights on pages with no annotation,
  Find, or selection overlay.

The original held-input deferral was therefore a host-policy defect, while the
expensive work inside each preparation step was an adapter hot-path defect.
Reader0's canonical page preparation contract was already sufficient.

## Ownership and bounded repair

Reader0 remains the sole owner of canonical pagination, page ranges, adjacent
spine decisions, and caller-owned speculative frames. This slice does not add
or change a Reader0 API. Lectern0 continues to own its Win32 repeat cadence,
presentation records, renderer cache, and one-page pixel snapshot.

On the first physical keydown Lectern0 still moves immediately and starts the
existing 24-frame initial delay. During that delay, and before every later
accepted repeat point, the page-repeat timer gives the directional adjacent
worker one bounded step. Forward preparation uses Reader0's prepared forward
range. Backward preparation consumes the exact canonical prior page returned
by `epub_reader_prepare_navigation` and rejects any result that is not strictly
before and adjacent to the current page.

Held preparation is limited to one target page, at most 64 shaped text
commands per timer step, the existing 8 ms ordinary work budget, and the
existing 4096-by-4096 pixel cap. It uses the repeat timer itself, so correctness
does not depend on ordering between two Win32 timers. Key-up, focus loss, Close
Book, or window destruction stops repeat preparation. If normal idle warming
is still useful after key-up, the existing forward-only 16 ms worker resumes
with its four-command steps and four-page cap.

The row adapter now follows the Re10 benchmark's shaping granularity:

- a non-justified LTR span is shaped once and its cluster advances provide the
  bounded grapheme stops;
- a justified LTR span is shaped as word/space tokens, matching the segmented
  widths used by Re10's justified painter;
- small-caps, unsupported-shaping, and RTL cases retain the exact bounded
  prefix fallback; and
- the final draw chunk is not measured after it is pushed.

Overlay geometry is skipped when there is no annotation, active Find result,
or selection to paint. Painting, hit testing, selection, and overlays still
consume the same display-row records when overlays are present.

No callback, provider table, vtable, event bus, dependency injection,
process-global mutable state, or persistent heap ownership was introduced.
Shaping uses the existing bounded scratch-arena convention; durable display
records remain in caller-owned arrays and bounded renderer caches. UI0,
Readerview0, Reader0, zero_foundation, and Re10 are unchanged by this slice.

## Durable and synchronization boundary

Directional preparation, repeat frame counters, shaped-text cache entries,
draw commands, and the adjacent pixel snapshot are ephemeral host state. They
must not be persisted or synchronized. Durable reading progress remains a
canonical Reader0 location inside Lectern0-owned catalog state, preserving the
future Lectern0/Re10 synchronization boundary established by the library
reference lock.

## Exact acceptance

The implementation commit is
`77cb77dab3705389788a21fbcc7d4c7991410a95`. It pins:

- Reader0 `2ffae376d9aa34d762f21d7ee61ea79ca613b151`;
- UI0 `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`;
- Readerview0 `26d836390fce2de64198430fa82d6f660fc7fc07`; and
- zero_foundation `3eab21c06c4aa0b4915f9e7fcb3830ba1688451f`.

The exact-book runner uses the 955125-byte EPUB with SHA-256
`D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.
It now searches for the `1161st Year` chapter page, simulates a real immediate
physical move followed by the 24/3 timer cadence, and requires two prepared
forward and two prepared backward repeat moves. Every held move must hit the
directional snapshot and remain pixel-exact against a cold render.

Clean two-process evidence:

- summary:
  `local/validation/held-navigation-performance-implementation/summary.json`;
- summary SHA-256:
  `A1066007FB7231ACEFFB63F37F737345A9AC12DEA89D8D1DBBBCCC378E8D6078`;
- 64 forward and 64 backward canonical moves in each process;
- 4/4 held snapshot hits and 4/4 held warmed/cold pixel equality per process;
- held warm max: 8.663 ms and 9.059 ms;
- held move max: 0.244 ms and 0.329 ms;
- held cached-render max: 6.526 ms and 8.082 ms;
- ordinary prepared-render max: 15.075 ms and 15.668 ms;
- 16/16 ordinary prepared snapshot hits and 16/16 pixel equality; and
- zero draw, raster-cache, or run-cache overflow.

The four shaped-text overflow events remain the previously accepted bounded
cache-eviction counter from scanning this typography-heavy book. They do not
overflow storage or change the warmed/cold output.

Strict MSVC C11 `/W4 /WX`, dependency guards, and the architecture audit pass.
Clean retained evidence also passes:

- exact publisher typography and all locally available font families,
  summary SHA-256
  `FF1161CBCEAB34479B0BA27D16CC08BA8139C1774EB564A04382CD04DECF1320`;
- Reader View, repeat 2, stable hash `bbc068e6c290fee1`;
- post-action arrow routing, repeat 2, summary SHA-256
  `A7BD4ED712E1D58E7A1BD9B79E4944EB30AF2C5F245AA87169C974F6DAE10E54`;
  and
- cover plus three maps, repeat 2, frozen 556-by-468 media geometry, summary
  SHA-256
  `ACADF2A3FBEF1197FB3972884229682D587F398BC65AF862033FAC8B6F0DBA78`.

The clean 15-state Re10/Lectern0 matrix rebuilt both hosts in-harness:

- manifest: `C:\Temp\ln5cross_20260720_2\manifest.json`;
- manifest SHA-256:
  `DC84E5A5154C213740E209E87E0F643F115AB0CB3C3C1ED6C48415DE8D4E7A2D`;
- deterministic: 15/15;
- decoded-pixel exact: 15/15;
- exact records: 9/15, with the six established host-record differences still
  pixel-exact;
- exact dependency and clean-tree enforcement: pass; and
- acceptance eligible: true.

## Promotion state

The branch is `codex/held-navigation-performance-slice5`. Promotion has not
been requested or performed. Lectern0 remains local-only and no remote was
created. The protected Re10 extraction worktree was not modified.
