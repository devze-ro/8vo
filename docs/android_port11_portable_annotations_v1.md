# Port 11 portable annotations v1

Status: byte contract offline-qualified on API 36 on 2026-08-08. This is
the provider-neutral annotation snapshot used by Android export/import tests
and intended for a later desktop peer and Google Drive adapter. It is not the
Android private `annotations.v1` file, a re10 SQLite dump, JSON, or permission
to connect a provider.

## Scope and identity

A snapshot is the complete canonical set of bookmark, highlight, and note
record envelopes known to one replica. It contains no installation actor
header, local next-counter value, wall clock, path, URI, filename, page number,
CFI, layout coordinate, SQLite ID, draft, reading position, preference, catalog
row, or provider metadata.

- `book_digest` is lowercase 64-hex SHA-256 of the exact EPUB bytes.
- Record, mutation, and actor IDs are lowercase 32-hex values. All 128-bit actor
  values, including all-zero, are legal on the wire.
- Anchors are Reader0 values: a nonnegative signed 64-bit spine index no larger
  than `0xffffffff`, plus nonnegative signed 64-bit normalized UTF-8 text-byte
  offsets. Ranges are `[start,end)` in one spine. Highlights are nonempty;
  bookmarks are points; notes may be points or nonempty ranges.
- An envelope's kind, book digest, and anchor are immutable. The optional
  attached-highlight ID is versioned head payload, not record identity; every
  nonempty value must resolve to a highlight envelope for the same digest in
  the complete snapshot, and that highlight may itself be tombstoned.
- Android keeps the attachment fixed during ordinary edits of one live version
  and uses the deterministically projected put when resolving concurrent
  versions. Version 1 tombstones deliberately erase payload, including the
  attachment; any concurrent put and its attachment remain retained until an
  explicit causal resolution.
- Wall time is never causal, canonical, or identity-bearing.

## Scalar encoding

All integers are big-endian. `u8` is one unsigned byte. `i32` and `i64` use the
Java signed two's-complement representation; fields whose prose says positive
or nonnegative must satisfy that constraint. A string is `byte_length:i32`
followed by exactly that many UTF-8 bytes. Text is preserved byte-exact after
strict UTF-8 validation; no normalization is performed. Fixed hex strings must
also have their exact ASCII byte lengths.

Maps and sets are arrays in canonical ascending byte order. Because every key
is fixed-length lowercase ASCII hex, this is also ordinary lexicographic order.
Duplicate or out-of-order keys, records, or heads reject the whole snapshot.

## Container

The portable container is:

| Field | Encoding | Value |
| --- | --- | --- |
| magic | `i32` | `0x4f314150`, ASCII `O1AP` |
| version | `i32` | `1` |
| header field count | `i32` | `1` |
| record count | `i32` | `0..2048` |
| records | repeated | canonical record envelopes below |
| checksum | `i32` | IEEE CRC-32/ISO-HDLC of every preceding byte |

The checksum is the algorithm exposed by `java.util.zip.CRC32`: reflected
polynomial `0xedb88320`, initial register `0xffffffff`, final XOR
`0xffffffff`; its unsigned low 32 bits are serialized big-endian. The complete
portable container, including that checksum, is at most 16 MiB minus 44 bytes
(`16,777,172` bytes). It detects
accidental corruption; it is not authentication, encryption, or an integrity
claim against an attacker. A recognizable higher version is reported as future
and is never merged. Any malformed, noncanonical, over-bound, or semantically
inconsistent candidate is an all-or-nothing rejection.

Android's private `O1AN` wrapper precedes the same record bytes with its stable
installation actor and local counter. That fixed private header is 44 bytes
larger than `O1AP`; keeping portable bytes at most `16,777,172` bytes therefore
keeps every publishable private file within the already-adopted 16 MiB `O1AN`
bound. Provider and desktop code must never upload, parse, or synthesize `O1AN`
as portable state.

## Record envelope

Each record is:

1. `record_id:string[32]`;
2. `kind:u8`: bookmark `1`, highlight `2`, note `3`;
3. `book_digest:string[64]`;
4. `frontier_count:i32`, `1..16`;
5. that many `(actor_id:string[32], counter:i64 positive)` entries;
6. `head_count:i32`, `1..8`;
7. that many mutation heads ordered by `mutation_id`.

A head is:

1. `mutation_id:string[32]`;
2. `actor_id:string[32]` and positive `counter:i64`;
3. operation `u8`: put `1`, delete `2`;
4. `context_count:i32`, `0..16`, then canonical
   `(actor_id:string[32], counter:i64 positive)` entries;
5. `spine_index:i64`, `byte_start:i64`, `byte_end:i64`;
6. `color:i32` and `flags:i32`;
7. `attached_id:string[0|32]`;
8. `label:string[0..256]`;
9. `excerpt:string[0..512]`;
10. `note:string[0..4096]`.

Color puts use yellow `0`, pink `1`, blue `2`, or orange `3`. Version 1 flags
reserve bit 0 for `starred`; every other bit is invalid. Current Android UI
writes flags `0`. Unused put fields are canonical zero/empty values:

