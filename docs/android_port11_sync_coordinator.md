# Android Port 11: provider-neutral annotation sync coordinator

Status: implementation contract adopted 2026-08-08 for a disconnected,
annotation-only local qualification slice. This contract does not connect
Google Drive, request network or account access, add a background worker, or
authorize cloud, OAuth, signing, Play Console, push, or merge operations.

## Boundary and non-goals

8vo owns synchronization policy, durable retry state, first-merge review,
portable-family selection, conflict presentation, and every future provider
adapter. The coordinator is a caller-driven value/state machine. It yields one
bounded read or conditional-write command and accepts one matching result; it
does not own a transport interface, callback table, thread, scheduler, service,
or dependency-injection framework.

The product caller owns exactly one serialized coordinator and sync-store
instance for this private file. The in-memory lease rejects another coordinator
that shares that store instance; it is not a file lock or cross-process lock.
No second app process, background worker, or independently constructed store may
operate on the file. A future Activity/service handoff must preserve this single
owner instead of treating the coordinator as a process-wide provider framework.

This slice synchronizes exactly one logical object containing the complete
canonical annotation `O1AP` snapshot. It does not synchronize Android's private
`O1AN` wrapper, note drafts, reading positions, preferences, Library metadata,
managed EPUBs, provider manifests, or deletion requests. Those are independent
record families with separate identity, bounds, merge, review, and recovery
contracts. In particular, the later reading-position family must preserve the
Kindle-like choice to keep the local presented position or move to a remote
Reader0 anchor.

No production Activity or provider calls this coordinator in this slice. A
deterministic fake executes its commands only in instrumentation tests.

## Terms and ownership

| Value | Contract |
| --- | --- |
| Logical object name | The product value `state/annotations/o1ap-v1`. It is bounded lowercase ASCII and identifies semantics, not a provider filename, path, folder, or unique key. No unimplemented family names are frozen. |
| Binding fingerprint | A private lowercase 64-hex SHA-256 computed by the product host for one provider, account, application namespace, and logical object. A future adapter supplies stable opaque provider/account namespace bytes; it does not own the composition or hashing policy. The value is compared only for exact equality and is never portable state. |
| Provider object handle | Opaque `1..512` bytes returned by a resolved read and echoed only to a conditional replacement in the same live operation. It is never parsed, sorted, logged, or persisted. |
| Provider revision | Opaque `1..512` bytes returned by a resolved read and echoed only as that operation's replace precondition. It has no ordering, clock, causal, or cross-account meaning and is not persisted. |
| Content digest | Lowercase SHA-256 of the exact complete `O1AP` bytes. It proves equality and pending state, not authorship. |
| Operation token | A fresh in-memory 32-hex token plus the binding fingerprint on every yielded command. It rejects stale, duplicated, late, cancelled, or cross-binding results and is never provider or portable metadata. |

Binding derivation is product-owned and domain-separated. The host hashes a
fixed versioned `8vo` binding domain followed by an unambiguous length-prefixed
tuple of the opaque provider namespace, stable account subject, application
namespace, and exact logical object name. Display names, email addresses,
paths, filenames, and provider revisions are not stable account subjects. The
fingerprint is correlation-sensitive private metadata: it must not be logged,
sent as analytics, published remotely, or reused as a provider object key.

A future adapter must resolve the logical object through its concrete API. A
name is not assumed unique. This first coordinator accepts only `missing`, one
resolved object, or an explicit duplicate-object result. It never chooses among
duplicates or deletes them. Drive-specific listing, duplicate reconciliation,
file IDs, folders, change tokens, resumable sessions, and quota policy remain a
later concrete adapter slice.

## Required remote primitive

Plain get/put is insufficient for complete multi-writer snapshots. A usable
adapter must provide semantically equivalent bounded values for:

- resolved read: `missing`, exactly one `(handle, revision, bytes)` candidate,
  duplicate objects, or a classified provider failure;
- conditional create: publish only while the logical object is still missing;
- conditional replace: publish only while the resolved handle still has the
  exact opaque revision returned by the read; and
- committed response: return a bounded new handle/revision, or report a
  precondition conflict, definite failure, or outcome unknown.

The coordinator never performs an unconditional overwrite. A precondition
conflict returns to read, validates and stages the new bytes, merges them into
local state, re-exports, and issues a new conditional command. One invocation
permits at most three conditional writes that each lose their precondition;
the third conflict terminates visibly as `revision retry limit`. There is no
sleep, clock, spin loop, or hidden retry. The caller may start a later manual
retry.

Provider failures remain distinct: unauthorized/revoked, quota, transient,
permanent, cancelled, duplicate objects, precondition conflict, outcome
unknown, and response/size violation. Only precondition conflict is retried in
the same invocation. Definite and unknown write failures never roll back an
already durable local annotation merge.

