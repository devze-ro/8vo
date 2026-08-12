# Android Port 11: synchronized Library membership v1

## Status and scope

This contract freezes and accepts a disconnected Port 11 backend plus
Library-only UI slice. It defines a provider-neutral synchronized-Library
membership overlay, Android-private review/recovery state, and explicit local
review actions. It does not connect a provider, request an account or
permission, transfer an EPUB, delete local or cloud bytes, or change the
accepted catalog and managed-transfer bytes.

The portable family is `O1LM`. Android's private atomic state is `O1MS` at
`<files>/port11/library-membership.v1`. Both are independent of canonical
catalog discovery `O1LC`, private catalog review `O1LS`, per-book manifest
`O1BM`, private transfer/cleanup `O1BQ`, Port 6's local catalog, and every
annotation, position, appearance, and progress family.

Version 1 deliberately makes the conservative product choices below:

- **Remove from this device** remains local-only and never writes `O1LM`;
- **Withdraw from synchronized Library** changes synchronized membership only;
- withdrawal never deletes a Port 6 row, managed EPUB, provider object,
  annotation, position, appearance, or progress state;
- **Restore to synchronized Library** is explicit and byte-identical;
- concurrent Withdraw and Restore remain a visible conflict until an explicit
  resolution observes every current head;
- causal record and actor history are retained for the lifetime of version 1;
  there is no compaction, eviction, capacity reclamation, or clock winner; and
- a reinstall creates a fresh actor. It does not reuse an old installation's
  counter or silently acknowledge old history.

Provider authorization, provider object ownership, remote EPUB deletion,
delete-all-cloud-data, encryption/authentication claims, account switching,
remote garbage collection, and history compaction remain later contracts.

## Accepted backend implementation and qualification

The accepted backend sub-slice added only `OctavoLibraryMembershipPortable`
(`O1LM`), `OctavoLibraryMembershipStore` (`O1MS`), their two focused Android
test classes, and this contract. At that backend gate there was no Activity/UI
integration and no
manifest, Gradle, CMake, dependency, asset, permission, provider, account,
network, worker, clock, or other portable-family change.

Qualification on the API 36 x86_64 emulator records:

- strict Java 17 `-Xlint:all -Werror` compilation for both production classes
  and both focused test classes;
- the exact dependency guard and architecture audit passing;
- the combined debug and instrumentation Java compile passing;
- focused membership coverage passing 21/21 in 1m48s: nine portable tests and
  twelve private-store tests;
- a clean ordinary connected rerun passing 334/334 with zero failures, errors,
  or skips in 10m35s (600.02 seconds of XML test time); and
- empty Android crash and fatal buffers after the clean ordinary rerun.

The first ordinary attempt passed 333/334 and timed out once in the unchanged
progress-sync strict-receipt test. The identical test then passed 1/1 in 29s
(5.579 seconds of XML test time), and the complete clean rerun passed 334/334.
Read-only diagnosis found no membership reference in that path and identified
the existing test helper's non-null-receipt check as weaker than production's
required `strictResumeSettled` proof. No production or test source changed
between those runs.

The resulting debug app is 4,128,302 bytes with SHA-256
`E33A92B8615180CDB1464045B4AC0B2BFC062EA36129F7E07F50234DD39FFD2F`;
it is aligned, v2-signed, targets API 36 with minimum API 26, and declares zero
permissions. The test APK is 1,387,094 bytes with SHA-256
`1D78AC158381A393EDFC8075BC6D1F3062ABD8FA78D37E1FAD3D49A6771AC1ED`;
only that test package has its generated `REORDER_TASKS` permission. Independent
portable and private-store adversarial audits found no remaining backend
blocker.

## Accepted Library UI integration and qualification

The accepted follow-on adds `OctavoLibraryMembershipPrompt`, loads `O1MS`
beside the existing Library stores in `OctavoActivity`, and adds focused prompt
and Activity integration tests. It does not change `O1LM` or `O1MS` bytes.

Existing import, managed cleanup, local reconciliation, transfer, and repair
surfaces retain priority. Membership blocked, staged, conflict, or cross-family
attention appears only afterward and before suppressed or foreign catalog
offers. Back from a confirmation closes it without mutation. Back from retained
attention preserves every durable byte, transiently returns to the usable
Library, and exposes a full-width reopen action.

