#include "octavo_theme.h"

#include <string.h>

_Static_assert(OctavoTheme_Dark == 0,
               "octavo dark theme persistence id changed");
_Static_assert(OctavoTheme_Light == 1,
               "octavo light theme persistence id changed");
_Static_assert(OctavoTheme_CoralDark == 2,
               "octavo coral-dark theme persistence id changed");
_Static_assert(OctavoTheme_CoralLight == 3,
               "octavo coral-light theme persistence id changed");
_Static_assert(OctavoTheme_BlueDark == 4,
               "octavo blue-dark theme persistence id changed");
_Static_assert(OctavoTheme_BlueLight == 5,
               "octavo blue-light theme persistence id changed");
_Static_assert(OctavoTheme_Count == 6,
               "octavo theme persistence catalog changed");

static const OctavoThemeCatalogEntry octavo_theme_catalog[] = {
  {
    OctavoTheme_Dark, "dark", "Dark", UI0AppearanceMode_Dark,
    UI0ThemeProfile_Dark,
    {
      0x004A4947U, 0x008F430FU,
      {0x004D4A16U, 0x0047355CU, 0x002A4662U, 0x00523F1CU},
    },
  },
  {
    OctavoTheme_Light, "light", "Light", UI0AppearanceMode_Light,
    UI0ThemeProfile_Light,
    {
      0x00D8D7D4U, 0x00F6B36FU,
      {0x00FFF2A6U, 0x00FFD4ECU, 0x00CDE7FFU, 0x00FFDCA8U},
    },
  },
  {
    OctavoTheme_CoralDark, "coral-dark", "Coral Dark",
    UI0AppearanceMode_Dark, UI0ThemeProfile_CoralDark,
    {
      0x0062605EU, 0x009A3034U,
      {0x00524A25U, 0x0047355CU, 0x002A4662U, 0x00523F1CU},
    },
  },
  {
    OctavoTheme_CoralLight, "coral-light", "Coral Light",
    UI0AppearanceMode_Light, UI0ThemeProfile_CoralLight,
    {
      0x00D4D0CCU, 0x00EE9B94U,
      {0x00F4DFA3U, 0x00FFD4ECU, 0x00CDE7FFU, 0x00FFDCA8U},
    },
  },
  {
    OctavoTheme_BlueDark, "blue-dark", "Blue Dark",
    UI0AppearanceMode_Dark, UI0ThemeProfile_BlueDark,
    {
      0x003D454EU, 0x004F64BFU,
      {0x004D4A16U, 0x0047355CU, 0x002A4662U, 0x00523F1CU},
    },
  },
  {
    OctavoTheme_BlueLight, "blue-light", "Blue Light",
    UI0AppearanceMode_Light, UI0ThemeProfile_BlueLight,
    {
      0x00D6DADFU, 0x008BAEFFU,
      {0x00FFF2A6U, 0x00FFD4ECU, 0x00CDE7FFU, 0x00FFDCA8U},
    },
  },
};

_Static_assert(ARRAY_COUNT(octavo_theme_catalog) == OctavoTheme_Count,
               "octavo theme catalog must cover every stable identity");

static U32
octavo_theme_rgb(UI0Color color)
{
  return ((U32)color) & 0x00FFFFFFU;
}

static const OctavoThemeCatalogEntry *
octavo_theme_catalog_entry_or_dark(OctavoTheme theme)
{
  const OctavoThemeCatalogEntry *entry = octavo_theme_catalog_entry(theme);
  return entry ? entry : octavo_theme_catalog + OctavoTheme_Dark;
}

const OctavoThemeCatalogEntry *
octavo_theme_catalog_entry(OctavoTheme theme)
{
  U32 id = (U32)theme;
  if (id >= OctavoTheme_Count) return 0;
  const OctavoThemeCatalogEntry *entry = octavo_theme_catalog + id;
  return entry->id == theme ? entry : 0;
}

B32
octavo_theme_from_code(const char *code, OctavoTheme *out_theme)
{
  if (!code || !out_theme) return 0;
  for (U32 index = 0; index < OctavoTheme_Count; index += 1)
  {
    const OctavoThemeCatalogEntry *entry = octavo_theme_catalog + index;
    if (strcmp(code, entry->code) == 0)
    {
      *out_theme = entry->id;
      return 1;
    }
  }
  return 0;
}