## Checked local boundary

The annotation store must have completed `load()` before synchronization. A
never-loaded, corrupt-blocked, or future-version-blocked `O1AN` store cannot be
exported or merged and causes zero remote writes. Export is one synchronized
checked operation returning exact bytes, blocked/not-loaded, limit, or local
failure; the coordinator must not infer safety from a separate status check.

Fetched bytes are first inspected without mutation. Inspection distinguishes:

- valid and locally unchanged;
- valid and locally mergeable;
- valid but locally join-limited;
- malformed or semantically invalid input;
- recognizable future `O1AP`; and
- input that exceeds an `O1AP` structural or byte bound.

Only a valid candidate may enter the durable inbox. `invalid`, `future`, and
input-limit candidates leave both local and remote bytes exact and cannot be
mistaken for the retryable join-limit case. Inspection supplies only a bounded,
no-content review summary: annotation kind counts, visible/tombstoned count,
conflict count, exact byte length, and content digest. It exposes no note text,
excerpt, book digest, anchor, title, path, provider token, or user identifier,
but its counts, length, and stable digest remain correlatable private metadata.
They must not enter provider metadata, production logs, or analytics.

## Private coordinator state

Android stores one independently atomic private file at
`<files>/port11/annotation-sync.v1`, magic `O1AS`, version 1. It contains only:

- the binding fingerprint;
- the optional content digest of the last exact snapshot acknowledged as
  converged remotely;
- whether a remote object may previously have existed, so disappearance is
  never mistaken for an initial empty provider;
- while a write is in flight, the content digest of the exact submitted
  `O1AP`, so a lost success response can recognize 8vo's own bytes without
  bypassing review for different remote content;
- one phase: `idle`, `remote snapshot`, `write in flight`, or durable
  `remote missing review` (`REMOTE_MISSING_REVIEW`); and
- one bounded attention code for the last accepted remote/provider/protocol or
  local join/publication/export failure that is not otherwise derivable from
  the phase; and
- in `remote snapshot`, the exact validated `O1AP` candidate.

The remote-may-have-existed bit is deliberately conservative. One resolved
object, an explicit duplicate-object result, or a conditional write that has
been made eligible to leave the process sets it before later validation or
transport outcome can erase uncertainty. Ordinary retry, cancellation,
definite failure, outcome unknown, and pending-state discard do not clear it.
For healthy decoded state, only an explicit reviewed binding reset clears that
history. Explicit acknowledgement of quarantined, unreadable state is the
separate recovery path: it publishes a fresh unbound state because the prior
history cannot be trusted.

Provider handles and revisions are deliberately live-operation values and do
not survive process death. A restarted operation always reads again before a
conditional write. `write in flight` records that a remote write may be
pending or may already have committed; it never authorizes a blind replay.

The file is checksummed, strictly encoded, bounded to the portable maximum plus
at most 236 bytes of encoding overhead, descriptor-synchronized, and replaced
with one same-directory `ATOMIC_MOVE`. Its unbound empty form is exactly 44
bytes. There is no non-atomic fallback. Publication is all-or-nothing. A
recognizable future version remains byte-exact and blocks sync-state mutation.
Malformed current bytes move atomically to one of three
bounded quarantine names and synchronization remains blocked. A missing live
state file with an existing quarantine also remains blocked. Only an explicit
product acknowledgement through `acknowledgeQuarantinedReset()` may publish a
fresh empty `O1AS` and unblock future binding; it neither deletes the
quarantined evidence nor changes local annotations or remote data. Quarantine
failure remains blocked. A genuinely missing state with no quarantine remains
unpublished until an explicit binding begins.

The embedded snapshot makes one file the atomic retry unit. Publication streams
the bytes and checksum so the coordinator does not construct a second wrapper-
sized byte array. This private file is not a provider payload and has no desktop
or cross-provider compatibility promise.

An exact remote snapshot is retained while local merge returns join-limit,
local annotation publication fails, local annotation state is blocked, or the
process dies before incorporation. After incorporation, local `O1AN` contains
the causal state, so the sync file advances to `write in flight` before a write
command is yielded. Restart then reads the current remote state rather than
reissuing a stale condition.

Pending local work is derived without wall time. It requires attention when a
durable attention code is nonempty; otherwise it is pending when the sync phase
is not idle, no converged digest exists, or the exact current checked `O1AP`
hash differs from the last acknowledged hash. A mutation made after an outgoing
snapshot was captured therefore remains pending even if that older write
succeeds. A successfully published validated stage, missing-review state, write
intent, or convergence clears stale attention; cancellation and stale callbacks
do not.

## First-merge and binding safety

