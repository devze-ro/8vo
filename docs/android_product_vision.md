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
  launch colors with direct redraw paths intended to avoid a bright
  intermediate frame;
- a global-only durable preference contract, with per-book overrides deferred
  until their ownership and migration behavior are separately specified; and
- generic Android serif and sans-serif choices without introducing an
  unlicensed bundled font.

The broader product contract still requires:

- an explicit, independently tuned link treatment for every theme;
- user-controlled page brightness or dimming where Android permits it;
- images preserved faithfully unless the user explicitly requests a safe
  image treatment;
- optional schedule/system-theme following without taking away manual choice.

Port 7 implementation is not night-mode acceptance. Its API 36 semantic,
composed-pixel, 130% system-text, dual-ABI, and desktop regression evidence is
recorded in `android_port7.md`. Physical-device, hands-on TalkBack,
prolonged-reading, and dark-room evidence must still be completed before the
new appearance foundation is accepted.

Night-mode acceptance requires physical testing in a dark room on at least one
OLED handset and one LCD-class display when available. Automated screenshots
and contrast calculations are necessary but not sufficient; text clarity,
halation, accent intensity, minimum brightness, transitions, and prolonged
comfort must be reviewed by a person.

## Capability boundary

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
record, semantic product palettes, generic system-font acquisition, overlay
chrome, and the custom-view accessibility adapter. A layout-affecting change
uses Reader0's public canonical location navigation to rebuild the page that
contains the last successfully presented semantic byte. The host does not
substitute page-number arithmetic or Java pagination for that operation.

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