An eligible local row has a separate full-width **Withdraw from synchronized
Library**, **Restore to synchronized Library**, or **Review synchronized
Library conflict** action below the existing Open and local Remove row. A
header/history action keeps a withdrawn or conflicted exact catalog identity
reachable when its local row is absent. Local Remove still changes only Port 6,
managed-cleanup, and existing local suppression state; it never changes O1LM.

Every confirmation retained through Activity recreation binds the exact EPUB
descriptor, record presence and projection, record fingerprint, complete
snapshot fingerprint, O1MS state generation, and pending action. Staged review
uses the exact O1MS receipt, including review epoch and staged/base hashes.
Every callback also rechecks that the exact O1LC digest and byte count remain
available before a membership mutation. Stale callbacks and orphan or unequal
cross-family identities are inert and visibly retained.

Known exact `WITHDRAWN` or `CONFLICT` history blocks the existing matching O1LC
offer; exact `MEMBER` history permits it. O1LM absence does not become
provider-object-presence evidence in this slice and does not alter the accepted
legacy O1LC offer path. A separate reviewed object-presence/binding coordinator
is still required before a fresh installation may use inherited membership as
remote authority.

Final API 36 x86_64 emulator qualification records:

- exact dependency and architecture guards passing;
- debug production and instrumentation Java compilation passing;
- focused membership coverage passing 31/31 in 42 seconds: nine portable,
  twelve private-store, four prompt, and six Activity integration tests;
- the complete ordinary connected matrix passing 344/344 with zero failures,
  errors, or skips in 9m12s (475.356 seconds of XML test time); and
- empty Android crash and fatal buffers after the complete run.

The resulting debug app is 4,645,120 bytes with SHA-256
`FA10CB862E2916C06BC5C4E27F4A12BA9300C779A415EA48F168451224338616`;
it is aligned, v2-signed, targets API 36 with minimum API 26, contains only
arm64-v8a and x86_64 native libraries, and declares zero permissions. The test
APK is 1,850,204 bytes with SHA-256
`F1D9199EDD826A81964D0A46EA9FC1AC1A6EA3BE6A8F54D59B3AE8DC6564889B`;
only that test package has its generated `REORDER_TASKS` permission.

## Membership versus discovery and availability

`O1LC` remains the add-only lifetime discovery set. `O1LM` is a causal overlay
for exact EPUB descriptors already known to that catalog contract. Each `O1LM`
record repeats the exact lowercase SHA-256 digest, byte count, and EPUB kind so
its portable state is self-contained and can survive out-of-order delivery.

For a separately reviewed exact `O1LC` descriptor, no `O1LM` record means
inherited `MEMBER`. That absence is not a portable mutation. The first `O1LM`
mutation is therefore `WITHDRAW`; a `RESTORE` is valid only when causal
membership history already exists.

Local import, `O1LC` publication, local Remove, byte-identical re-import, and
managed transfer never create, rewrite, or erase `O1LM`. In particular,
re-import cannot resurrect a withdrawn descriptor.

The inherited default is safe only after a later family-specific coordinator
has reviewed both catalog and membership object presence. A fresh or unbound
install without that evidence, a detected rollback or protocol violation, or
corrupt/future/over-bound `O1MS` makes the effective cross-family state
`UNKNOWN` and inert. A failed, cancelled, or unattempted remote read is not
evidence of absence and retains the last durable reviewed projection. A
confirmed missing object after a previously present object is separate review
attention, not an implicit inherited default.

A present orphan `O1LM` record still has its intrinsic membership projection;
only its effective cross-family state is `UNKNOWN`/`CATALOG_MISSING`. Equal
digests with unequal byte counts or kinds are cross-family `EQUIVOCATION` and
blocked, with both families retained exact. Neither state hides or disables an
existing local Port 6 book. A valid orphan or mismatch remains retained even if
`O1LC` is full; reconciliation failure is visible and never evicts either
family.

