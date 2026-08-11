# Android Port 11: durable annotations and synchronization contract

Status: implementation contract adopted 2026-08-07. The accepted first three
local slices are bookmarks, durable multi-color highlights, and note editing/
recoverable drafts/conflict UI. The fourth local slice freezes and qualifies
actor-neutral portable bytes plus the deterministic offline join, and the fifth
qualifies disconnected annotation coordination. The sixth, independent local
slice implements and API 36/API 34-qualifies provider-neutral reading positions
and Kindle-style confirmation without extending the annotation record or
coordinator families. The seventh, appearance-only local slice is accepted on
API 36 with canonical `O1PF` whole-profile lanes and private atomic `O1SS`
review transactions. The eighth independent local slice is accepted on API 36
for the existing global Chapter/Page/Location/Percentage progress-display
choice, with canonical
`O1PC` lanes, private atomic `O1PS` review transactions, and the pre-existing
local `O8PG` record remaining three separate formats. The ninth independent
local slice is accepted on API 36 for provider-neutral `O1LC` catalog discovery,
private `O1LS` review decisions, exact `O1BM` manifests, and the private `O1BQ`
managed-transfer/cleanup queue. These remain separate from every earlier `O1`
family and from Port 6's local catalog. Google Drive remains disconnected.

## Boundary

8vo owns annotation workflows, durable state, migrations, merge policy, UI,
and future provider coordination. Reader0 remains authoritative for EPUB
interpretation, UTF-8 text bytes, pagination, selection, locations, and
navigation. Readerview0 and UI0 may supply public projection, state, semantic,
panel, and color mechanics; they do not own annotation records or persistence.

Android identifies a book by the lowercase 64-hex SHA-256 digest of its managed
EPUB bytes. An annotation anchor is Reader0-owned data copied by value:

- point: `spine_index`, `text_byte_offset`;
- same-spine range: `spine_index`, `text_byte_start`, `text_byte_end`;
- every byte is a canonical UTF-8 text byte, never a Java character index,
  page number, rendered coordinate, CFI invented by the host, or file path.

The host may ask Reader0 for section labels, excerpts, location summaries, and
navigation. Those projections are replaceable metadata; they cannot change an
anchor. A navigation is committed to history and panel state only after the
target frame is successfully presented.

## Portable records

The provider-neutral state is a canonical, versioned collection of record
envelopes. It is suitable for local files, export, and a later provider adapter.

| Field | Contract |
| --- | --- |
| `record_id` | Stable lowercase 32-hex identity. A bookmark ID is the first 128 bits of SHA-256 over a bookmark-specific namespace, book digest, and point anchor. Highlight and note IDs use 128 bits from a cryptographic random source. |
| `kind` | `bookmark`, `highlight`, or `note`; immutable for an identity. |
| `book_digest` | Lowercase 64-hex EPUB digest; immutable for an identity. |
| `anchor` | Immutable validated Reader0 point or same-spine range. Highlight ranges are nonempty. Notes may use a point or a nonempty range and may reference a stable highlight ID in the same book. |
| `payload` | Bookmark label/excerpt/star; highlight color/excerpt/star; or note text/star. Strings are UTF-8, length-bounded, and preserved exactly after validation. |
| `mutation` | Stable ID, stable actor ID, positive actor counter, causal context, operation (`put` or `delete`), and the complete payload needed to recover that head. |
| `frontier` | Per-record vector of the greatest incorporated counter for each actor. It compacts causally dominated history without erasing live concurrent heads. |

Wall-clock time may be retained only as display metadata. It never chooses a
winner, establishes causality, generates an identity, or orders canonical
bytes.

The portable container has magic `O1AP` and deliberately excludes the importing
installation's actor/counter. Android's private `O1AN` wrapper carries those two
local values and must never be uploaded or treated as an interop format. The
complete byte layout, hash preimages, canonical payload rules, causal validity,
and golden fixture are frozen in
[`android_port11_portable_annotations_v1.md`](android_port11_portable_annotations_v1.md).

Bounds for version 1 are part of the file contract: 2,048 record envelopes,
16 actors per record frontier, eight concurrent heads per record, a 16 MiB
private-file bound, 256 UTF-8 bytes for a label, 512 for an excerpt, and 4,096
for note text. Bookmark and highlight colors are a fixed four-value semantic
palette, not stored ARGB values. Exceeding a bound rejects the whole candidate
mutation or merge and preserves the prior state. The actor-neutral portable
container is bounded to 16 MiB minus its 44-byte private-wrapper difference so
every accepted portable snapshot remains publishable without advancing the
adopted private `O1AN` v1 bound.

## Mutation and merge

Each installation has one random stable actor ID and a monotonically increasing
local counter stored in the same atomic state. A local mutation:

1. copies the current record frontier and live heads as its causal context;
2. advances the local actor counter without overflow;
3. creates a canonical mutation ID from the complete mutation bytes;
4. replaces only heads it causally observes; and
5. publishes the complete candidate state atomically before the UI claims
   success.

