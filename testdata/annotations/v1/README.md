# Portable annotation v1 goldens

The Base64 assets decode to canonical `O1AP` portable-v1 byte containers
specified in `docs/android_port11_portable_annotations_v1.md`:

| Fixture | Decoded bytes | SHA-256 | Coverage |
| --- | ---: | --- | --- |
| `portable-full.base64` | 2,251 | `e2dba96f2029e9f0d4a989ff12c3ae9b69c6206a9ec26baa9f71acde2545641e` | bookmark, all four colors, all-zero actor, multibyte UTF-8, standalone note, attached note |
| `portable-causal.base64` | 1,792 | `470f3fa199e00721d04d697a7b60e625473757fa9d9598ddafe322a1bc4c4a49` | three actors, canonical multi-entry frontiers and contexts, concurrent two-head attached-note conflict, starred put, highlight and note tombstones, counter above `2^53`, decomposed Unicode, CRLF preservation |

The source book for both fixtures is the deterministic 242,131-byte Port 6
EPUB at
`../../../android/app/src/main/assets/port6/octavo_port6.epub`. Its exact-file
SHA-256 is
`5d81c6ba136774cb4addc01dfc88bec355d637456ee6aacb3004983a6f055ed3`.

`portable-full.json` is a compact human semantic oracle. The independently
generated `portable-causal.json` is a structurally isomorphic oracle: records,
frontiers, heads, contexts, and fields appear in wire order; counts are
explicit; every record repeats `book_digest`; and all canonical payload fields,
including empty ones, are present. Kind and operation values use their numeric
wire IDs. Every `i64` is a decimal string so JSON tooling cannot round a value
above JavaScript's `2^53` exact-integer limit. Neither JSON file is a transport
or a canonical JSON format; consumers must validate the decoded binary.

The checksum is CRC-32/ISO-HDLC, the algorithm implemented by Java
`java.util.zip.CRC32`: polynomial `0x04c11db7` (reflected
`0xedb88320`), initial value `0xffffffff`, reflected input/output, and final XOR
`0xffffffff`. Serialize the low 32 bits as four big-endian bytes. The causal
fixture deliberately has checksum `0x8f2f9462`, with its high bit set, to catch
signed/unsigned serialization mistakes.

Under portable v1's canonical delete rule, every delete uses color `0`, flags
`0`, and empty `attached_id`, `label`, `excerpt`, and `note`. The causal
fixture's two concurrent live note puts remain attached to the tombstoned
highlight.

The adopted size compatibility rule keeps Android's complete private `O1AN`
file at or below 16 MiB. Because `O1AP` is exactly 44 bytes smaller for the same
records, a complete portable container is at most 16 MiB minus 44 bytes
(16,777,172 bytes).

Android packages this directory only into the instrumentation-test APK. It is
not included in the production APK. A future desktop consumer must preserve the
portable record/mutation identities and causal state independently of re10's
lossy path/local-ID presentation rows and current JSON user export.
