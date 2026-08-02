# Android product vision

Status: product north star, adopted 2026-08-01. This is a living product
contract rather than a completed-feature claim.

## North star

8vo will be a premium, local-first Android reader for user-owned books. It
will match the useful offline reading, library, navigation, search,
annotation, accessibility, and interaction quality of the Kindle Android app
while giving the reader control of files and synchronization. Its night
reading experience should be demonstrably more comfortable.

Kindle is a quality and capability benchmark, not a visual specification. 8vo
must not copy Amazon branding, proprietary assets, exact layouts, or
copyrighted content. It should develop a recognizable 8vo design language and
judge every interaction by whether it feels at least as deliberate, calm, and
trustworthy as the benchmark.

## Product model

The product is **local-first and bring-your-own-storage**:

- imported books, positions, preferences, annotations, indexes, and pending
  operations remain usable without a network connection;
- the user owns the source books and can back up or export all 8vo-created
  data in documented formats;
- Google Drive is the first planned synchronization destination, not an 8vo
  account or content service;
- Google Drive is user-controlled cloud storage, not self-hosting; a later
  self-hosted path may use Nextcloud/WebDAV or S3-compatible storage; and
- the first provider will be implemented concretely. Durable sync records
  should remain portable, but 8vo will not introduce a speculative provider
  framework before a second provider is designed.

8vo is format-neutral at the application level, but EPUB remains its only
current document backend. Additional formats require separately designed,
tested slices rather than a generic document framework.

## Reader promises

| Promise | Product consequence |
| --- | --- |
| The book comes first | Reading chrome is quiet, predictable, and absent when it is not useful. |
| Reading is comfortable for hours | Typography, spacing, themes, brightness, motion, and touch behavior are first-class engineering work. |
| Position is never surprising | Navigation, preview, reflow, restart, and synchronization preserve a reversible, successfully presented location. |
| User data belongs to the user | Books and annotations are never trapped in an 8vo service and are never silently deleted. |
| Offline is normal | Core reading, search, navigation, annotations, and library management do not depend on a server. |
| Accessibility is architecture | TalkBack semantics, scalable controls, contrast choices, alternate text, and non-gesture paths are acceptance requirements. |
| Premium includes reliability | No-data-loss behavior, fast recovery, low latency, battery discipline, and visible failures matter as much as appearance. |

## What premium means

A premium interface is not ornamental complexity. For 8vo it means:

- a stable visual system for color, typography, spacing, shape, iconography,
  elevation, and motion;
- clear hierarchy without crowding, including on compact handsets;
- coherent state across the library, reader, sheets, dialogs, system bars,
  rotation, process recreation, and device changes;
- immediate feedback, reversible destructive actions, and no ambiguous taps;
- content-aware navigation that preserves chapter and reading context;
- motion that explains a transition and never delays reading;
- no blank frames, white flashes, stale pages, lost locations, or silent
  storage failures; and
- measurable visual, interaction, accessibility, performance, and durability
  acceptance gates.

The locally retained 2026-08-01 Kindle reference captures demonstrate useful
patterns without becoming templates:

- a calm hierarchical table of contents with destination positions;
- search results grouped by book structure with useful snippets;
- a browsable annotations workspace with colors, notes, filters, and sharing;
- a reversible page-preview and scrubber experience that does not lose the
  committed reading position; and
- reader controls that appear as a coherent layer while keeping the page
  visually dominant.

The captures contain third-party book text and remain under ignored
`local/reference/`; they are not part of the public repository.

The current Port 7 refinement makes the reading page one borderless canonical
full-viewport surface. Hidden chrome is the identity presentation of that
page; when controls are visible, the Android host uniformly scales and
translates the same native Surface between the controls. Showing chrome does
not reserve permanent page bands, repaginate, redraw, or change the committed
semantic location.

## Night-reading advantage

A single black-background theme is not an acceptable night mode. High local
contrast, cool white text, saturated accents, inconsistent system chrome, and
bright transition frames can all create discomfort even when a palette meets
a minimum contrast ratio.