For non-equivocating causal histories whose intermediate and result fit every
bound, merge is pure, commutative, associative, and idempotent:

1. validate both complete inputs, immutable envelope fields, justified causal
   frontiers, maximal heads, unique actor dots, canonical kind payloads, and
   same-book attachments;
2. reject an exact mutation identity associated with unequal bytes;
3. take the component-wise maximum frontier;
4. retain a head shared exactly by both sides; retain a side-only head only when
   the opposite frontier has not incorporated its actor/counter dot;
5. retain all non-dominated concurrent heads, sort actors, heads, and records by
   unsigned canonical byte order, and revalidate every bound.

A record is visibly present when it has at least one live `put` head. A
concurrent `delete` head is retained as conflict state but does not silently
erase an unseen concurrent put. Concurrent bookmark/highlight heads use a
deterministic projection and expose a conflict for later resolution. Every
concurrent note body remains available; the UI must show each body and let the
user save a new mutation that causally observes all heads. A resolved delete is
a durable tombstone, not physical removal. Physical compaction requires proof
that every participating replica incorporated the tombstone and is outside this
port.

Capacity makes the binary join deliberately partial. `LIMIT` preserves the
prior bytes and in-memory state exactly; a future sync coordinator must retain
and retry that same remote snapshot after any successful merge or resolution
that can reduce concurrent heads. An imported frontier ahead of Android's
private counter for its current actor causes a fresh actor rotation before
publication, preserving the old dots without counter reuse or exhaustion.
Structural validation and CRC protect against malformed/corrupt bytes, not a
party able to fabricate schema-valid causal history; provider trust and the
encryption/authentication decision remain launch gates.

## Local persistence and recovery

Android stores one canonical state at `<files>/port11/annotations.v1`. The file
has a magic value, schema version, declared counts, canonical ordering, explicit
lengths, and a CRC32 over all preceding bytes. Loading is all-or-nothing.

Publication writes a bounded same-directory temporary file, flushes it,
synchronizes its descriptor, and uses `ATOMIC_MOVE` with `REPLACE_EXISTING`.
There is no non-atomic fallback. A failed write, sync, or move leaves the prior
published bytes and in-memory state authoritative, keeps the requested action
available for retry, and reports a visible error.

Missing state starts empty without publishing. A known older version is first
fully validated, migrated in memory, and published only through the same atomic
path; no older annotation schema exists yet. A future version is preserved and
blocks mutation instead of being rewritten. Malformed current-version state is
atomically moved to one of three bounded quarantine names before an empty store
becomes writable. If quarantine fails, the store remains read-only and the UI
reports recovery failure. Recovery never silently overwrites suspect bytes.

The catalog, positions, preferences, annotations, and managed EPUBs are
independent atomic files. 8vo does not claim a cross-file transaction.

## Book and layout semantics

- Reflow, rotation, font changes, Activity recreation, and process restart keep
  the same digest and Reader0 byte anchors. Page numbers may change.
- Removing a managed EPUB does not delete its annotations. The Library must
  offer local-data deletion separately. Re-importing byte-identical content
  reconnects the existing digest and records; different bytes are a new book.
- Navigation to a missing, invalid, or no-longer-resolvable anchor fails
  visibly without changing the current position or deleting the record.
- EPUB replacement and repair verify the digest before association. No title,
  path, provider URI, or filename is sufficient identity.

## Android interaction and accessibility

The reader chrome provides an explicit current-location bookmark toggle and an
explicit Annotations entry point; neither workflow is gesture-only. The toggle
uses the last successfully presented Reader0 point. Its label, state,
description, TalkBack announcement, and list update change only after atomic
publication. Failure leaves the prior visual and accessible state exact.

The workspace is a bounded native Android side sheet with a heading, polite
status/error live region, current-book bookmark/highlight/note counts, stable
ordered rows, Go to, Edit, Remove, and Done. Rows expose a useful section/location label,
excerpt, and non-color-only highlight name without making any of them the
anchor. All controls have at least 48dp
targets, deterministic keyboard and accessibility traversal, large-text
scrolling, focus restoration, RTL-aware placement, and a non-animated path
under reduced motion or touch exploration. Opening and closing the sheet do not
repaginate. Back closes it before selection/history/Library behavior.

Go to disables stale mutation/navigation controls while a Reader0 destination
is provisional. The sheet closes only after successful presentation; failure
keeps it open and exposes retry. Remove is durable before the row disappears.
Lifecycle teardown flushes no optimistic annotation state because every record
action is synchronously published or rejected. Incomplete note text is instead
synchronously autosaved to the separate local draft envelope described below.

## First local vertical slice

Port 11 begins with:

- load/recovery of the provider-neutral store;
- toggle at the currently presented Reader0 point;
- deterministic label/excerpt projection;
- current-book ordered list, direct navigation, and removal;
- survival across Activity recreation, process restart, book removal, and
  byte-identical re-import;
