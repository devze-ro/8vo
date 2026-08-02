# Android feature-parity contract

Last reviewed: 2026-08-03. Baseline implementation: accepted Android Port 6
plus the Android Port 7 appearance implementation candidate. Port 7 formal
acceptance is pending.

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

"Current Android baseline" distinguishes accepted Port 6 behavior from Port 7
behavior that is implemented on its milestone branch but not yet accepted.
The current Port 7 refinement's 27/27 API 36 result, desktop regressions,
real-book timing, semantic, and pixel evidence supports only the claims named
in this matrix; it is not hands-on accessibility, extended-reading, or
subjective comfort acceptance.
Physical-iQOO results recorded for the preceding candidate are historical and
superseded. The current APK must complete a new physical-device run.

## Library and ownership

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| Local EPUB import | Improve | Android document picker, bounded managed copy, Reader0 validation | Multi-select and visible import progress; clear duplicate, invalid, full-storage, and cancellation outcomes. |
| Multiple-book catalog | Match | Built-in sample plus 63 imported books | Scale without losing bounded/recoverable storage guarantees; migrate catalog versions safely. |
| Exact per-book resume | Improve | Port 6 accepts exact same-layout resume. At default system scale, the current Port 7 API 36 matrix exercises every supported preference value sequentially, baseline/extrema round trips, 320 x 480dp and 600 x 840dp viewports, portrait/landscape, rotation, replacement, recreation, and a fresh-process reopen while verifying Reader0 restoration to the page containing the last successfully presented spine/byte anchor. This is not a Cartesian-combination claim. The previous-candidate iQOO result is historical; the current physical rerun is pending. | Preserve a semantic location across every preference, viewport, application-version, physical device, and synchronized-device change. |
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
| Reflowable paged reading | Match | Port 6 accepts canonical Reader0 pages. The current Port 7 API 36 matrix verifies preference-driven canonical rebuilds around a successfully presented semantic anchor, and the user-owned *Gardens of the Moon* EPUB resumed to its saved page in the real-book timing probe. Current physical and hands-on representative-publication acceptance remains pending. | Publication structure and styling remain legible across supported viewports and preferences. |
| Continuous scrolling | Match/Audit | Missing | Offer where semantically safe; preserve exact location when switching modes. Verify current Kindle book constraints. |
| Font size | Match | Port 7 defaults new/global-default readers to 16sp and offers 16, 18, 21, 24, and 28sp. Its version-2 migration changes only the exact version-1 all-default 18sp tuple; version 1 stored no separate intent bit, so every other version-1 tuple and any version-2 18sp choice is preserved. API 36 verifies bounded atlas rebuild, durable storage, nonempty/unclipped atlas-cell and reader ink at tested extrema, semantic reflow, and 130% system text. Current physical scale evidence is pending. | Immediate preview, broader accessible range where justified, broader physical scale/device evidence, and exact semantic resume. |
| Font family | Improve | Port 7's API 36 matrix verifies Literary system serif and Clear system sans serif with four styles, a bounded one-entry atlas cache, and no bundled font; formal and current physical acceptance are pending. | Curated high-quality reading faces plus publisher-font choice, style coverage, licensing, and deterministic fallback. |
| Weight/boldness | Match/Audit | Publisher bold style only | Reader-selectable weight where the face supports it without damaging publisher emphasis. |
| Line spacing | Match | Port 7's API 36 matrix verifies global 1150, 1250, 1300, and 1500-permille presets with a keyed atlas rebuild and canonical reflow. | Stable baseline, broader physical clipping evidence, and representative-book review. |
| Margins/content width | Match | Port 7's API 36 matrix verifies Wide, Balanced, and Focused global width targets within Readerview0 geometry. | Tablet maximum line width, multi-window/foldable behavior, safe insets, and broader physical preference-extreme evidence. |
| Alignment and hyphenation | Match | Port 7's Publisher mode uses one allocation-free 8vo word-spacing plan shared by Windows and Android over validated Reader0 rows; nonterminal eligible rows receive controlled inter-word adjustment. Ragged right preserves natural spacing. Reader0 remains the line-breaking authority and language-aware hyphenation is absent. | Validate advanced justification across representative scripts and structures; add language-aware hyphenation where semantically safe. |
| Publisher formatting control | Match | Port 7 makes publisher colors explicitly Theme safe or Allow, with High contrast always authoritative; broader publisher styling remains partial. | Clear publisher/default style policy without making meaningful content unreadable. |
| Page and progress display | Match | Page index/count and percentage projection | Page/location/time/chapter alternatives, accessible labels, and consistent values after reflow. |
| Orientation and multi-window | Match | Port 6 accepts lifecycle/surface recreation. The current Port 7 API 36 compact/large rotation cases verify semantic-anchor reflow; the previous-candidate iQOO evidence is historical, and current physical, multi-window, and foldable acceptance remain pending. | Position-stable rotation, foldable/tablet layouts, split screen, and configuration changes. |
| Immersive reader chrome | Improve | Port 7 presents one borderless canonical full-viewport native page. Hidden chrome is the identity composition; visible chrome uniformly scales and translates that same Surface between Android controls without repagination, redraw, or semantic-location change. Transition gestures are canceled/gated, and a synchronous target-theme cover masks the initial Surface layer. Current API 36 semantic and pixel evidence found no black or near-black sampled app frame; current physical touch/transition acceptance is pending. | Edge/system-bar coherence, no accidental navigation or bright frames, and verified physical reduced-motion behavior. |
| Page-turn behavior | Improve | Deterministic tap zones now share one gate with appearance/reflow generations; no page-turn animation is added. | Configurable tap/gesture map, RTL awareness, optional restrained transition, and zero skipped/unpresented pages. |
| Themes | Improve | Current API 36 evidence verifies all six independently tuned Android palette identities, role contrast thresholds, exact native pixels, persisted night cold-open, and composed transitions. The shared Windows catalog centralizes six stable desktop IDs, labels, UI0 mappings, and reader/search/highlight roles; Android palettes remain intentionally tuned for their own surfaces instead of forcing pixel equivalence. Current physical captures and subjective dark-room comfort remain pending. | Verified contrast and comfort across reader, settings, library return, dialogs, system bars, launch, selection, search, highlights, and failures. |
| Brightness/dimming | Improve | System brightness only | Reader-controlled page comfort where Android permits, with explicit reset and no illegible system UI. |
| Reader-entry performance | Improve | Android native debug builds use `-O2` with symbols. Whole-book location metadata no longer blocks the first page; bounded one-spine warming begins only after successful presentation. For an already imported user-owned *Gardens of the Moon* EPUB, Library-to-Resume was externally observed at 411ms and native creation-to-success at 220ms. Target-theme transition samples contained no black or near-black app frame. | Repeat on current physical devices and representative books; establish and continuously enforce cold/warm latency, frame, memory, and energy budgets. |

