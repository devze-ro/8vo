#ifndef OCTAVO_THEME_H
#define OCTAVO_THEME_H

#include "base/base_core.h"
#include "ui0.h"

/*
 * Stable 8vo product theme identities.
 *
 * The numeric values are persisted by OctavoSettingsFile versions 2 and 3.
 * They are intentionally independent of UI0ThemeProfileKind ordinals.
 */
typedef enum OctavoTheme
{
  OctavoTheme_Dark = 0,
  OctavoTheme_Light = 1,
  OctavoTheme_CoralDark = 2,
  OctavoTheme_CoralLight = 3,
  OctavoTheme_BlueDark = 4,
  OctavoTheme_BlueLight = 5,
  OctavoTheme_Count = 6,
} OctavoTheme;

enum
{
  OctavoReaderHighlightColor_Count = 4,
};

typedef struct OctavoReaderThemeExtensions
{
  U32 search_hit;
  U32 search_match;
  U32 highlight[OctavoReaderHighlightColor_Count];
} OctavoReaderThemeExtensions;

typedef struct OctavoThemeCatalogEntry
{
  OctavoTheme id;
  const char *code;
  const char *label;
  UI0AppearanceMode appearance;
  UI0ThemeProfileKind ui0_profile_kind;
  OctavoReaderThemeExtensions reader;
} OctavoThemeCatalogEntry;

typedef struct OctavoReaderContentTheme
{
  U32 page_background;
  U32 ink;
  U32 ink_secondary;
  U32 ink_muted;
  U32 link;
  U32 selection;
  U32 search_hit;
  U32 search_match;
  U32 user_highlight;
  U32 note_marker;
} OctavoReaderContentTheme;

const OctavoThemeCatalogEntry *octavo_theme_catalog_entry(OctavoTheme theme);
B32 octavo_theme_from_code(const char *code, OctavoTheme *out_theme);
UI0ThemeProfile octavo_theme_profile(OctavoTheme theme);
OctavoReaderContentTheme octavo_reader_content_theme(OctavoTheme theme);
U32 octavo_theme_highlight_color(OctavoTheme theme, U32 color_index);

/* Deterministic product contract exercised by the desktop Reader View smoke. */
B32 octavo_theme_catalog_contract(void);

#endif /* OCTAVO_THEME_H */