- bookmark: point anchor, color `0`, empty attachment and note;
- highlight: nonempty range, empty attachment, label, and note;
- note: point/range, color `0`, empty label, and nonempty note text.

A delete retains the immutable anchor and has color `0`, flags `0`, and all four
payload strings (`attached_id`, label, excerpt, and note) empty. The delete
remains an envelope head/tombstone; records are not physically deleted in v1.

## Stable hashes

The bookmark record ID is the first 16 SHA-256 bytes, rendered lowercase hex,
of:

```text
ASCII("8vo.port11.bookmark.v1\n")
|| ASCII(book_digest)
|| spine_index:i64
|| byte_offset:i64
```

The namespace makes bookmark kind implicit. A mutation ID is the first 16
SHA-256 bytes, rendered lowercase hex, of the mutation below without its ID:

```text
record_id:string || kind:u8 || book_digest:string
|| actor_id:string || counter:i64 || operation:u8
|| context_count:i32 || canonical context entries
|| spine_index:i64 || byte_start:i64 || byte_end:i64
|| color:i32 || flags:i32
|| attached_id:string || label:string || excerpt:string || note:string
```

An ID whose recomputed hash differs, or one ID associated with unequal bytes,
rejects the complete input.

## Causal validity and join

For every accepted envelope:

- each head dot `(actor_id,counter)` equals that actor's frontier component;
- every context component is no greater than the envelope frontier;
- a head's own-actor context is lower than its counter;
- every frontier component is justified either by an equal live head dot or by
  an exact component in at least one live head's context;
- one actor has at most one live head in a record;
- no live head causally observes another live head; and
- one actor/counter head dot is not reused by another record in the snapshot.

Join first validates both complete snapshots and compares kind, digest, and
anchor before pruning either side. For a record present on both sides it takes
the component-wise maximum frontier, retains an exact head shared by both
sides, retains a left-only head only when the right frontier has not
incorporated its dot, and applies the symmetric rule to a right-only head. It
then revalidates canonical order, attachment references, causality, and every
bound. For non-equivocating causal histories whose required intermediate and
result fit the declared bounds, this state-based opposite-frontier join is
commutative, associative, and idempotent; replaying a stale put cannot resurrect
a causally resolved tombstone or note body. A capacity rejection makes the
bounded binary operator deliberately partial: it leaves the prior state exact,
and a coordinator must retain and retry that input after any successful merge
or causal resolution that may reduce live state.

The Android import result distinguishes merged, unchanged, blocked local state,
invalid input, future version, capacity limit, and atomic-publication failure.
Only merged/unchanged are success. An unchanged join performs no disk write.
A changed join normally keeps the importing installation's private actor and
counter. If imported state contains a dot for that actor beyond its locally
published counter, Android rotates to a fresh actor before publication instead
of reusing or exhausting the old actor's dot space; the old causal history stays
in the records. It then encodes the full private candidate and publishes it by
the existing synchronized same-directory atomic replace. Serialization itself
is bounded while writing, not only after allocation. Any failure leaves bytes,
counter, projections, and retry input exact.

Portable v1 validates untrusted bytes for structure, bounds, references, and
causal consistency, but it is not a Byzantine or authenticated CRDT: a party
that can replace the file can still fabricate schema-valid actor histories and
records. Provider import remains disconnected until the product defines source
trust, review/recovery UX, and its encryption/authentication claim.

## Golden and consumer boundary

`testdata/annotations/v1/portable-full.base64` decodes to the 2,251-byte golden
whose SHA-256 is
`e2dba96f2029e9f0d4a989ff12c3ae9b69c6206a9ec26baa9f71acde2545641e`.
The adjacent JSON is a human semantic oracle only. The fixture uses the
242,131-byte deterministic Port 6 EPUB, all four colors, an all-zero actor,
multibyte UTF-8, a standalone note, and a stable note attachment.

`portable-causal.base64` is an independently generated 1,792-byte companion
whose SHA-256 is
`470f3fa199e00721d04d697a7b60e625473757fa9d9598ddafe322a1bc4c4a49`.
It freezes multi-actor frontiers and contexts, canonical DELETE payloads, two
concurrent note heads, the starred flag, a counter above `2^53`, decomposed
Unicode, CRLF, and a high-bit CRC. Its JSON oracle mirrors every decoded wire
field so a future desktop consumer does not need to infer omitted values.

Desktop support requires re10 to add the same EPUB SHA-256 and stable portable
record/mutation/tombstone identities. Current path/local-ID rows and export are
a presentation projection and cannot round-trip this state. A later Drive
implementation may place these bytes behind a small Drive adapter; another
provider may implement the same bounded get/put/list/delete transport after
Drive is proven. Neither possibility justifies a generic framework now.

Reading positions are deliberately a separate portable record family. Their
later UI may offer the Kindle-like choice to go to another device's last
successfully presented Reader0 anchor or keep the local location. That prompt,
position causality, Drive authorization, encryption claim, and threat model are
outside annotation portable v1.
