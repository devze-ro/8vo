# Android feature-parity contract

Last reviewed: 2026-08-06. Baseline implementation: accepted Android Ports 0-7
plus the corrected Android Port 8 structural-navigation candidate against
Reader0 `0.7.0-dev` / API 7 at
`5fe949d88258cd96884c44b69e4f4ab6f27dc394`. Earlier Port 8 API 36/iQOO
automation, exact data restore, and controlled real-book Contents jump/Return
checks are predecessor evidence. Corrected-source API 36 and API 34 iQOO
validation passes 67/67 plus external restart and 15/15 large-text/reduced-
animation gates. Controlled real-book navigation closes the six reported
defects. The first bounded Navigation-polish slice passes its API 26 and API 36
emulator guards/build, 26/26 focused, 85/85 ordinary, restart, and 22/22 large-
text/reduced-animation gates. The API 26 framework cursor/handle compatibility
gate also passes. Desktop light/dark versus Android light/dark/High Contrast
portrait Navigation visual review passes. Audible TalkBack, full-app visual
parity, remaining bounded polish, and user subjective/manual acceptance remain

This is the living capability contract for the premium 8vo Android product
described in `android_product_vision.md`. Kindle for Android is the principal
quality benchmark, but the target is capability and interaction parity with a
distinct 8vo design—not a screen clone.

The inventory is intentionally explicit about uncertainty. Kindle behavior
can vary with application version, region, account, content format, publisher
flags, and licensing. A row is not considered fully inventoried until those
conditions and an observed app version are recorded.

## Target labels

| Label | Meaning |
| --- | --- |
| **Match** | Deliver the useful local capability and comparable interaction quality. |
| **Improve** | Match the capability and deliberately exceed the benchmark in the stated way. |
| **Local alternative** | Deliver the user benefit without depending on Amazon or copying a proprietary dataset/service. |
| **Content-dependent** | Support it when the document carries the required structure or media. |
| **Excluded service** | Outside the user-owned, bring-your-own-storage product boundary. |
| **Audit** | Benchmark behavior or availability still requires versioned hands-on verification. |

"Current Android baseline" distinguishes accepted Port 7 behavior from Port 8
behavior implemented locally but not yet accepted. Port 7's Reader0, companion
re10, exact 8vo guard/build, final emulator and iQOO 36/36 matrices,
ProcessRestart, 130% accessibility, crash, and byte-exact backup/restore gates
passed. Emulator instrumentation took 510.019 seconds and iQOO instrumentation
took 108.467 seconds. The matrix contains appearance store 9, appearance 15,
navigation 5, library 5, accessibility 1, and bootstrap 1. Every 33-test API 6
and earlier record is historical. Audible TalkBack, broader alternate-input and
reduced-motion, extended physical theme reading, and subjective dark-room
comfort remain product follow-up; they are not Port 8 acceptance evidence.

## Library and ownership

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| Local EPUB import | Improve | Android document picker, bounded managed copy, Reader0 validation | Multi-select and visible import progress; clear duplicate, invalid, full-storage, and cancellation outcomes. |
| Multiple-book catalog | Match | Built-in sample plus 63 imported books | Scale without losing bounded/recoverable storage guarantees; migrate catalog versions safely. |
| Exact per-book resume | Improve | Port 6 accepts exact same-layout resume. The accepted Port 7 emulator and iQOO 36/36 matrices plus separate ProcessRestart driver passed, including preference reflow, recreation, rotation, replacement, lifecycle, and reopen around the last successfully presented Reader0 spine/byte anchor. Controlled imported-book Resume restored semantic location 1:14:1756; this is never a Cartesian-combination claim. | Preserve a semantic location across every preference, viewport, application-version, physical device, and synchronized-device change. |
| Cover grid and list | Match | Text rows with a synchronous 16dp outer Library gutter; no cover grid yet | Crisp cover extraction/caching, placeholders, reading progress, responsive grid/list, and accessible labels. |
| Book metadata | Match | Reader0-validated title | Author, series, cover, language, identifiers, file facts, and user-correctable display metadata. |
| Sort and filter | Match | Recent-import ordering only | Recent, title, author, series, progress, unread/finished, downloaded, and format where applicable. |
| Collections | Match | Missing | User-defined collections with deterministic ordering and exportable membership. |
| Library search | Match | Missing | Fast local title, author, series, and metadata search with no network dependency. |
| Remove | Improve | Managed-copy removal; provider source retained | Confirmation or undo, clear data-retention choice, synchronization tombstone, and recoverable failure. |
| Locate/re-import/repair | Improve | Missing | Reconnect missing content without discarding position or annotations; verify identity before relinking. |
| Import/export library data | Improve | Missing | Documented portable backup for catalog, preferences, annotations, and indexes; never require an 8vo server. |

