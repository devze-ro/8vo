# Android feature-parity contract

Last reviewed: 2026-08-03. Baseline implementation: accepted Android Ports 0-7
plus the locally qualified Android Port 8 structural-navigation emulator
candidate against Reader0 `0.7.0-dev` / API 7. Physical iQOO and hands-on
real-book acceptance remain pending.

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
| Exact per-book resume | Improve | Port 6 accepts exact same-layout resume. The final current-source emulator and iQOO 36/36 matrices plus separate ProcessRestart driver passed, including preference reflow, recreation, rotation, replacement, lifecycle, and reopen around the last successfully presented Reader0 spine/byte anchor. Controlled imported-book Resume restored semantic location 1:14:1756; this is never a Cartesian-combination claim. | Preserve a semantic location across every preference, viewport, application-version, physical device, and synchronized-device change. |
| Cover grid and list | Match | Text rows only | Crisp cover extraction/caching, placeholders, reading progress, responsive grid/list, and accessible labels. |
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
| Reflowable paged reading | Match | Port 6 accepts canonical Reader0 pages. Port 7 keeps preference-driven Reader0 rebuilds around the last successfully presented semantic anchor. The final current-source emulator and iQOO 36/36 matrices passed. The private-book full-measure visual diagnostic remains historical. | Publication structure and styling remain legible across supported viewports and preferences. |
| Continuous scrolling | Match/Audit | Missing | Offer where semantically safe; preserve exact location when switching modes. Verify current Kindle book constraints. |
| Font size | Match | Port 7 defaults new, missing, or corrupt global state to 14sp and offers 14, 16, 18, 21, 24, and 28sp. Loading a valid version-1 18sp default, version-2 16sp default, or transitional version-2 18sp value yields 14sp in memory, preserves every other valid field, marks migration pending, and leaves old bytes exact. Version 3 publishes only after the first successfully accepted reader frame; atomic-save failure remains pending, is visible, and retries later. Final current-source migration cases and 130% emulator/iQOO tests passed; broader-device and hands-on scale review remain pending. | Immediate preview, broader accessible range where justified, broader physical scale/device evidence, and exact semantic resume. |
| Font family | Improve | Port 7 offers Literary system serif and Clear system sans serif with four styles, no bundled font, and a bounded one-entry sparse-atlas cache. Atlas version 2 maps 233 sorted codepoints with validated direct ASCII/Latin-1 lookup, bounded fallback, and missing-glyph diagnostics. Final current-source automation passed and controlled iQOO Resume reported zero missing glyphs; broader script/fallback acceptance remains pending. | Curated high-quality reading faces plus publisher-font choice, style coverage, licensing, and deterministic fallback. |
| Weight/boldness | Match/Audit | Publisher bold style only | Reader-selectable weight where the face supports it without damaging publisher emphasis. |
| Line spacing | Match | Port 7 offers global 1150, 1250, 1300, and 1500-permille presets with a keyed atlas rebuild and canonical reflow. Final current-source emulator/iQOO matrices passed; broader representative-book clipping review remains pending. | Stable baseline, broader physical clipping evidence, and representative-book review. |
| Margins/content width | Match | Port 7 offers Wide, Balanced, and Focused global width targets within Readerview0 geometry. Final current-source emulator/iQOO matrices passed; tablet, multi-window/foldable, and broader-device evidence remain pending. | Tablet maximum line width, multi-window/foldable behavior, safe insets, and broader physical preference-extreme evidence. |
| Alignment and hyphenation | Match | Reader0 0.7.0-dev/API 7 retains the accepted API 6 authoritative `soft_wrapped` provenance on canonical styled rows. One allocation-free 8vo plan shared by desktop and Android uses overflow-safe exact-fill arithmetic for eligible Publisher prose while preserving hard/preformatted whitespace and natural final/hard-line spacing. Reader0 remains the line-breaking authority and language-aware hyphenation is absent. Port 8 Reader0, Android emulator, 8vo Windows, and re10 regression gates pass; physical representative-book review remains. | Validate advanced justification across representative scripts and structures; add language-aware hyphenation where semantically safe. |
| Publisher formatting control | Match | Port 7 makes publisher colors explicitly Theme safe or Allow, with High contrast always authoritative; broader publisher styling remains partial. | Clear publisher/default style policy without making meaningful content unreadable. |
| Page and progress display | Match | Port 8 offers Chapter, meaningful Page, canonical Location, and Percentage choices from the last successfully presented Reader0 state. The global choice and current position publish only after accepted presentation. Emulator reflow, lifecycle, surface replacement, process restart, and reopen gates pass. | Complete physical accessibility and representative real-book review. |
| Orientation and multi-window | Match | Port 6 accepts lifecycle/surface recreation. Final current-source emulator/iQOO rotation and semantic-anchor evidence passed; multi-window and foldable acceptance remain pending. | Position-stable rotation, foldable/tablet layouts, split screen, and configuration changes. |
| Immersive reader chrome | Improve | Port 7 presents one borderless canonical full-viewport native page. Ordinary entry/reopen starts hidden; visible chrome uniformly scales the same Surface with no Previous/Next buttons, repagination, redraw, or location change. Final current-source emulator/iQOO automation, actual-Resume entry regression, and controlled iQOO Resume with no visible reader controls passed. Hands-on reduced-motion review remains pending. | Edge/system-bar coherence, no accidental navigation or bright frames, and verified physical reduced-motion behavior. |
| Page-turn behavior | Improve | Deterministic tap zones, horizontal swipes, hidden virtual actions, and Page Up/Page Down or D-pad share one native presentation gate. Swipe classification cancels the native tap, and lifecycle/surface boundaries clear gesture state. Final current-source emulator/iQOO navigation automation passed; no page-turn animation is added. | Configurable tap/gesture map, RTL awareness, optional restrained transition, and zero skipped/unpresented pages. |
| Themes | Improve | Final current-source emulator/iQOO automation verified all six independently tuned Android palette identities and deterministic role/pixel behavior. Android colors remain intentionally platform-tuned while the Windows catalog stays centralized. Extended physical theme and dark-room review remain pending. | Verified contrast and comfort across reader, settings, library return, dialogs, system bars, launch, selection, search, highlights, and failures. |
| Brightness/dimming | Improve | System brightness only | Reader-controlled page comfort where Android permits, with explicit reset and no illegible system UI. |
| Reader-entry performance | Improve | Android native debug builds use `-O2` with symbols. The metric runs from first `showReader` through the first accepted native unlock/post. Current controlled iQOO Resume reported 138ms with 24ms total native stages; restored cold Library start was 287ms. The strict 1500ms gate passed; older timings remain historical and differently bounded. | Repeat on representative devices/books; establish and continuously enforce cold/warm latency, frame, memory, and energy budgets. |