Port 7 establishes the first bounded implementation of that theme system:

- a warm-charcoal default dark theme with warm off-white body text;
- an optional true-black OLED theme rather than making pure black mandatory;
- paper, sepia, dusk, warm-dark, OLED, and high-contrast presets with
  independently specified semantic color roles;
- separately tuned selection, search, and highlight colors for each surface
  rather than mechanically inverting the light palette;
- coherent library, reader, dialog, sheet, status-bar, navigation-bar, and
  launch colors plus a synchronous target-theme entry cover that masks the
  native Surface's initial compositor layer;
- a global-only durable preference contract, with per-book overrides deferred
  until their ownership and migration behavior are separately specified; and
- generic Android serif and sans-serif choices without introducing an
  unlicensed bundled font.

The global reader default is 16sp. The version-2 appearance migration changes
only an exact version-1 all-default 18sp record to that new default. Version 1
did not store whether that identical tuple was explicitly reselected; every
other version-1 tuple and any version-2 18sp choice is preserved. The bounded
typography atlas is rebuilt only when its family, resolved pixel size, or line
spacing key changes.

The broader product contract still requires:

- an explicit, independently tuned link treatment for every theme;
- user-controlled page brightness or dimming where Android permits it;
- images preserved faithfully unless the user explicitly requests a safe
  image treatment;
- optional schedule/system-theme following without taking away manual choice.

Port 7 implementation is not night-mode acceptance. The current borderless and
performance refinement passed 27/27 API 36 tests, its 130% system-text probe,
both Android ABI builds, the desktop regressions, and fresh-process evidence.
With the user-owned *Gardens of the Moon* EPUB already imported, an externally
observed Library-to-Resume took 411ms, native creation to the successfully
presented frame took 220ms, and the sampled transition contained no black or
near-black app frame. Full evidence and measurement limits are recorded in
`android_port7.md`. Physical-iQOO evidence from the preceding candidate is
historical and superseded; it must be repeated on the current APK. Audible
TalkBack, hands-on keyboard/switch and reduced-motion review, prolonged Paper,
Warm dark, and OLED reading, and subjective dark-room comfort evidence must
still be completed before the foundation is accepted.

Night-mode acceptance requires physical testing in a dark room on at least one
OLED handset and one LCD-class display when available. Automated screenshots
and contrast calculations are necessary but not sufficient; text clarity,
halation, accent intensity, minimum brightness, transitions, and prolonged
comfort must be reviewed by a person.

## Capability boundary

The capabilities below are the long-term parity target. Port 7 deliberately
does not implement a cover library, table of contents, in-book search, text
selection, highlights/notes, or an annotations workspace.

The parity target includes local equivalents for:

- a polished cover-based library, metadata, sorting, filtering, collections,
  import, removal recovery, and local book search;
- high-quality reflowable reading, reader preferences, themes, progress,
  orientation, paged and continuous modes where appropriate;
- table-of-contents navigation, go-to, history, preview/scrubbing, and
  full-text search;
- selection, multi-color highlights, notes, bookmarks, an annotations
  workspace, filtering, export, and sharing;
- dictionary lookup, vocabulary support, and other reference tools through
  offline data or user-selected providers where practical;
- accessibility, multilingual text, images, tables, links, footnotes, and
  mainstream EPUB presentation; and
- user-controlled backup and synchronization of books, positions,
  preferences, annotations, and library metadata.

The following are not parity requirements:

- Amazon storefront, subscriptions, advertising, recommendations, account,
  DRM, delivery, device-fleet, or Send-to-Kindle services;
- Audible integration, Goodreads integration, or Amazon social features;
- proprietary datasets or experiences such as X-Ray and Word Wise exactly as
  Amazon implements them; 8vo may provide transparent local alternatives; or
- copying Kindle branding, trade dress, icons, animations, or screen layouts.

A feature that varies by title, format, region, account, or licensing must be
identified as such in the parity matrix. Absence from a single test book does
not prove absence from Kindle, and presence in Kindle does not automatically
justify coupling 8vo to a service.