## Reading surface and typography

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| Reflowable paged reading | Match | Port 6 accepts canonical Reader0 pages. Port 7 keeps preference-driven Reader0 rebuilds around the last successfully presented semantic anchor, with accepted 36/36 emulator/iQOO evidence. Port 8's corrected Reader0 fully paginates predecessor spines at or below 16 KiB from byte zero, removing path-dependent reverse-page phase; corrected API 36 and API 34 physical pagination/reverse-navigation automation passes. A controlled real-book reverse sequence retained the expected intermediate pages, a full reported Dramatis Personae page, and full reported prose pages with no sparse recurrence. Legitimate paragraph/chapter endings and widow/orphan rules may still leave bottom space; broader real-book breadth remains pending. | Publication structure and styling remain legible across supported viewports and preferences. |
| Continuous scrolling | Match/Audit | Missing | Offer where semantically safe; preserve exact location when switching modes. Verify current Kindle book constraints. |
| Font size | Match | Port 8 defaults new, missing, or corrupt global state to 16sp and offers 14, 16, 18, 21, 24, and 28sp. Valid version-1 18sp and version-2 16/18sp default-like records yield 16sp in memory, preserve every other field, mark migration pending, and leave old bytes exact. Every valid version-3 choice, including 14sp, remains exact because the record has no default-versus-explicit provenance. Version 3 publishes only after the first accepted reader frame; atomic-save failure remains pending, visible, and retryable. Focused API 36 and API 34 appearance, migration, persistence, fresh-default, and 130%-system-text automation passes; hands-on scale review remains pending. | Immediate preview, broader accessible range where justified, broader physical scale/device evidence, and exact semantic resume. |
| Font family | Improve | Port 7 offers Literary system serif and Clear system sans serif with four styles, no bundled font, and a bounded one-entry sparse-atlas cache. Atlas version 2 maps 233 sorted codepoints with validated direct ASCII/Latin-1 lookup, bounded fallback, and missing-glyph diagnostics. Accepted Port 7 automation passed and controlled iQOO Resume reported zero missing glyphs; broader script/fallback acceptance remains pending. | Curated high-quality reading faces plus publisher-font choice, style coverage, licensing, and deterministic fallback. |
| Weight/boldness | Match/Audit | Publisher bold style only | Reader-selectable weight where the face supports it without damaging publisher emphasis. |
| Line spacing | Match | Port 7 offers global 1150, 1250, 1300, and 1500-permille presets with a keyed atlas rebuild and canonical reflow. Accepted Port 7 emulator/iQOO matrices passed; broader representative-book clipping review remains pending. | Stable baseline, broader physical clipping evidence, and representative-book review. |
| Margins/content width | Match | Port 7 offers Wide, Balanced, and Focused global width targets within Readerview0 geometry. Port 8 now reserves two base vertical insets above reader content and one below it, shrinking canonical content height so Reader0 reflows instead of borrowing the bottom gutter. Exact API 36 geometry, overlay-neutrality, reflow, recreation, and surface-replacement automation passes; a fresh API 34 capture places first ink 85px below the app content edge while retaining the canonical bottom reserve. Publisher spacing and widow/orphan carries may still produce a one-line last-glyph difference; the guaranteed page-edge reserve stays stable. Broader physical preference-extreme review remains. | Tablet maximum line width, multi-window/foldable behavior, safe insets, and broader physical preference-extreme evidence. |
| Alignment and hyphenation | Match | Reader0 0.7.0-dev/API 7 retains the accepted API 6 authoritative `soft_wrapped` provenance on canonical styled rows. One allocation-free 8vo plan shared by desktop and Android uses overflow-safe exact-fill arithmetic for eligible Publisher prose while preserving hard/preformatted whitespace and natural final/hard-line spacing. Reader0 remains the line-breaking authority and language-aware hyphenation is absent. Corrected Reader0, strict Windows 8vo, re10, API 36, and API 34 physical gates pass; controlled real-book prose remained full, while representative physical-script review remains. | Validate advanced justification across representative scripts and structures; add language-aware hyphenation where semantically safe. |
| Publisher formatting control | Match | Port 7 makes publisher colors explicitly Theme safe or Allow, with High contrast always authoritative; broader publisher styling remains partial. | Clear publisher/default style policy without making meaningful content unreadable. |
| Page and progress display | Match | Port 8 offers Chapter, meaningful Page, canonical Location, and Percentage choices from the last successfully presented Reader0 state. The global choice and current position publish only after accepted presentation. Corrected API 36 and API 34 physical reflow, lifecycle, surface-replacement, restart, and reopen gates pass; controlled real-book Chapter navigation and exact Return also passed. | Complete audible accessibility and broader representative-book review. |
| Orientation and multi-window | Match | Port 6 accepts lifecycle/surface recreation. Accepted Port 7 emulator/iQOO rotation and semantic-anchor evidence passed; multi-window and foldable acceptance remain pending. | Position-stable rotation, foldable/tablet layouts, split screen, and configuration changes. |
| Immersive reader chrome | Improve | Port 7 presents one borderless canonical full-viewport native page. Ordinary entry/reopen starts hidden; visible chrome uniformly scales the same Surface with no Previous/Next buttons, repagination, redraw, or location change. Accepted Port 7 emulator/iQOO automation, actual-Resume entry regression, and controlled iQOO Resume with no visible reader controls passed. Hands-on reduced-motion review remains pending. | Edge/system-bar coherence, no accidental navigation or bright frames, and verified physical reduced-motion behavior. |
| Page-turn behavior | Improve | Deterministic tap zones, horizontal swipes, hidden virtual actions, and Page Up/Page Down or D-pad share one native presentation gate. Port 8 prepares each static candidate once, requires the image-verification snapshot and present to reuse an exact lifecycle/surface/layout/page token, retains it across bounded forced failures, and consumes it only after accepted commit. Corrected 5/5 presentation-gate stress, 11/11 image/prepared-frame, and full 67/67 API 36/API 34 automation pass. Five deliberately waited reverse turns on the real book retained every expected intermediate page without a sparse recurrence or visible bright/black transition; broader subjective touch review remains. | Configurable tap/gesture map, RTL awareness, optional restrained transition, and zero skipped/unpresented pages. |
| Themes | Improve | Accepted Port 7 emulator/iQOO automation verified all six independently tuned Android palette identities and deterministic role/pixel behavior. Android colors remain intentionally platform-tuned while the Windows catalog stays centralized. Extended physical theme and dark-room review remain pending. | Verified contrast and comfort across reader, settings, library return, dialogs, system bars, launch, selection, search, highlights, and failures. |
| Brightness/dimming | Improve | System brightness only | Reader-controlled page comfort where Android permits, with explicit reset and no illegible system UI. |
| Reader-entry performance | Improve | Android native debug builds use `-O2` with symbols. The metric runs from first `showReader` through the first accepted native unlock/post. Accepted Port 7 controlled iQOO Resume reported 138ms with 24ms total native stages; restored cold Library start was 287ms. The strict 1500ms gate passed; older timings remain historical and differently bounded. | Repeat on representative devices/books; establish and continuously enforce cold/warm latency, frame, memory, and energy budgets. |