- exact rollback on capacity, serialization, synchronization, or atomic-move
  failure; and
- TalkBack, keyboard, 130% text, rotation, reduced-motion, and lifecycle tests.

## Second local vertical slice: multi-color highlights

The next slice reuses the version-1 envelope without migration:

- selection creation copies the active Reader0 `DocSelection` spine/start/end
  values and a bounded excerpt only after the selection frame is presented;
- each new highlight receives a cryptographically random stable record ID and
  one semantic color: `yellow` (0), `pink` (1), `blue` (2), or `orange` (3);
- recolor preserves the record ID and exact anchor while publishing a causal
  update; remove publishes a tombstone before the row or reader projection
  changes;
- the native product renderer receives a bounded, copied projection sorted by
  record ID. For overlapping persistent highlights the lowest canonical record
  ID wins per glyph. Transient selection wins over active search, active search
  wins over persistent highlights, and persistent highlights win over inactive
  search;
- theme-specific ARGB values remain 8vo design tokens. Only semantic color IDs
  persist; Reader0 and UI0 gain no annotation color or persistence API;
- a store failure leaves the exact selection, floating actions, rendered page,
  and workspace unchanged and exposes a retryable error;
- after durable publication, the selected range is cleared and success is
  announced only after the persistent highlight projection is successfully
  presented. Exhausted frame retries retain the durable record, keep the
  selection available, and report that display will retry or recover on reopen;
  and
- the four named color actions are available in the floating selection toolbar
  and on the TalkBack page-content node. The workspace exposes named swatches,
  excerpts, conflict status, navigation, recolor, and removal with large-text,
  keyboard, RTL, reduced-motion, and lifecycle behavior matching bookmarks.

Annotation search/export and Drive transport follow on the same envelope; they
are not simulated in this slice.
No Google account, OAuth client, Drive scope, cloud resource, signing key, Play
Console setting, or provider library is introduced.

## Third local vertical slice: notes and recoverable drafts

The note slice reuses the version-1 portable envelope without migration:

- Add note is a fifth named action on the floating selection toolbar and the
  TalkBack page-content node. It copies only the successfully presented
  Reader0 same-spine range and bounded selected-text excerpt, allocates a
  cryptographically random stable record ID, and opens the native editor;
- note text is nonempty when committed and is capped at 4,096 UTF-8 bytes.
  The editor rejects only the suffix beyond that byte bound and retains exact
  accepted Unicode text. Its multiline field uses UI0's horizontal and
  vertical text-input inset metrics rather than platform-default padding;
- incomplete text is local-only crash-recovery state at
  `<files>/port11/note-draft.v1`. This 8 KiB checksummed version-1 envelope
  contains one record ID, source-head token, book digest, exact anchor,
  optional attachment, excerpt, and body. Every edit attempts a synchronous
  same-directory descriptor-synchronized `ATOMIC_MOVE`; a failed autosave
  keeps the in-memory text exact, shows Retry, and never changes the portable
  annotation record;
- Save first compares the draft's deterministic source-head token with the
  record's current canonical heads. A stale editor is rejected visibly. A
  successful put observes every current head, so explicit conflict resolution
  collapses all displayed versions without consulting device time. Retrying a
  put already published immediately before process death is idempotent;
- the draft is cleared only after the portable mutation publishes. Failure
  leaves the editor, recovered draft, prior durable record, and selection
  available. Cancel explicitly discards the draft without mutating a record;
- the workspace shows ordered note rows, excerpt, exact body, Go to, Edit, and
  Remove. Every concurrent live body is separately visible with a numbered
  `Use version` action; put/delete state is disclosed as a retained conflict;
- removal publishes a causal tombstone before the row disappears. Note
  navigation uses the exact Reader0 spine/start byte and the same
  presentation-gated rollback path as bookmarks/highlights;
- each current-book note has a bounded product-owned visual projection. Native
  rendering places a small theme-accent page glyph immediately after the exact
  Reader0 range end. For a range spanning the current page, the end is clamped
  to the visible Reader0 byte boundary, matching the desktop convention. The
  glyph neither changes text layout nor becomes an alternate anchor. Add and
  Remove publish the durable record first, then gate selection clearing and
  success announcement on the matching marker generation being presented;
  exhausted presentation retries name the note-marker failure and retain the
  durable record for reopen recovery. A marker becomes touchable only when its
  native generation and immutable Java record projection are both presented.
  Its centered target is at least 48dp, a completed tap opens that exact durable
  note in the existing editor, and a different unsaved draft is retained with a
  visible instruction instead of being overwritten; and
- Back/Done may close the editor while retaining its durable local draft for
  recovery. Activity recreation and process restart never manufacture a note
  from a draft, and a draft is not portable or eligible for future Drive sync.