## Navigation and retrieval

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| Hierarchical table of contents | Match | Missing | Nested headings, current-section indication, destination progress/page, fast jump, and TalkBack hierarchy. |
| Go to page/location/percentage | Match | Missing | Validate each input, use Reader0 navigation, and preserve a return point. |
| Chapter navigation | Match | Cross-spine page movement only | Previous/next structural destination without host-side EPUB logic. |
| Navigation history | Improve | Missing | Reversible stack across TOC, link, search, footnote, annotation, and preview jumps. |
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
| Kerning, ligatures, shaping | Improve | Android raster atlas; limited shaping | Script-aware shaping, font fallback, bidi, style fidelity, and deterministic layout evidence. |
| Embedded fonts | Content-dependent | Missing | License-respecting load, publisher/default toggle, fallback, malformed-font isolation, and memory bounds. |
| Images and illustrations | Content-dependent | Not in accepted Android page | Decode, fit, caption/alt text, zoom/pan, color-theme policy, memory/bomb limits, and lifecycle recovery. |
| Tables | Content-dependent | Missing | Legible layout, horizontal navigation or focused view, selection, and accessibility. |
| Footnotes/endnotes | Content-dependent | Links only/partial | Semantic references, preview when possible, full navigation, and return. |
| Lists, block quotes, poetry, drop caps | Content-dependent | Partial | Preserve structure and intentional rhythm across font and width changes. |
| MathML | Content-dependent/Audit | Missing | Accessible math representation and bounded fallback; benchmark availability depends on content. |
| RTL and complex scripts | Match | Not accepted | Correct bidi, page direction, touch zones, shaping, selection, search, and navigation. |
| Vertical/CJK writing | Audit | Missing | Decide target after engine feasibility and content audit; record any explicit limitation. |
| Fixed-layout books | Content-dependent | Missing | Separately designed fixed-layout backend behavior; zoom, pan, orientation, and accessibility. |
| Comics/guided panels | Local alternative/Content-dependent | Missing | Separately scoped visual-reading experience; do not copy proprietary Guided View data or motion. |
| Audio/video in books | Audit | Missing | Decide after EPUB media, platform, battery, accessibility, and security requirements are specified. |

## Accessibility and comfort

| Capability | Target | Current Android baseline | Completion requirement |
| --- | --- | --- | --- |
| TalkBack | Improve | Current API 36 automation verifies a bounded page/previous/next/read-only-progress virtual tree, Java/virtual ownership changes, labels, values, ranges, presentation-gated actions, events, and the absence of duplicate native progress while chrome is hidden. A previous-candidate real-device traversal is historical; current physical audible TalkBack/touch-exploration acceptance and full publication semantics are pending. | Expose document structure, text, progress, controls, actions, selection, and navigation through a coherent accessibility tree. |
| Large text/display scale | Improve | Port 7 adds five reader sizes and current API 36 130% tests pass without losing the essential settings/title actions. Previous-candidate physical evidence is historical; current physical scales and other devices remain pending. | No clipped/hidden actions at supported scales; reader preference and system UI remain usable independently. |
| Touch targets and alternate actions | Match | Current API 36 automation verifies the 48dp token, virtual click/scroll/show-on-screen actions, labels, visible/hidden chrome ownership, explicit focus clear, disabled end boundaries, and read-only Progress. Current physical touch, keyboard, and switch review remain pending. | Minimum accessible targets, switch/keyboard paths, labels, focus order, and no gesture-only essential action. |
| Contrast choices | Improve | Current API 36 evidence verifies deterministic WCAG-style role thresholds for five comfort palettes plus an independently tuned High contrast choice. Previous-candidate physical captures are historical; current physical pixels and subjective comfort verification remain pending. | Comfortable themes plus verified high-contrast alternatives; never force one contrast profile on every reader. |
| Reduced motion | Improve | Current API 36 evidence verifies a durable explicit global choice that reduces appearance token durations to zero; system-following and current physical hands-on review remain pending. | Honor system preference and provide a non-animated equivalent for every transition. |
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
| Position/settings/annotations sync | Improve | Local per-book presented positions plus one checksummed version-2 global appearance record, including a bounded exact-default version-1 migration; synchronization is missing. | Versioned portable records, deterministic merge, device clock independence, conflict visibility, and migration. |
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