## Navigation and retrieval

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| Hierarchical table of contents | Match | Port 8 renders bounded Reader0 EPUB 2/3 or spine-fallback rows with nesting, current-section state, destination progress, and Android hierarchy semantics. The correction exposes a contained synthetic anchor for image-only pages immediately and after reopen. Corrected API 36 and API 34 physical nested/flat/absent/malformed/partially-invalid, image-only, and hierarchy automation passes in the 67/67 matrices. On the real book, the first MAPS destination opened without error and presented three genuine map leaves before Dramatis Personae. | Complete audible TalkBack and broader representative-book review. |
| Go to page/location/percentage | Match | Port 8 validates canonical one-based Location, meaningful layout-relative Page, and Percentage before invoking Reader0; Chapter is a separate Reader0 high-level operation. Jumps remain provisional until exact prepared-frame presentation. Corrected full 67/67 API 36/API 34 and 5/5 presentation-gate stress automation pass; controlled physical Chapter destinations passed, while broader subjective destination review remains pending. | Complete subjective physical destination review. |
| Chapter navigation | Match | Port 8 calls Reader0 directly. Only tokenized, exact namespace-qualified `epub:type` chapter semantics win; with none, a complete source-order contiguous decimal/canonical-Roman/English numbered-label model may qualify, otherwise resolution fails closed. Unqualified/XSI type, NCX class/depth, malformed tails, gaps, reversals, and duplicates do not become chapters. Shared Reader0 and corrected API 36/API 34 tests pass; on the real book, Chapter `1` and `2` resolved to Chapter One and Chapter Two. Broader representative physical EPUB coverage remains pending. | Extend representative physical EPUB coverage. |
| Navigation history | Improve | Port 8 uses Reader0's bounded session back/forward model for Contents and Go-to only; entries are exposed only after destination presentation. Corrected Return/Forward, rapid-input, failure, prepared-frame rollback, recreation, and external restart automation passes on API 36 and API 34; history still correctly does not survive process death. Controlled real-book Return restored the prior prose origin exactly; broader subjective interaction review remains pending. | Complete subjective physical interaction review; extend the shared model only when future consumers are implemented. |
| Page preview/scrubber | Improve | Missing | Page Flip-class spatial preview with thumbnails/chapter landmarks and an explicit return to committed position. |
| In-book full-text search | Match | Missing on Android | Local index, Unicode-aware query, useful snippets, hit emphasis, structural grouping, jump/back, and bounded failure. |
| Link navigation | Match | Partial engine surface, no Android UX | Internal/external distinction, safe external handoff, visited/return state, and accessible actions. |
| Footnote preview | Improve | Missing | Non-destructive contextual preview when possible, full destination fallback, and return history. |