The draft file has the same missing/future-version/quarantine rules as the
annotation file and three bounded quarantine slots. It is deliberately a
separate independently atomic file; 8vo claims no cross-file transaction.

## Fourth local vertical slice: portable bytes and offline join

This slice adds no network permission, account, provider library, background
worker, or UI surface. It:

- exports a canonical `O1AP` record-only snapshot and validates bounded
  untrusted bytes before considering a merge;
- reports merged, unchanged, blocked, invalid, future-version, capacity, and
  atomic-publication outcomes distinctly; an unchanged join performs no write;
- uses the opposite-frontier state join so stale snapshots cannot resurrect a
  resolved note body or tombstone;
- rejects context-ahead/dominated heads, duplicate actor dots, changed anchors,
  unjustified frontier components, dangling/cross-book attachments, unknown
  flag bits, and noncanonical delete/kind payloads; optional note attachments
  remain versioned head payload so concurrent or legacy-empty values converge;
- treats local counter exhaustion as a visible capacity result rather than an
  exception, rotates away from imported self-ahead actor history, bounds
  serialization before allocation can exceed the file limit, and preserves
  recognizable future private versions even when they exceed the current
  schema's size bound; and
- packages the shared Port 6 semantic oracle/golden only in the test APK. re10
  remains untouched because it first needs EPUB SHA-256 plus stable portable
  identities; its path/local-ID database export is not sync state.

The adopted disconnected, provider-neutral annotation coordination boundary is
specified separately in
[`android_port11_sync_coordinator.md`](android_port11_sync_coordinator.md). It
qualifies caller-driven conditional synchronization and private retry/review
state only; it does not connect or authorize a provider.

The adopted provider-neutral reading-position and Kindle-style confirmation
boundary is specified in
[`android_port11_position_sync.md`](android_port11_position_sync.md). Its
bounded `O1RP` lanes and private `O1RS` decisions are independent of `O1AP`,
`O1AN`, and `O1AS`; this local slice uses simulated remote bytes and adds no
provider, account, permission, worker, clock, or Drive connection.

The frozen provider-neutral global-appearance boundary is specified in
[`android_port11_preference_sync.md`](android_port11_preference_sync.md). Its
bounded `O1PF` whole-profile lanes and private `O1SS` review/transaction state
are independent of the annotation and position families. This seventh slice is
appearance-only.

The accepted provider-neutral global progress-display-choice boundary is
specified in
[`android_port11_progress_sync.md`](android_port11_progress_sync.md). Its
bounded canonical `O1PC` lanes, private atomic `O1PS` identity/review/pending
state, and the existing product-local `O8PG` choice are independent formats and
independently atomic files. This eighth slice neither reuses another Port 11
family as a generic sync framework nor connects a provider.

## Offline gates before Drive

Annotation provider work may begin only after deterministic tests prove canonical
round-trip, mutation rollback, restart, corruption/future-version handling,
digest re-import, merge order independence, idempotence, associativity,
concurrent put/delete retention, concurrent-note recovery, and every stated
capacity bound. Emulator UI tests must then pass toggle/list/go-to/remove,
presentation failure, accessibility, recreation, process restart, and the
ordinary Port 7-10 regression matrix before a physical device is requested.

The fourth local slice closes the annotation wire/join gate on API 36. The
fifth backend-only slice closes the disconnected conditional-coordination and
durable retry/review gate. The sixth local slice closes only the separate
position wire/merge/presentation-confirmation gate. The seventh closes the
appearance-only wire/store/review/presentation gate on API 36. The eighth closes
the separate global progress-choice wire/store/review/presentation gate on API
36. The ninth closes the local-only catalog-discovery and managed-transfer
wire/store/review/restart gate on API 36. It does not close the concrete-provider,
authorization, account binding, network, permission, encryption, or provider-
threat-model gates, and it does not authorize a Drive connection.

The later Drive adapter will use `drive.appdata` only for hidden manifests and
portable state and `drive.file` only for EPUBs the user approves in a visible
8vo folder. It must provide explicit opt-in, Sync now, progress, errors,
conflicts, retry, metered-network policy, disconnect/revocation, and separate
local/cloud deletion. Broad Drive scope, a proprietary 8vo account, and an 8vo
backend are excluded. The product must make an explicit encryption/threat-model
claim decision before launch.

## Current local-slice validation

On 2026-08-08, the fourth local slice passes the unchanged exact dependency
guard and architecture audit and builds debug plus instrumentation APKs for
`x86_64` and `arm64-v8a`. The verified API 36 x86_64/QEMU target has a 192 MiB
heap growth limit. Its acceptance evidence is:

- all eight portable-state tests pass together in 19.834 seconds, covering both
  independently generated goldens, exact byte round-trip/restart, actor-neutral
  import, six-order three-replica convergence, stale replay, versioned note
  attachments, empty tombstones, explicit note resolution, hostile semantic
  inputs, bounded-join retry, atomic rollback, and private-actor rotation;