## Architecture obligations

The product ambition does not relax the current ownership model:

- 8vo owns Android lifecycle, native-window ownership, touch classification,
  presentation policy, library, file picker, persistence, synchronization,
  accessibility bridge, and Android UI;
- Reader0 owns document interpretation, canonical pagination, locations,
  search/selection primitives that belong to the document engine, and frames;
- Readerview0 owns platform-neutral reader projection and content geometry;
- Android dependencies and storage/provider policy must not enter Ground0,
  Reader0, UI0, or Readerview0;
- `reader0.c`, `ui0.c`, and `readerview0.c` remain compiled exactly once;
- any necessary shared-package change must remain platform-neutral and be
  validated in both 8vo and re10; and
- bounded storage, caller ownership, atomic durability, explicit failure, and
  the successful-presentation gate remain permanent invariants.

Premium surfaces must not compensate for missing engine behavior by
duplicating EPUB interpretation, pagination, search anchoring, selection
anchoring, or navigation logic in Java or the 8vo host.

Port 7 applies that rule concretely. Android owns one global appearance
record, semantic product palettes, generic system-font acquisition,
full-viewport Surface composition, chrome, and the custom-view accessibility
adapter. A layout-affecting change uses Reader0's public canonical location
navigation to rebuild the page that contains the last successfully presented
semantic byte. Showing or hiding chrome only composes the already presented
page; the host does not substitute page-number arithmetic or Java pagination
for either operation.

Windows and Android consume one allocation-free 8vo word-spacing plan over
validated Reader0 rows; the plan does not own line breaking or document
semantics. The shared Windows theme catalog centralizes its six stable IDs,
labels, UI0 mappings, and reader/search/highlight roles. Android palettes are
intentionally independently tuned for Android surfaces and night-reading
conditions instead of forcing pixel equivalence across platforms.

Reader entry uses native debug code built with `-O2` and symbols. Expensive
whole-book location metadata is not warmed before the first page: bounded
one-spine steps run only after a successful presentation and then refresh the
same page's metadata. A synchronous target-theme cover hides the native
Surface's initial compositor layer until that successful frame is ready, and
privacy-safe first-frame timing remains diagnostic evidence rather than a
second presentation authority.

## Evidence standard

Every capability moves through four states: inventoried, specified,
implemented, and accepted. "Implemented" never means "parity achieved" until
the relevant evidence exists.

Depending on the slice, acceptance includes:

- deterministic unit or instrumentation assertions;
- exact dependency and source-consumption guards;
- both Android ABIs and the strict Windows/re10 regression gates;
- visual regression evidence at supported viewport, font-scale, and theme
  combinations;
- TalkBack, large-text, touch-target, keyboard/switch, and reduced-motion
  checks where relevant;
- latency, memory, storage, battery, and recovery budgets;
- physical-device testing, including dark-room review for night surfaces; and
- hands-on comparison against the neutral interaction requirement captured in
  the parity matrix, not against a copied screenshot.

## Reference baseline

The initial capability inventory combines the supplied Kindle Android
captures with public Amazon descriptions. Amazon currently describes reader
control over text size, font, layout, margins, and background; notes and
highlights; in-book search; and dictionary lookup. Amazon's publishing
documentation describes enhanced typography and Page Flip on Android. These
sources establish categories, not a frozen or exhaustive contract:

- <https://read.amazon.com/landing>
- <https://kdp.amazon.com/en_US/help/topic/GNY87A6WM6EK6YEE>
- <https://kdp.amazon.com/en_US/help/topic/G42HENP2VHSN8VW8>
- <https://kdp.amazon.com/en_US/help/topic/GH4DRT75GWWAGBTU>

The installed Kindle application must be audited periodically with its app
version, Android version, region/account conditions, book capabilities, and
capture date recorded locally. `android_feature_parity.md` is the public,
neutral result of that audit and must be updated when the benchmark changes.