## Selection, annotations, and knowledge

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| Text selection | Match | Missing on Android | Precise handles, word/paragraph expansion, scrolling, style-aware geometry, TalkBack actions, and stable anchors. |
| Multi-color highlights | Improve | Missing | Theme-tuned accessible palette, overlap policy, exact anchor durability, and export semantics. |
| Notes | Match | Missing | Create/edit/delete, visible association, autosave/recovery, and conflict-safe durable storage. |
| Bookmarks | Match | Missing | Fast toggle, visible state, named/listed destination, and durable anchor. |
| Annotations workspace | Match | Missing | Book/chapter grouping, snippets, color/type/date filters, favorites, notes, and direct navigation. |
| Annotation search | Match | Missing | Local query across note text and highlighted source with structural context. |
| Share/export annotations | Improve | Missing | User-selected plain text/Markdown/JSON export with citation metadata and publisher-aware excerpt safeguards. |
| Dictionary lookup | Local alternative | Missing | Offline/user-installed dictionary first, clear language choice, morphology where available, and optional explicit handoff. |
| Translation | Local alternative | Missing | User-selected local/OS/provider action; no silent text upload and no core-reading dependency. |
| Web/encyclopedia lookup | Local alternative | Missing | Explicit external action with privacy disclosure and easy return to reading. |
| Vocabulary builder | Local alternative | Missing | User-owned saved words, context, lookup result, review/export, and no proprietary service requirement. |
| X-Ray-like entities | Local alternative/Audit | Missing | Optional transparent local index if useful; do not promise Amazon's proprietary metadata. |
| Word Wise-like help | Local alternative/Audit | Missing | Optional local graded assistance only if quality, licensing, and reader control are credible. |