- the exact 16 MiB private/16 MiB-minus-44 portable file boundary and 2,048-
  record case also pass alone in 21.441 seconds on that bounded heap, while the
  smaller inclusive/+1 capacity matrix passes independently;
- the pre-existing annotation store passes 8/8 and full note integration passes
  3/3; the clean ordinary matrix, with the external restart probe deliberately
  excluded, passes 127/127 in 258.130 seconds;
- the separate seed, confirmed force-stop/no-surviving-process, and fresh-
  process restart driver passes in 4.191 and 2.487 seconds; and
- the emulator crash buffer is empty. The production APK contains no golden
  fixture, while the instrumentation APK contains exactly the seven intended
  Base64/JSON/hash/README assets.

The serializer stops at its bound before an oversized allocation, and a
capacity-rejected merge is an exact no-op whose input remains eligible for
retry after causal resolution. Recognizable future private files remain
byte-exact/read-only even above the current size ceiling. Schema-valid imported
dots ahead of Android's private actor counter cause a fresh private-actor
rotation instead of counter reuse or exhaustion. CRC and causal validation are
explicitly not authentication against a party able to fabricate valid history;
provider trust and encryption/authentication claims remain required launch
decisions.

Only `emulator-5554` was installed, cleared, and exercised for this backend-only
slice. The connected API 34 iQOO was visible but untouched; no physical setting,
app data, or animation scale changed. No Google account, Drive API, OAuth or
Cloud resource, signing key, Play Console state, commit, push, or merge was
touched.

On 2026-08-08, the fifth backend-only slice passes the unchanged exact
dependency guard and dual-ABI debug/test build. Its caller-driven coordinator
and private atomic `O1AS` state qualify fixed logical-object reads, bounded
opaque revisions, conditional create/replace, digest-bound first-merge review,
durable remote-recreation review, exact join-limit retention, visible durable
attention, stale callback rejection, uncertain-write recovery, binding reset,
and corruption/future-version blocking without a provider interface, account,
network permission, worker, thread, clock, or UI. On the verified API 36
x86_64/QEMU target:

- all seven coordinator tests pass together in 56.295 seconds;
- the exact 16 MiB-minus-44 remote snapshot stages, reloads, merges, converges
  without a write, and reloads clean in 56.531 seconds on the 192 MiB heap;
- the portable-state, annotation-store, and note-integration regressions pass
  8/8, 8/8, and 3/3; and
- the clean ordinary matrix, excluding only the two externally driven restart
  probes, passes 134/134 in 264.678 seconds and leaves the crash buffer empty.

Only `emulator-5554` was addressed. The iQOO was never targeted and was
disconnected by the user before the final matrix. Google Drive, OAuth, Google
Cloud, Play Console, signing keys, and every physical-device setting remained
untouched. The concrete Drive adapter and the then-unqualified catalog,
transfer, provider, authorization, encryption, and provider-threat-model gates
were then later explicitly authorized slices; the catalog/transfer local gate is
now accepted below, while every concrete-provider and security gate remains
deferred.

On 2026-08-09, the sixth local slice passes the unchanged exact dependency
guard and architecture audit and builds debug plus instrumentation APKs for
`x86_64` and `arm64-v8a`. Its independent `O1RP`/`O1RS` family qualifies exact
EPUB SHA-256 identity, bounded per-device Reader0 anchors, deterministic
clock-free merge/stale replay, strict successful-presentation publication,
durable Go/Stay/Back/retry decisions, process recovery, and a modal accessible
confirmation surface. On API 36 `emulator-5554`:

- all three focused position classes pass 24/24 together in 45.912 seconds;
- the external driver passes its 1/1 seed in 5.264 seconds, confirms force-stop
  with no surviving process, and passes its 1/1 fresh-process verification in
  3.742 seconds;
- the ordinary matrix, excluding only that externally driven probe, passes
  158/158 in 462.672 seconds; and
- the crash and fatal buffers are empty, while the production APK declares no
  permissions and contains no simulated remote snapshot fixture.

The first API 34 iQOO run passed 22/24 and exposed that the primary action could
not accept input focus while the physical device was in touch mode. The fixed
candidate explicitly makes Go there, Stay here, and Retry touch-mode focusable.
The two failed cases then pass 2/2 in 4.388 seconds, and the complete focused
matrix passes 24/24 in 23.245 seconds. The external seed/confirmed-force-stop/
fresh-process driver passes in 1.992 and 1.076 seconds, the ordinary matrix
passes 158/158 in 317.321 seconds, crash/fatal buffers are empty, and exit
history contains only expected user-requested force-stops/package replacement.

No physical system setting changed: font and all animation scales remained
`1.0`, while accessibility and touch exploration remained off. Cleanup restored
the accepted app/test APK hashes, the exact 26-file persistent archive, and all
32 paths, lengths, and SHA-256 values in the accepted 4,996,158-byte private
manifest; no app/test process survived. Automation covers the modal boundary,
48dp actions, touch-mode focus, synthetic 200% wrapping, and reduced motion;
the user explicitly deferred hands-on iQOO TalkBack/large-text review. No
provider, account, network permission, worker, clock, Google dependency, OAuth,
Drive API, cloud resource, signing key, or Play Console state was added or
mutated.