UI0ThemeProfile
octavo_theme_profile(OctavoTheme theme)
{
  const OctavoThemeCatalogEntry *entry =
    octavo_theme_catalog_entry_or_dark(theme);
  UI0ThemeProfile result =
    ui0_theme_profile_for_kind(entry->ui0_profile_kind);
  result.code = entry->code;
  result.label = entry->label;
  result.appearance = entry->appearance;
  return result;
}

OctavoReaderContentTheme
octavo_reader_content_theme(OctavoTheme theme)
{
  const OctavoThemeCatalogEntry *entry =
    octavo_theme_catalog_entry_or_dark(theme);
  UI0ThemeProfile profile =
    ui0_theme_profile_for_kind(entry->ui0_profile_kind);
  const UI0Color *color = profile.resolved.colors;
  return (OctavoReaderContentTheme){
    .page_background = octavo_theme_rgb(color[UI0ColorRole_Surface]),
    .ink = octavo_theme_rgb(color[UI0ColorRole_TextPrimary]),
    .ink_secondary = octavo_theme_rgb(color[UI0ColorRole_TextSecondary]),
    .ink_muted = octavo_theme_rgb(color[UI0ColorRole_TextMuted]),
    .link = octavo_theme_rgb(color[UI0ColorRole_Accent]),
    .selection = octavo_theme_rgb(color[UI0ColorRole_Selection]),
    .search_hit = entry->reader.search_hit,
    .search_match = entry->reader.search_match,
    .user_highlight = entry->reader.highlight[0],
    .note_marker = octavo_theme_rgb(color[UI0ColorRole_Accent]),
  };
}

U32
octavo_theme_highlight_color(OctavoTheme theme, U32 color_index)
{
  const OctavoThemeCatalogEntry *entry =
    octavo_theme_catalog_entry_or_dark(theme);
  if (color_index >= OctavoReaderHighlightColor_Count) color_index = 0;
  return entry->reader.highlight[color_index];
}