A missing binding may be established only by an explicit coordinator start.
An unequal existing binding returns `binding mismatch`; it never imports,
exports, clears, or reuses state across accounts/providers. An explicit local
reset may discard only this private coordinator file after product review. It
does not delete annotations, revoke authorization, disconnect an account, or
delete remote data.

For a binding with no converged digest, the first resolved remote object is
validated, previewed, and durably staged, then the operation stops with
`review required` before mutating `O1AN`. Approval must echo the exact staged
content digest. A changed or missing digest asks for review again. The sole
exception is recovery of a durable `write in flight`: a resolved object whose
digest exactly matches 8vo's persisted outgoing digest is its own possibly
committed write, not newly discovered remote content. Remote `missing` may
proceed to conditional create only when no remote object may have existed.
Otherwise the coordinator first publishes durable `remote missing review` and
stops. Recreation approval is eligible only when that phase already existed at
`begin`; the coordinator then reads again and creates only if the object is
still missing. A found object follows the ordinary validation/review path
instead. This backend slice proves both gates but supplies no production review
UI or authorization flow, so it remains disconnected.

## State machine

Only one command is outstanding for the caller-owned serialized coordinator
instance. The in-memory lease is defense in depth within the shared store
instance, not authorization for multiple instances or processes.

1. `begin(binding, approved_digest?, approve_recreation?)` verifies both stores
   are loaded and writable, acquires the in-memory lease, binds or checks the
   fingerprint, and rejects a concurrent session. Snapshot or recreation
   approval is eligible only if its corresponding durable review phase existed
   at `begin`.
2. If an exact staged snapshot exists, inspect it again. On first merge, require
   the matching approval digest. Merge it atomically before requesting current
   remote state. Join-limit or annotation-publication failure retains it exact.
3. Yield `read(logical_name)` with a fresh operation token and binding.
4. On `missing` with no remote-may-have-existed history, checked-export local
   bytes, publish `write in flight` with that history set conservatively, and
   yield conditional create. Otherwise publish `remote missing review` and stop.
   After a later approved begin, read again before conditionally recreating.
5. On one resolved object, validate all bounds before durable staging. A first
   remote stops for review, except for exact recovery of 8vo's persisted
   in-flight digest. Otherwise atomically merge the staged bytes.
6. Checked-export the full local union. If it is byte-equal to the read object,
   publish its digest as converged, clear the inbox, and finish without a write.
7. If unequal, publish `write in flight`, then yield conditional replace using
   the exact live handle/revision and exported bytes.
8. On committed create/replace, publish the SHA-256 of the exact written bytes
   as converged and clear the phase. If that local publication fails, report it;
   the remote may already be correct and the next invocation reconciles by read.
9. On precondition conflict, read again unless the exact three-conflict bound
   is exhausted. On definite failure, cancellation, or outcome unknown, finish
   visibly with `write in flight` retained.

Every accepted response must echo the outstanding token and binding. Any wrong,
stale, duplicate, or out-of-order response is rejected without local or remote
mutation. The standalone `invalid response` completion describes only that
rejected callback: the original outstanding command remains live and must still
be completed or cancelled. Cancellation invalidates the live token and retains
whatever durable phase and attention already exist.

## Crash and interruption recovery

| Last durable boundary | Restart behavior |
| --- | --- |
| Before a resolved candidate is staged | Read again; local and remote are unchanged. |
| Candidate staged, before/during local merge | Retry that exact candidate before any new read. |
| Join-limit or local annotation publish failure | Keep the exact candidate; after local resolution/recovery, retry it before reading. |
| Local merge committed, before write intent | Staged candidate remerges idempotently, then read again. |
| Write intent committed, before/after transport call | Read current remote state; never blindly reissue the old condition. |
| Provider committed but response was lost | Read sees the committed bytes and merges them unchanged. If current checked export still equals those bytes, acknowledge convergence without another overwrite; otherwise conditionally publish the newer local union. |
| Provider committed but converged-manifest publication failed | The retained write intent forces read/reconciliation; local annotations remain durable. |
| Prior presence or write possibility followed by `missing` | Retain durable `remote missing review`; require explicit recreation approval on a later begin, then read again before any conditional create. |
| Corrupt O1AS moved to quarantine | Keep sync blocked across restart until explicit product acknowledgement publishes empty coordinator state; preserve the quarantine and local annotations. |

The annotation store and coordinator state remain independent atomic files; no
cross-file transaction is claimed. The ordering above makes every observable
intermediate recoverable through idempotent merge and a fresh conditional read.

## Result taxonomy