After reinstall, version 1 cannot detect a schema-valid provider rollback by
itself. First-sync review, source trust, and later provider binding remain
mandatory. `O1MS` review epochs and absence of a staged input are not provider,
account, or remote-object-presence authority.

## Canonical `O1LM` bytes

All integers are big-endian. Every string uses an `i32` byte length followed by
canonical lowercase ASCII. Records are ordered by digest; frontier and context
entries are ordered by actor ID; heads are ordered by mutation ID.

```text
u32 magic                 // 0x4f314c4d, "O1LM"
u32 version               // 1
u32 header_field_count    // 1
i32 record_count          // 0..63
record[record_count]
u32 crc32                 // IEEE CRC-32 over every preceding byte
```

Each record is:

```text
string[64] digest         // i32 length 64 + lowercase SHA-256 ASCII
i64 byte_count            // 1..536870912
i32 kind                  // 1 = EPUB
i32 frontier_count        // 1..16
frontier[frontier_count]  // string[32] actor + positive i64 counter
i32 head_count            // 1..8
head[head_count]
```

Each head is:

```text
string[32] mutation_id    // lowercase first-128-bit SHA-256
string[32] actor_id       // random 128-bit installation actor
i64 counter               // positive
u8 operation              // 1 = WITHDRAW, 2 = RESTORE
i32 context_count         // 0..16
context[context_count]    // string[32] actor + positive i64 counter
```

The exact empty length is 20 bytes. A maximally shaped record is 7,104 bytes:
68 descriptor-digest bytes, 8 byte-count bytes, 4 kind bytes, 4 frontier-count
bytes, 16 44-byte frontier entries, 4 head-count bytes, and eight 789-byte
heads. The exact structural maximum is therefore `20 + 63 * 7104 = 447,572`
bytes.

The portable reader cap is 524,244 bytes. A recognizable future object is an
eight-byte `O1LM` magic/version prefix with unsigned version greater than 1 and
total length at most that cap; its remaining bytes are opaque. A current object
above the structural maximum, a future object above the reader cap, or any
count that exceeds its structural bound is `LIMIT`. No count or length may
allocate, multiply, or loop before its remaining-byte and overflow checks.

Unknown field counts, invalid ASCII/UTF-8, non-lowercase or wrong-length IDs,
zero/negative counters, invalid operation values, noncanonical order,
duplicates, truncation, trailing bytes, checksum mismatch, and causal
inconsistency reject the complete input without mutation.

## Stable mutation identity

A mutation ID is the first 16 bytes of SHA-256 rendered as 32 lowercase hex
characters. The hash input is:

```text
ASCII("8vo.port11.library-membership.mutation.v1\n")
|| exact descriptor digest string encoding
|| byte_count:i64
|| kind:i32
|| actor_id string encoding
|| counter:i64
|| operation:u8
|| context_count:i32
|| canonical context entries
```

The namespace and exact length/count encodings are part of the hash. A claimed
ID whose recomputation differs is invalid. Equal mutation IDs, actor dots, or
descriptors with unequal bytes are equivocation and reject the complete input
or join.

## Causal validity

Every accepted record satisfies all of the following before merge pruning:

- each head dot `(actor_id, counter)` equals that actor's record-frontier
  component;
- each context component is positive, names a frontier actor, and is no greater
  than the frontier value;
- a head's own-actor context is absent or lower than its counter;
- each frontier maximum is justified by an equal live head dot or an exact
  component in at least one live head's context;
- one actor has at most one live head in a record;
- no live head causally observes another live head;
- every `RESTORE` has nonempty context; and
- every exact `(actor, counter)` dot appearing as a head or context component is
  owned by only one descriptor across the complete snapshot.

The global dot-ownership rule rejects reuse of any dot still represented in the
bounded current snapshot. It is not a lifetime ledger for older dominated dots
that causal projection no longer represents. `O1MS` prevents reuse by its own
installation by requiring the private counter to be at least every represented
occurrence of its current actor. If imported state is ahead of that private
counter, the store rotates to a fresh actor unused anywhere in the full
current-plus-imported merge candidate before publication.

## Projection and explicit operations

An absent record has no portable projection; the later reviewed cross-family
projection may call it inherited `MEMBER` only under the catalog/object-presence
rule above. A present record projects:

- `MEMBER` when every live head is `RESTORE`;
- `WITHDRAWN` when every live head is `WITHDRAW`; or
- `CONFLICT` when live heads contain both operations.

A root Withdraw creates a new record with empty context. An ordinary later
Withdraw is allowed only from `MEMBER`; an ordinary Restore is allowed only
from `WITHDRAWN`. Both ordinary operations reject `CONFLICT`. A distinct,
explicit conflict-resolution operation observes the complete current frontier,
advances the private actor counter, and emits `RESTORE` for member or `WITHDRAW`
for withdrawn, causally replacing all observed heads.

Withdrawing an already withdrawn record or restoring an already member record
is idempotent `UNCHANGED`. Restoring an absent record is `INVALID`; neither
`O1LC` presence nor re-import may bypass that rule.

Every root Withdraw, later Withdraw, Restore, and conflict-resolution API
requires an exact current receipt. The receipt binds the descriptor, intrinsic
projection or exact absence, canonical record-history fingerprint, complete
snapshot fingerprint, and persisted `O1MS` state generation. A changed receipt
is stale and causes no mutation. Conflict resolution uses a distinct explicit
capability and cannot be invoked through an ordinary stale Withdraw/Restore
callback; this prevents an action reviewed before a merge from silently
observing and resolving newly arrived heads.

The state generation advances exactly once with each successful changed local
mutation, approved changed join, or explicit reviewed recovery. An unchanged
operation, staging/review mutation, rejected result, and reload do not advance
it. At `Long.MAX_VALUE`, an operation that would change the current snapshot is
exact no-op `EXHAUSTED`.

## Join

Join validates both complete inputs, immutable descriptors, global dot
ownership, and equivocation before considering any capacity result. For a
record present on both sides it:

1. takes the component-wise maximum frontier;
2. retains an exact head shared by both sides;
3. retains a left-only head only if the right frontier has not incorporated its
   dot;
4. applies the symmetric rule to a right-only head; and
5. revalidates canonical order, mutation IDs, causality, global dot ownership,
   and every bound.

Non-overlapping records are retained unchanged. Join is commutative and
idempotent whenever its bounded result fits. Associativity holds when every
intermediate join required by the compared evaluation orders also fits the
version-1 bounds; a rejected intermediate limit is not a portable snapshot.
Stale replay cannot resurrect a causally resolved Withdraw or Restore. Pruning
dominated live heads is required causal join/resolution behavior; it is not
history compaction. Records and actor-frontier components are never removed.

## Capacity and typed limits

Version 1 uses explicit limit scope and reason:

```text
scope  = INPUT | JOIN | LOCAL
reason = RECORD_HISTORY | ACTOR_HISTORY | CONCURRENT_HEADS |
         ENCODED_BYTES | COUNTER_EXHAUSTED
```

`RECORD_HISTORY` beyond 63 and `ACTOR_HISTORY` beyond 16 are permanent for
version 1. Withdraw does not free an `O1LC` or `O1LM` record slot. A new actor
cannot mutate a record whose frontier is full. `CONCURRENT_HEADS` beyond eight
is never auto-resolved; a locally retained valid conflict can shrink only by an
explicit resolution that observes it. `ENCODED_BYTES` makes no retry promise.
At `Long.MAX_VALUE`, the private actor rotates atomically if a fresh actor can
enter the target frontier; otherwise the result is permanent history attention.

Every invalid, equivocal, or rejected limit outcome preserves portable/private
bytes, in-memory state, actor, counter, staged input, and review state exactly.
A successful `LIMIT_RETAINED` staging result may publish only the exact staged
input and typed attention while leaving the current snapshot/actor/counter
unchanged. Approval that still reaches the same limit preserves the already
staged input. A larger future protocol is required for compaction or capacity
reclamation.

## Private `O1MS` state

`O1MS` has magic `0x4f314d53` (`O1MS`), version 1, and an exact 1,048,576-byte
cap. It owns only:

- one random 32-hex installation actor and global nonnegative counter;
- one canonical current `O1LM` snapshot;
- one nonnegative current-state generation used only for stale-action binding;
- one nonnegative review epoch;
- at most one exact staged current or recognizable-future portable input, its
  SHA-256, and the SHA-256 of the current base snapshot at staging time;
- bounded typed attention/limit metadata.

Its version-1 physical order is fixed and big-endian:

```text
u32 magic                 // 0x4f314d53, "O1MS"
u32 version               // 1
u32 field_count           // 8
ascii[32] actor_id         // raw lowercase characters, no length prefix
i64 counter               // nonnegative
i64 state_generation      // nonnegative
i64 review_epoch          // nonnegative
i32 attention
i32 limit_scope           // 0 when no typed limit
i32 limit_reason          // 0 when no typed limit
i32 current_length
i32 staged_kind           // 0 none, 1 current, 2 recognizable future
i32 staged_length
raw[32] staged_sha256     // present only when staged_kind != 0
raw[32] base_sha256       // present only when staged_kind != 0
bytes[current_length] current_o1lm
bytes[staged_length] staged_o1lm
u32 crc32
```

Attention wire values are `NONE(0)`, `CURRENT_APPROVAL(1)`,
`JOIN_LIMIT_RETAINED(2)`, `FUTURE_RETAINED(3)`, `STAGED_CONFLICT(4)`, and
`STALE_BASE(5)`. Limit scope is `NONE(0)`, `INPUT(1)`, `JOIN(2)`, or `LOCAL(3)`;
limit reason is `NONE(0)`, `RECORD_HISTORY(1)`, `ACTOR_HISTORY(2)`,
`CONCURRENT_HEADS(3)`, `ENCODED_BYTES(4)`, or `COUNTER_EXHAUSTED(5)`. Unknown or
inconsistent enum combinations reject the complete private state.

The complete fixed metadata is 96 bytes with no staged input and 160 bytes with
one staged input, including the checksum. The exact maximum serialized state is
`447572 + 524244 + 160 = 971,976` bytes, leaving 76,600 bytes below the private
cap. This is also comfortably inside the 8 KiB metadata sub-bound.

It never stores a decoded second copy of staged bytes, title, path, URI, Port 6
row, provider/account/object/revision/session data, EPUB content, arbitrary
message, wall clock, or per-attempt log. The encoder must count while writing,
enforce the exact metadata and total bounds above, and assert the final length.

A missing store remains absent with an in-memory fresh identity until its first
successful mutation or staging publication. A recognizable future private
version and over-bound primary remain byte-exact in place and block mutation.
Malformed current state is moved byte-exactly into one of three fixed quarantine
slots; if no slot can preserve it, the primary remains in place and blocked.
A missing primary with any occupied quarantine path is blocked.

Corrupt/quarantined `O1MS` never auto-publishes empty state: doing so could turn
a withdrawn book into inherited membership. Recovery from exact portable bytes
requires an explicit reviewed API and a fresh actor.

Every publication uses a bounded same-directory lock file to serialize store
instances that share the one fixed temporary path. The lock carries no state.
Under that exclusion, publication creates/writes the temp, flushes and
file-descriptor-syncs it, rereads the exact candidate, then performs mandatory
atomic replace with no non-atomic fallback. The store tracks expected
destination presence and bytes; after the temp is durable and immediately
before replace it requires the destination to remain byte-exactly that expected
state. A missing or changed destination after load/publication is
`PUBLISH_UNCERTAIN_BLOCKED`, not a fresh missing store, so a stale store instance
cannot overwrite or implicitly restore history. Definite pre-replace failure
preserves the prior state. A post-replace uncertain result blocks until explicit
reload proves exact prior or candidate bytes; another result remains blocked.
No retry is implicit on startup or lifecycle events.

## Staging and review

Current-version portable bytes are fully validated before staging. A
recognizable future input is classified only by magic, unsigned version, and
length; its remaining bytes stay opaque. At most one exact staged object is
retained. Staging binds its SHA-256 and the exact base-snapshot SHA-256.