On 2026-08-09, the seventh appearance-only `O1PF`/`O1SS` slice passes its exact
dependency pins and dual-ABI debug/instrumentation build. On API 36
`emulator-5554`, the four new recovery cases pass 4/4 in 17.709 seconds, the
complete focused matrix passes 41/41 in 81.376 seconds, and the deterministic
loaded-pending pair passes 2/2 in an isolated 9.765-second run. The corrected
legacy appearance regression passes 36/36 in 126.493 seconds after its
corrected pair passed 2/2 in 9.457 seconds. The external seed passes 1/1 in
5.692 seconds, force-stop is confirmed with no surviving process, and the
fresh-process verification passes 1/1 in 3.744 seconds. The ordinary matrix,
excluding only the two external-process probe methods, passes 200/200 in
532.465 seconds, and the crash/fatal buffers are empty.

That final API 36 evidence accepts the local wire/store/presentation,
lifecycle, automated accessibility, and recovery boundary. Only
`emulator-5554` was targeted; the iQOO and every other physical device were not
targeted. The slice still uses deterministic simulated remote bytes and adds no
provider, account, network permission, worker, clock, Google dependency, OAuth
flow, or Drive connection.

On 2026-08-10, the eighth independent progress-display-choice `O1PC`/`O1PS`
slice passes the exact dependency guard and architecture audit and builds the
debug plus instrumentation APKs for `x86_64` and `arm64-v8a`. The existing
product-local `O8PG` record, provider-neutral `O1PC` bytes, and private atomic
`O1PS` identity/review/pending state remain separate. On API 36
`emulator-5554`:

- the isolated preserved-future `O8PG` Retry/Back regression passes 1/1 in
  6.330 seconds, and the complete focused matrix passes 37/37 in 78.279 seconds;
- the final rebuilt legacy `O8PG` regression passes 7/7 in 0.202 seconds, while
  structural navigation integration passes 6/6 in 21.030 seconds;
- the cross-family coexistence regression passes 49/49 in 143.490 seconds;
- the external seed passes 1/1 in 7.514 seconds, force-stop is confirmed with
  no surviving process, and fresh-process verification passes 1/1 in 5.126
  seconds;
- the ordinary matrix, excluding only the two external-process probe methods,
  passes 238/238 in 438.899 seconds; and
- crash and fatal buffers are empty.

Only `emulator-5554` was targeted; no physical device was targeted. The slice
uses deterministic simulated remote bytes and adds no provider, account,
network permission, worker, clock, Google dependency, OAuth flow, or Drive
connection. It does not authorize the still-deferred Drive security, threat-
model, account-binding, authorization, ownership, conditional-write, duplicate,
switching, deletion, privacy, first-sync, reinstall, or rollback decisions.

On 2026-08-10, the ninth independent catalog/managed-transfer slice passes its
Android compile and API 36 `emulator-5554` acceptance gate. Canonical `O1LC`,
private `O1LS`, per-book `O1BM`, and private `O1BQ` remain four distinct bounded
formats and do not reuse annotation, position, appearance, progress-choice, or
Port 6 records as a generic synchronization framework. The exact evidence is:

- focused catalog/managed-transfer coverage passes 80/80 in 1m53s;
- preserved appearance, reading-position, progress-choice, and structural-
  navigation coverage passes 47/47 in 2m36s;
- the ordinary matrix, excluding only the two externally driven restart-probe
  methods, passes 313/313 in 8m53s;
- the external seed passes 1/1 in 4.564 seconds, an actual force-stop leaves no
  process, and fresh-process verification passes 1/1 in 3.293 seconds; and
- the selected matrix at actual 130% system text with animations disabled
  passes 21/21 in 63.351 seconds, after which font plus window, transition, and
  animator scales are restored and read back at `1.0`.

The Library distinguishes durable offer dismissal from retained work: Back on
**Book available from another device** records exact **Not now** review-epoch
state, while Back on working/retry/cleanup UI only defers that modal, preserves
the exact durable attempt, stops live posted identity work, and exposes a
nonmodal action to reopen it. Late managed-publication recovery validates with
Reader0 first, then executes attempt-bound 4 MiB fused SHA-256 and Port 6 catalog
commit steps so the final identity proof and catalog association have no
callback gap.

The final crash and fatal buffers are empty. No physical device, provider, Drive
or account path, network permission, security/encryption decision, worker, cloud
resource, or Play Console state was used or changed. Those concrete-provider
and launch security gates remain explicitly deferred.