## EPUB and media fidelity

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| CSS/style cascade | Match | Reader0 subset | Mainstream EPUB typography, inheritance, units, display, spacing, and safe user overrides with regression fixtures. |
| Kerning, ligatures, shaping | Improve | Sparse 233-codepoint Android raster atlas with publication punctuation and missing-glyph diagnostics; still limited shaping/fallback | Script-aware shaping, font fallback, bidi, style fidelity, and deterministic layout evidence. |
| Embedded fonts | Content-dependent | Missing | License-respecting load, publisher/default toggle, fallback, malformed-font isolation, and memory bounds. |
| Images and illustrations | Content-dependent | Not in accepted Port 7. The Port 8 candidate has narrow image-only and ordinary in-flow support: Reader0-owned resources/anchors/`visual_units`; same-archive encoded-size preflight before allocation/decompression; serial Java decode with per-resource caps plus cumulative 16-MiB encoded and 8,388,608-pixel decoded budgets per presentation; terminal `CacheFull` after exhaustion; a 32-entry/32-MiB native LRU with current-frame pinning; aspect fit, fallback, and presentation gating. Reader0 adversarial smoke, corrected 11/11 image/prepared-frame, and full 67/67 API 36/API 34 gates pass. Controlled real-book review presented three genuine map leaves without error; broader media-fidelity review remains pending. | Add caption/alt text, zoom/pan, broader formats/color-theme policy, whole-archive bomb limits, and representative lifecycle/fidelity coverage. |
| Tables | Content-dependent | Missing | Legible layout, horizontal navigation or focused view, selection, and accessibility. |
| Footnotes/endnotes | Content-dependent | Links only/partial | Semantic references, preview when possible, full navigation, and return. |
| Lists, block quotes, poetry, drop caps | Content-dependent | Partial; Port 7 no longer justifies explicit publisher hard lines such as `<br>` verse, and the earlier post-feedback APK's exact-book punctuation/verse evidence passed with zero missing glyphs historically | Preserve structure and intentional rhythm across font and width changes. |
| MathML | Content-dependent/Audit | Missing | Accessible math representation and bounded fallback; benchmark availability depends on content. |
| RTL and complex scripts | Match | Not accepted | Correct bidi, page direction, touch zones, shaping, selection, search, and navigation. |
| Vertical/CJK writing | Audit | Missing | Decide target after engine feasibility and content audit; record any explicit limitation. |
| Fixed-layout books | Content-dependent | Missing | Separately designed fixed-layout backend behavior; zoom, pan, orientation, and accessibility. |
| Comics/guided panels | Local alternative/Content-dependent | Missing | Separately scoped visual-reading experience; do not copy proprietary Guided View data or motion. |
| Audio/video in books | Audit | Missing | Decide after EPUB media, platform, battery, accessibility, and security requirements are specified. |

## Accessibility and comfort

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| TalkBack | Improve | Port 8 retains the bounded reader virtual tree and adds a native Android Contents hierarchy, navigation controls, validation states, and presentation-gated announcements. Corrected API 36 and API 34 physical semantics, hierarchy, focus order, actions, and 48dp automation pass, including both 15/15 large-text matrices; audible touch exploration remains pending. | Extend publication semantics with accurate headings, lists, links, alt text, tables, notes, and language. |
| Large text/display scale | Improve | Port 7 offers six reader sizes from 14sp through 28sp. Port 8's corrected Contents/Go-to/accessibility matrix passed 15/15 at 130% system text on API 36 and 15/15 on the API 34 iQOO in 16.185 seconds of instrumentation time (18.591 seconds wall). Emulator settings restored exactly. On the iQOO, the settings database restored exactly to font scale `1.0`, window and transition scales `1.0`, and the previously absent animator-scale key. A later hands-on check found that some vivo SystemUI animations still behaved as disabled; explicitly setting the animator duration scale to `1.0` repaired the device. Broader-device validation remains pending. | No clipped/hidden actions at supported scales; broaden device coverage. |
| Touch targets and alternate actions | Match | Port 8 retains the 48dp token, virtual actions, Page Up/Page Down, D-pad, keyboard focus, and accessible Contents/Go-to/Return actions without visible Previous/Next buttons. Corrected API 36 automation passes; hands-on keyboard/switch review remains pending. | Minimum accessible targets, switch/keyboard paths, labels, focus order, and no gesture-only essential action. |
| Contrast choices | Improve | Accepted Port 7 emulator/iQOO automation verified deterministic role thresholds for five comfort palettes plus independently tuned High contrast. Subjective comfort verification remains pending. | Comfortable themes plus verified high-contrast alternatives; never force one contrast profile on every reader. |
| Reduced motion | Improve | Port 8 retains the durable explicit zero-duration choice. Its corrected navigation surface passed 15/15 with system animations disabled on API 36 and API 34. Emulator settings restored exactly. The iQOO settings database restored exactly to font scale `1.0`, window and transition scales `1.0`, and the previously absent animator-scale key, but a later vivo SystemUI behavior check found that some animations still behaved as disabled. Explicitly setting the animator duration scale to `1.0` repaired the device. No visible bright/black transition appeared during controlled physical review; user subjective quality and automatic system-preference following remain pending. | Coordinate physical runs with the user; do not disable device-wide animations outside the test window. Restore window, transition, and animator scales explicitly to `1.0`, then verify visible behavior as well as stored values. Honor system preference and provide a non-animated equivalent for every transition. |
| Screen-reader publication content | Match | Port 7 exposes bounded current-page text and essential controls, not headings, lists, links, images, tables, notes, or language structure. | Reading order, headings, lists, links, alt text, tables, notes, and language exposed accurately. |
| Reading aids | Improve/Audit | Missing | Explore focus/ruler, dyslexia-friendly choices, spacing, and per-user presets without claiming medical benefit. |