Approval and discard must echo those two hashes and the current nonzero review
epoch. Hashes without the epoch would permit an ABA callback after discard and
identical restaging on an unchanged base. Identical staging is `UNCHANGED` and
does not advance the epoch. Any distinct second current/future combination
returns `STAGED_CONFLICT`, retains the first bytes exact, and performs no
replacement. Advancing a `Long.MAX_VALUE` review epoch is exact no-op
`EXHAUSTED`.

Approval is rejected as stale if local state changed. A local mutation retains
the staged bytes and makes that stale base visible; it never clears or silently
rebases the review. Approval of a current object performs the exact join and
clears staging only in the same successful atomic candidate. A join/history
limit retains the input and typed attention. A recognizable future object is
retained byte-exactly and blocks approval on this app version.

## First backend-only vertical slice

The first implementation under this contract adds only:

- canonical `O1LM` encode, decode, inspect, local mutation, and join;
- atomic private `O1MS` load/export/stage/approve/discard/local-mutation state;
- deterministic package-private simulated replicas; and
- focused codec/store tests.

It makes no Activity, Port 6, `O1LC`, `O1LS`, `O1BM`, `O1BQ`, manifest,
dependency, permission, provider, or network change. No local or simulated
result is described as provider success.

An `O1LM` mutation never inserts into `O1LC`, clears an `O1LS` suppression,
queues or cancels an `O1BQ` transfer/cleanup, or changes Port 6 availability.

## Accepted Library UI seam and remaining coordinator boundary

The accepted Library-only vertical slice loads `O1MS` beside the existing
catalog/transfer stores. Current import, managed cleanup, local reconciliation,
transfer, and repair recovery retain priority. Membership
blocked/pending/conflict attention appears only afterward and before suppressed
or foreign `O1LC` offers.

The row uses a separate full-width action below Open/Remove:
**Withdraw from synchronized Library**, **Restore to synchronized Library**, or
**Review synchronized Library conflict**. It does not add a third horizontal
button. Local Remove remains unchanged. A header/history route keeps Restore
reachable when the local row is absent.

Every callback binds the descriptor, projection, canonical per-record history
fingerprint, complete snapshot fingerprint, store generation, review epoch,
staged/base hashes when relevant, and pending action identity. Activity
recreation fields are transient callback guards, not portable or provider
authority. Any serialized coordinator fields require a separately frozen
version or family. Back from confirmation closes transient UI without mutation.
Back from conflict or Retry defers only the UI, preserves exact durable
attention, and exposes a nonmodal reopen action. Focus restoration uses an
exact saved action discriminator rather than a boolean Remove flag.

## Qualification

The backend gate must cover:

- empty, single, maximum, and exact/+1 byte/count boundaries;
- golden bytes/hashes, canonical order, CRC, truncation, trailing data, future
  versions, and defensive copies;
- mutation-ID and global dot ownership, stale replay, same-dot/ID equivocation,
  and immutable descriptor mismatch;
- join commutativity, associativity, idempotence, concurrent opposite heads,
  and explicit resolution in both directions;
- every record/actor/head/byte/counter limit with exact rollback;
- actor rotation, self-ahead import, reinstall actor isolation, and permanent
  actor-history exhaustion;
- missing, future, over-bound, corrupt quarantine/block, occupied quarantine,
  definite publication failure, and prior/candidate/other uncertain reload;
- exact staged/base approval, stale approval, future conflict, and retained
  join-limit input;
- proof that local import/Remove/re-import and every other `O1` family remain
  byte-exact and unreferenced; and
- exact dependency/architecture/permission/security scans with no production
  fixture, network, provider, account, worker, clock winner, or secret.

Prompt accessibility, 200%-text layout, 48dp actions, Activity recreation,
Back/focus restoration, stale callbacks, and future-state preservation are
covered by the accepted emulator gate. Physical-device, external-process UI,
provider binding, and launch-security gates remain later work.

## Security boundary

CRC, SHA-256, canonical causality, and a nonempty Restore context provide
corruption detection, equality, and auditable structure. They do not
authenticate a user, device, Withdraw, or Restore. A schema-valid malicious
source can forge actors and actions, permanently consume record/actor capacity,
and disclose digest/size membership metadata. Source trust, encryption,
anti-rollback after reinstall, provider authorization, and production log
redaction remain explicit launch gates.