On 2026-08-08, hands-on review of the initial third-slice candidate found two
presentation omissions: the note field lacked internal padding and the reader
did not show the note's attachment point. The revised candidate uses UI0's
text-input inset metrics and a theme-safe, exact-anchor page glyph. The
unchanged dependency and architecture guards pass, and the dual-ABI debug/test
build is green. The user accepts the revised editor inset and exact-anchor
marker appearance. The marker-tap follow-up passes 13/13 focused API 36 tests
in 19.071 seconds, including discovery of the newly rendered accent pixels, a
real down/up tap opening the exact durable note, draft rollback, marker
add/remove evidence, restart durability, and forced frame-failure/reopen
recovery. Its clean ordinary run passes 119/119 in 317.113 seconds. The current
external seed/confirmed-force-stop/fresh-process driver passes in
6.409 and 3.811 seconds and verifies the note marker's exact spine/start/end
range. The 130% text/reduced-motion matrix passes 32/32 in 80.645 seconds, and
fixed pagination passes 5/5 in 26.582 seconds. The emulator crash buffer is
empty and font plus all three animation scales were restored/read back as
`1.0`.

The marker-tap candidate passes the API 34 iQOO focused gate 13/13 in 9.223
seconds with normal motion. One ordinary diagnostic missed top chrome in its
first window capture; that unchanged timing check passed 1/1 in isolation and
the authoritative clean rerun passed 119/119 in 235.657 seconds. The current
candidate also passes external restart in 1.948/1.115 seconds; the prior
candidate passed 32/32 at 130%
text in 75.601 seconds, and 5/5 fixed pagination in 17.714 seconds. The physical
crash buffer is empty;
TalkBack is off, touch exploration is `0`, rotation is `0`, and font plus every
animation scale are `1.0`. The user accepts the marker tap opening the exact
note in the editor. Cleanup restored the saved app APK
`A1EE566962B3DB5C8B4CDC4564D632FEFD9C058A173F5E644B2469CBA70C2EBD`,
test APK
`EBBDBE0CC8E6EC487A6556826DBF29476A6C6009280034C4BCC58174BC0E354B`,
and all 32 private files totaling 4,996,158 bytes byte-for-byte against manifest
`91B72D0059C80C6368D3C957BE2FC63660CC155FE3B72437DDFB7A0E5264E540`.
The protected archive remains available at SHA-256
`4C71715451BA6C0BA68DDC9FC2C1C308E3941899858F033195F68C80EE638D94`.

Earlier on 2026-08-08, the second local slice passed the unchanged exact dependency
guard and architecture audit and compiled the final debug/test candidate for
Android x86_64 and arm64-v8a. Its API 36 emulator gates are:

- 7/7 annotation-store tests, including atomic highlight round-trip, rollback,
  restart, and deterministic concurrent-color recovery;
- 3/3 highlight integration tests, including named TalkBack actions, real
  rendered Pink pixels, recolor/removal/recreation, store and frame failure
  recovery, and a forced queued-projection drain;
- 24/24 combined store/highlight/bookmark/selection/accessibility regression;
- 113/113 ordinary tests in 193.044 seconds, with only the externally driven
  process-restart probe excluded by its annotation;
- the separate seed, confirmed force-stop/no-surviving-process, and fresh-
  process verifier in 3.448 and 1.918 seconds, including exact Orange highlight
  spine, UTF-8 byte range, and semantic color recovery;
- 29/29 configuration-compatible tests in 44.325 seconds at actual 130% system
  text with window, transition, and animator scales set to `0.0`; and
- 5/5 fixed-pagination navigation tests in 14.909 seconds at 100% text with the
  same reduced-motion configuration.

An initial 113-test diagnostic on a degraded saved AVD runtime passed 112 tests
and missed only the existing 1500 ms first-frame debug bound at 1721 ms. The
unchanged accepted bookmark APK also missed that bound on the same runtime. The
guest showed load above 30, severe swap pressure, a spinning sensor HAL, and a
background Phone ANR. That run is rejected. A no-snapshot cold boot, ordinary
Android background-process cleanup, and an idle 400%-CPU sample restored the
environment; the unchanged final candidate then passed the complete 113-test
matrix above. No production threshold was relaxed.

The emulator crash buffer is empty, no 8vo or test process remains, and system
text plus all three animation scales were explicitly restored to `1.0` and read
back exact. No physical device was visible to `adb` or touched for this slice,
and no Google account, Drive API, OAuth configuration, cloud resource, signing
key, Play Console setting, commit, push, or merge was touched.

The coordinated API 34 iQOO second-slice gate then passed 10/10 focused store/
highlight tests in 7.088 seconds and a clean 113/113 ordinary matrix in 234.374
seconds. An earlier diagnostic completed 109/113: four existing keyboard-focus,
window-capture, clipboard, and Back timing assertions then passed 4/4 in 14.194
seconds in isolation and in the complete clean rerun; the diagnostic is not
acceptance evidence. The separate seed/confirmed-force-stop/fresh-process
driver passed in 2.092 and 1.172 seconds with exact Orange highlight spine,
UTF-8 byte range, and color recovery. At actual 130% system text, 29/29
configuration-compatible tests passed in 66.954 seconds with normal motion
retained. After explicit font restoration to `1.0`, 5/5 fixed-pagination tests
passed in 17.865 seconds, again with every animation scale at `1.0`.