Successful completion reports whether the operation pulled a local change,
performed a remote write, and encountered a precondition retry. Non-success
results remain distinct: not loaded, local annotation blocked, sync state
blocked, binding mismatch, first-remote review required, remote-deletion/
recreation review required, remote invalid, remote future
version, remote input limit, local join limit with retained candidate, local
annotation publication failure, sync-state publication failure, duplicate
remote objects, unauthorized/revoked, quota, transient, permanent, cancelled,
outcome unknown, invalid/stale response, and revision retry limit.

When `O1AS` publication succeeds, no transient message is the sole owner of an
accepted error. The private manifest retains bounded attention for invalid/
future/overbound/duplicate remote state, accepted protocol violations,
provider failures other than user cancellation, write uncertainty/retry
exhaustion, and non-derivable local join/publication/export failures. A sync-state
publication failure is necessarily visible in its completion because the
failed file cannot durably record itself. A future UI derives pending/attention
state from the manifest and provides explicit Sync now, retry, review,
disconnect, revocation, and separate local/cloud deletion actions.

## Security and privacy boundary

`O1AP` validation, CRC, and SHA-256 detect malformed bytes and equality; they do
not authenticate an author. A schema-valid malicious snapshot can still forge
actors, delete records, or consume bounds. The fake-provider qualification
therefore proves transport ordering and recovery only. It is not authorization,
TLS, provider trust, anti-rollback after reinstall, or end-to-end encryption.

Before Drive is connected, the product must freeze its source-trust and initial-
review UX, account-switch behavior, reinstall/rollback policy, privacy flow,
logging restrictions, and accurate encryption claim. If client-side encryption
is chosen, it wraps exact portable bytes in a separate versioned authenticated-
encryption envelope; it does not change `O1AP`. Provider/account identifiers,
tokens, note text, excerpts, anchors, book digests, and remote payloads must not
enter production logs.

Disconnect, authorization revocation, local data deletion, and deletion of
8vo-created cloud data remain four separate user actions. No synchronization
result implicitly deletes a managed EPUB or local annotation.

## Deterministic offline gates

Before any concrete provider work, API 36 instrumentation must prove:

- two fixed-actor devices converge after an offline create race without a
  clock, including conditional-create failure and read/merge/replace retry;
- first-remote review stages exact bytes and requires the matching digest;
- prior presence, duplicates, and uncertain/failed conditional writes make a
  later missing result durably review-gated; only an approval presented against
  that pre-existing phase permits a fresh read and conditional recreation;
- read, local merge, manifest, definite write, and outcome-unknown failures are
  exact and restartable at every durable boundary;
- the third consecutive precondition conflict stops, and a later manual retry
  can converge;
- invalid, future, input-overbound, duplicate, and blocked-local cases perform
  zero remote writes and preserve both sides;
- a valid 5-head plus 4-head join retains the exact remote snapshot across
  restart, then succeeds after causal resolution reduces the live heads;
- sync-state corruption quarantine, explicit quarantined-state acknowledgement,
  future preservation, binding mismatch, stale operation tokens, cancellation,
  defensive byte copying, and checked never-loaded export all fail safely;
- exact pending/converged hashes survive process recreation and local mutations
  made after an outgoing snapshot; and
- the exact maximum portable snapshot is staged, merged, and recovered within
  the known 192 MiB API 36 heap in an isolated test.

The existing portable-state, annotation-store, note-integration, ordinary, and
external-process gates remain regressions. Only the explicit API 36 emulator
serial may be used while the iQOO is connected. This backend-only slice does not
request physical validation.

## Later provider and desktop work

The first Drive adapter must separately prove least-privilege authorization,
`appDataFolder` identity, duplicate listing/reconciliation, quota/revocation/
account switching, metered policy, and visible sync/recovery UI. In particular,
it must prove a true atomic create-if-missing operation and a true conditional
replacement against the exact revision returned by the resolved read. A name
lookup followed by an ordinary upload, a last-writer-wins overwrite, or merely
detecting duplicate creates afterward is not equivalent. If Drive cannot supply
those primitives directly, it does not qualify for this coordinator unless a
separately reviewed product-owned protocol first proves equivalent atomicity;
the coordinator contract must not be weakened to accommodate it. `drive.file`
EPUB transfer remains a separate manifest and resumable-transfer slice.

Another provider may reuse the product values and state-machine semantics only
if its concrete adapter proves equivalent conditional behavior. This contract
does not promise identical Drive, WebDAV, or S3 APIs and does not justify a
shared provider framework.

Desktop interoperability is the exact `O1AP` bytes and golden fixtures, not
Android Java or a database copy. re10 first needs authoritative portable state
keyed by exact EPUB SHA-256; its current URI hash, numeric rows, physical
deletes, and folded highlight/comment projection cannot round-trip portable
identity, tombstones, independent notes, or concurrent heads.