B32
octavo_theme_catalog_contract(void)
{
  static const char *expected_codes[] = {
    "dark", "light", "coral-dark", "coral-light", "blue-dark", "blue-light",
  };
  static const char *expected_labels[] = {
    "Dark", "Light", "Coral Dark", "Coral Light", "Blue Dark", "Blue Light",
  };
  static const UI0AppearanceMode expected_appearances[] = {
    UI0AppearanceMode_Dark,
    UI0AppearanceMode_Light,
    UI0AppearanceMode_Dark,
    UI0AppearanceMode_Light,
    UI0AppearanceMode_Dark,
    UI0AppearanceMode_Light,
  };
  static const UI0ThemeProfileKind expected_profiles[] = {
    UI0ThemeProfile_Dark,
    UI0ThemeProfile_Light,
    UI0ThemeProfile_CoralDark,
    UI0ThemeProfile_CoralLight,
    UI0ThemeProfile_BlueDark,
    UI0ThemeProfile_BlueLight,
  };
  static const OctavoReaderContentTheme expected_content[] = {
    {
      0x00181716U, 0x00F2F0EAU, 0x00C9C4BAU, 0x008D877BU,
      0x00F26A1BU, 0x004D3424U, 0x004A4947U, 0x008F430FU,
      0x004D4A16U, 0x00F26A1BU,
    },
    {
      0x00FFFDF9U, 0x001B1A18U, 0x0047423BU, 0x007A7368U,
      0x00D95618U, 0x00FFE7D4U, 0x00D8D7D4U, 0x00F6B36FU,
      0x00FFF2A6U, 0x00D95618U,
    },
    {
      0x00464644U, 0x00F5EBDDU, 0x00DED4C8U, 0x00C2B6ACU,
      0x00E85D56U, 0x0063423EU, 0x0062605EU, 0x009A3034U,
      0x00524A25U, 0x00E85D56U,
    },
    {
      0x00F3E8DBU, 0x00333230U, 0x0053514FU, 0x006F6D68U,
      0x00E85D56U, 0x00F3C2B9U, 0x00D4D0CCU, 0x00EE9B94U,
      0x00F4DFA3U, 0x00E85D56U,
    },
    {
      0x000D1824U, 0x00EAF0F7U, 0x00B8C7D8U, 0x007E8FA3U,
      0x007C93FFU, 0x00345F91U, 0x003D454EU, 0x004F64BFU,
      0x004D4A16U, 0x007C93FFU,
    },
    {
      0x00FFFDF9U, 0x00121A22U, 0x00334252U, 0x006E7680U,
      0x00365CE7U, 0x00E6EEFFU, 0x00D6DADFU, 0x008BAEFFU,
      0x00FFF2A6U, 0x00365CE7U,
    },
  };
  static const U32 expected_highlights[][OctavoReaderHighlightColor_Count] = {
    {0x004D4A16U, 0x0047355CU, 0x002A4662U, 0x00523F1CU},
    {0x00FFF2A6U, 0x00FFD4ECU, 0x00CDE7FFU, 0x00FFDCA8U},
    {0x00524A25U, 0x0047355CU, 0x002A4662U, 0x00523F1CU},
    {0x00F4DFA3U, 0x00FFD4ECU, 0x00CDE7FFU, 0x00FFDCA8U},
    {0x004D4A16U, 0x0047355CU, 0x002A4662U, 0x00523F1CU},
    {0x00FFF2A6U, 0x00FFD4ECU, 0x00CDE7FFU, 0x00FFDCA8U},
  };

  if (ARRAY_COUNT(expected_codes) != OctavoTheme_Count ||
      ARRAY_COUNT(expected_labels) != OctavoTheme_Count ||
      ARRAY_COUNT(expected_appearances) != OctavoTheme_Count ||
      ARRAY_COUNT(expected_profiles) != OctavoTheme_Count ||
      ARRAY_COUNT(expected_content) != OctavoTheme_Count ||
      ARRAY_COUNT(expected_highlights) != OctavoTheme_Count)
    return 0;

  for (U32 index = 0; index < OctavoTheme_Count; index += 1)
  {
    OctavoTheme id = (OctavoTheme)index;
    const OctavoThemeCatalogEntry *entry = octavo_theme_catalog_entry(id);
    UI0ThemeProfile profile = octavo_theme_profile(id);
    OctavoReaderContentTheme content = octavo_reader_content_theme(id);
    OctavoTheme parsed = OctavoTheme_Count;
    if (!entry || entry->id != id || strcmp(entry->code, expected_codes[index]) ||
        strcmp(entry->label, expected_labels[index]) ||
        entry->appearance != expected_appearances[index] ||
        entry->ui0_profile_kind != expected_profiles[index] ||
        profile.kind != expected_profiles[index] ||
        profile.appearance != expected_appearances[index] ||
        strcmp(profile.code, expected_codes[index]) ||
        strcmp(profile.label, expected_labels[index]) ||
        memcmp(&content, expected_content + index, sizeof(content)) != 0 ||
        content.user_highlight != entry->reader.highlight[0] ||
        !octavo_theme_from_code(expected_codes[index], &parsed) || parsed != id)
      return 0;

    for (U32 color_index = 0;
         color_index < OctavoReaderHighlightColor_Count;
         color_index += 1)
    {
      if (entry->reader.highlight[color_index] !=
            expected_highlights[index][color_index] ||
          octavo_theme_highlight_color(id, color_index) !=
            expected_highlights[index][color_index])
        return 0;
    }
    if (octavo_theme_highlight_color(id, OctavoReaderHighlightColor_Count) !=
          expected_highlights[index][0])
      return 0;

    for (U32 other = index + 1; other < OctavoTheme_Count; other += 1)
    {
      if (strcmp(entry->code, expected_codes[other]) == 0) return 0;
    }
  }

  OctavoTheme unchanged = OctavoTheme_BlueLight;
  UI0ThemeProfile fallback_profile =
    octavo_theme_profile((OctavoTheme)OctavoTheme_Count);
  OctavoReaderContentTheme fallback_content =
    octavo_reader_content_theme((OctavoTheme)OctavoTheme_Count);
  return !octavo_theme_catalog_entry((OctavoTheme)OctavoTheme_Count) &&
    !octavo_theme_from_code("unknown", &unchanged) &&
    unchanged == OctavoTheme_BlueLight &&
    fallback_profile.kind == UI0ThemeProfile_Dark &&
    strcmp(fallback_profile.code, "dark") == 0 &&
    memcmp(&fallback_content, expected_content, sizeof(fallback_content)) == 0;
}