## Navigation and retrieval

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| Hierarchical table of contents | Match | Port 8 renders bounded Reader0 EPUB 2/3 or spine-fallback rows with nesting, current-section state, destination progress, and Android hierarchy semantics. Deterministic nested, flat, absent, malformed, partially invalid, UTF-8, truncation, focus, touch-target, and hierarchy gates pass. | Complete audible TalkBack and representative physical real-book review. |
| Go to page/location/percentage | Match | Port 8 validates chapter, canonical one-based location, meaningful layout-relative page, and percentage before invoking Reader0. Jumps are provisional until presentation; deterministic, lifecycle, failure, and reflow emulator gates pass. | Complete physical real-book destination review. |
| Chapter navigation | Match | Port 8 selects Reader0 structural destinations without interpreting EPUB in Java. Deterministic EPUB 2/3 and fallback current-section/destination gates pass. | Complete representative physical EPUB review. |
| Navigation history | Improve | Port 8 uses Reader0's bounded session back/forward model for Contents and Go-to only; entries are exposed only after destination presentation. Exact Return/Forward, rapid-input, render/lifecycle/surface failure, recreation, and process-restart gates pass; history correctly does not survive process death. | Complete physical interaction review; extend the shared model only when future consumers are implemented. |
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
| Images and illustrations | Content-dependent | Not in accepted Android page | Decode, fit, caption/alt text, zoom/pan, color-theme policy, memory/bomb limits, and lifecycle recovery. |
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
| TalkBack | Improve | Port 8 retains the bounded reader virtual tree and adds a native Android Contents hierarchy, navigation controls, validation states, and presentation-gated announcements. Emulator semantics, hierarchy, focus order, actions, and 48dp targets pass; audible touch-exploration review remains pending. | Extend publication semantics with accurate headings, lists, links, alt text, tables, notes, and language. |
| Large text/display scale | Improve | Port 7 offers six reader sizes from 14sp through 28sp. Port 8's focused Contents/Go-to/accessibility matrix passes at 130% system text on API 36, including compact/large layout coverage; broader physical evidence remains pending. | No clipped/hidden actions at supported scales; reader preference and system UI remain usable independently. |
| Touch targets and alternate actions | Match | Port 8 retains the 48dp token, virtual actions, Page Up/Page Down, D-pad, keyboard focus, and accessible Contents/Go-to/Return actions without visible Previous/Next buttons. Emulator automation passes; hands-on keyboard/switch review remains pending. | Minimum accessible targets, switch/keyboard paths, labels, focus order, and no gesture-only essential action. |
| Contrast choices | Improve | Final current-source emulator/iQOO automation verified deterministic role thresholds for five comfort palettes plus independently tuned High contrast. Subjective comfort verification remains pending. | Comfortable themes plus verified high-contrast alternatives; never force one contrast profile on every reader. |
| Reduced motion | Improve | Port 8 retains the durable explicit zero-duration choice and its navigation surface passed with Android system animations disabled. Hands-on physical quality and automatic system-preference following remain pending. | Honor system preference and provide a non-animated equivalent for every transition. |
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