Android's custom-rendered reader must expose explicit accessibility nodes and
actions; a single canvas or surface node is not sufficient. The implementation
baseline is Android's custom-view accessibility guidance:
<https://developer.android.com/guide/topics/ui/accessibility/views/custom-views>.

## Bring-your-own-storage and synchronization

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| Offline-first operation | Improve | Local reading/library work offline | Every core feature queues safe local changes and remains understandable without connectivity. |
| Google Drive synchronization | Improve | Missing | Least-privilege authorization, explicit enable/disable, progress, retry, metered-network policy, and no hidden 8vo account. |
| Books sync | Improve | Missing | User choice per book/library, digest identity, resumable transfer, storage quota handling, and no duplicate copies. |
| Position/settings/annotations sync | Improve | Local per-book presented positions plus one checksummed version-3 global appearance record. Version-1/version-2 compatibility is non-mutating at load and publication remains successful-presentation-gated; synchronization is missing. | Versioned portable records, deterministic merge, device clock independence, conflict visibility, and migration. |
| Encryption | Improve | App-private local storage only | Specify threat model; offer user-controlled end-to-end protection before claiming private cloud sync. |
| Backup/export/restore | Improve | Missing | Human-triggerable complete backup, documented validation, partial recovery, and forward-compatible restore. |
| Self-hosted provider | Future | Missing | Nextcloud/WebDAV or S3-compatible path after Google Drive proves the sync record and conflict model. |

Google Drive provides a hidden per-application data area as well as
user-visible Drive files. 8vo must decide deliberately which records belong
in each, request the narrowest practical scope, and preserve exportability:
<https://developers.google.com/workspace/drive/api/guides/appdata>.

## Explicit service exclusions and substitutes

| Kindle ecosystem capability | 8vo decision |
| --- | --- |
| Amazon bookstore, samples, subscriptions, recommendations, and advertising | **Excluded service.** Import user-owned content and integrate with Android file/share flows. |
| Amazon DRM and account entitlements | **Excluded service.** Do not circumvent DRM; clearly report unsupported protected content. |
| Send to Kindle and Amazon device delivery | **Excluded service.** Provide local import plus user-controlled storage/sync. |
| Whispersync/Amazon cloud library | **Excluded service.** Provide Google Drive first, then genuinely self-hostable storage. |
| Audible narration synchronization | **Excluded service/Audit.** A future open audiobook/read-along feature requires a separate product design and lawful media. |
| Goodreads/social network | **Excluded service.** Export/share through user-selected Android destinations; social reading is a separate opt-in design. |

## Parity-audit procedure

Before declaring any category complete:

1. Record the Kindle Android app version, Android/device version, capture date,
   region/account conditions, test title/format, and relevant publisher flags.
2. Exercise the capability on compact phone, large phone/tablet when
   available, light theme, at least two dark themes, large system text, and
   offline mode where relevant.
3. Store raw third-party screenshots only under ignored `local/reference/`.
   Do not commit copyrighted book text, Kindle assets, account information, or
   notification/status-bar secrets.
4. Describe the user need and state transitions neutrally. Do not specify an
   Amazon icon, exact coordinate, proprietary label, or copied animation.
5. Define deterministic, visual, accessibility, performance, lifecycle,
   recovery, and physical acceptance evidence for 8vo.
6. Update this matrix with accepted commit/milestone links. Re-audit before a
   parity release and whenever a material benchmark change is observed.

Amazon's public reader overview currently lists customizable text/layout,
notes/highlights, in-book search, and dictionary lookup. Its publishing
documentation describes enhanced typography, Page Flip, and content-dependent
features. These links are supporting inventory sources, not acceptance proof:

- <https://read.amazon.com/landing>
- <https://kdp.amazon.com/en_US/help/topic/GNY87A6WM6EK6YEE>
- <https://kdp.amazon.com/en_US/help/topic/G42HENP2VHSN8VW8>