The user accepted long-press selection, Yellow highlight creation and native
rendering, workspace excerpt/named color, Pink recolor, Go to, removal, and
restart behavior without a reported issue. With TalkBack 17.0.1 enabled, the
user also accepted Select text; Yellow, Pink, Blue, and Orange highlight
actions; the success announcement; Annotations heading/count/color/excerpt;
all four recolor controls; Go to; Remove; and Done without a focus, wording, or
navigation issue.

Cleanup restored TalkBack off, touch exploration off, no enabled accessibility
service, rotation off, audio baseline, font `1.0`, and all three animation
scales explicitly `1.0`. Live transitions rendered 123 Launcher and 107
SystemUI frames. The restored app APK SHA-256 is
`A1EE566962B3DB5C8B4CDC4564D632FEFD9C058A173F5E644B2469CBA70C2EBD`, and
the restored test APK SHA-256 is
`EBBDBE0CC8E6EC487A6556826DBF29476A6C6009280034C4BCC58174BC0E354B`;
both match pretest. All 32 captured private files totaling 4,996,158 bytes
match pretest path, length, and content; the 26 persistent files totaling
4,751,505 bytes reproduce archive SHA-256
`9118B960B212FC67EDC70152314341D8EDEDE0851045335D4B11CF23D79D3699`
and the existing canonical manifest SHA-256
`35368BF22698B1C9AA64FB940512C414AA83EFA50B6934B7248DABC71233719B`.
No app/test process remains. One later crash-buffer entry was attributed by UID
to `com.myairtelapp` invoking `dmesg`, not 8vo; after it was cleared the buffer
remained empty, and 8vo exit history contained only expected force stops. No
Google, Drive, OAuth, cloud, signing, Play Console, commit, push, or merge
operation was performed.

For the accepted first-slice baseline on 2026-08-07, the provider-neutral store
and bookmark slice passed the exact
dependency guard and architecture audit, compiled for Android x86_64 and
arm64-v8a, and passed these API 36 emulator gates:

- 5/5 annotation store tests and 3/3 bookmark integration tests;
- 108/108 ordinary tests with the external-restart verifier correctly excluded
  from the in-process matrix;
- the separate seed, confirmed force-stop, and fresh-process restart driver,
  including exact bookmark digest/spine/byte recovery;
- 26/26 configuration-compatible tests at actual 130% system text with window,
  transition, and animator scales set to `0.0`; and
- 5/5 fixed-pagination navigation tests at 100% text with the same reduced-
  motion configuration.

An unfiltered 110-test diagnostic passed 109 tests and failed only because it
invoked the external-restart verifier without its seed/force-stop driver; it is
not acceptance evidence. A combined 31-test configuration diagnostic passed 29
tests; its two failures were legacy tests deliberately asserting 100%-text
pagination bounds while the device was at 130%. The authoritative split above
keeps those bounds at 100% and exercises the other 26 tests at 130%.

The emulator crash buffer is empty. System text and all three animation scales
were explicitly restored to `1.0` and read back exact. No physical device,
Google account, Drive API, OAuth configuration, cloud resource, signing key, or
Play Console setting was touched during the emulator gate.

The coordinated API 34 iQOO gate then passed 8/8 focused tests in 5.429
seconds, 108/108 ordinary tests in 204.294 seconds, the separate seed/confirmed-
force-stop/fresh-process restart driver in 1.762 and 1.064 seconds, and 26/26
configuration-compatible tests in 57.182 seconds at actual 130% system text
with normal motion retained. The user accepted bookmark add/list/Go to/remove
touch behavior without a reported issue. With TalkBack 17.0.1 enabled, the user
also accepted the bookmark toggle, success/state change, workspace heading and
count, location/excerpt, Go to, Remove, and Done semantics without a focus,
wording, or navigation issue.

Cleanup restored TalkBack off, touch exploration off, no enabled accessibility
service, rotation off, font `1.0`, and every animation scale explicitly `1.0`.
Live vivo Launcher/Recents behavior rendered 75 frames, so motion restoration
was verified rather than inferred from stored values. The crash buffer is
empty, and no 8vo or test process remains. All 26 original app files totaling
4,751,505 bytes restored byte-exact with pre/post archive SHA-256
`9118B960B212FC67EDC70152314341D8EDEDE0851045335D4B11CF23D79D3699`
and canonical path/length/content manifest SHA-256
`35368BF22698B1C9AA64FB940512C414AA83EFA50B6934B7248DABC71233719B`.
No Google, Drive, OAuth, cloud, signing, Play Console, commit, push, or merge
operation was performed.
