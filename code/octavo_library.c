#include "octavo_library.h"

#include "base/base_format.h"

#include <stddef.h>
#include <string.h>
#include <windows.h>

#define OCTAVO_LIBRARY_CATALOG_MAGIC 0x4C304C4942524152ull
#define OCTAVO_LIBRARY_CATALOG_VERSION 1u

typedef struct OctavoLibraryCatalogFile
{
  U64 magic;
  U32 version;
  U32 entry_count;
  U64 next_entry_id;
  OctavoLibraryEntry entries[OctavoLibraryEntryCap];
} OctavoLibraryCatalogFile;

_Static_assert(sizeof(OctavoLibraryCatalogFile) <=
                 OctavoLibraryCatalogFileCap,
               "octavo library catalog exceeds its file cap");

FUNCTION void
octavo_library_copy_cstr(char *dst, U64 cap, const char *src)
{
  if (!dst || cap == 0) return;
  U64 at = 0;
  if (src)
  {
    while (src[at] && at + 1 < cap)
    {
      dst[at] = src[at];
      at += 1;
    }
  }
  dst[at] = 0;
}

void
octavo_library_catalog_init(OctavoLibraryCatalog *catalog)
{
  if (!catalog) return;
  MemoryZeroStruct(catalog);
  catalog->next_entry_id = 1;
}

FUNCTION B32
octavo_library_entry_strings_valid(OctavoLibraryEntry *entry)
{
  if (!entry) return 0;
  entry->source_path[ARRAY_COUNT(entry->source_path) - 1] = 0;
  entry->progress_spine_href[ARRAY_COUNT(entry->progress_spine_href) - 1] = 0;
  entry->title[ARRAY_COUNT(entry->title) - 1] = 0;
  entry->author[ARRAY_COUNT(entry->author) - 1] = 0;
  return entry->entry_id != 0 && entry->source_path[0] != 0;
}

B32
octavo_library_catalog_load(OctavoLibraryCatalog *catalog,
                              const char *catalog_path)
{
  if (!catalog || !catalog_path || !catalog_path[0]) return 0;
  OctavoLibraryCatalogFile file = {0};
  U64 size = 0;
  if (!os_read_entire_file(catalog_path, &file, sizeof(file), &size) ||
      size < offsetof(OctavoLibraryCatalogFile, entries) ||
      file.magic != OCTAVO_LIBRARY_CATALOG_MAGIC ||
      file.version != OCTAVO_LIBRARY_CATALOG_VERSION ||
      file.entry_count > OctavoLibraryEntryCap)
  {
    return 0;
  }
  U64 expected_size = offsetof(OctavoLibraryCatalogFile, entries) +
    sizeof(file.entries[0]) * (U64)file.entry_count;
  if (size != expected_size) return 0;

  OctavoLibraryCatalog loaded = {0};
  loaded.entry_count = file.entry_count;
  loaded.next_entry_id = MAX(file.next_entry_id, 1ull);
  U64 maximum_id = 0;
  for (U32 index = 0; index < file.entry_count; index += 1)
  {
    OctavoLibraryEntry entry = file.entries[index];
    entry.runtime_missing = 0;
    if (!octavo_library_entry_strings_valid(&entry)) return 0;
    for (U32 previous = 0; previous < index; previous += 1)
      if (loaded.entries[previous].entry_id == entry.entry_id) return 0;
    loaded.entries[index] = entry;
    maximum_id = MAX(maximum_id, entry.entry_id);
  }
  loaded.next_entry_id = MAX(loaded.next_entry_id, maximum_id + 1);
  loaded.revision = 1;
  *catalog = loaded;
  octavo_library_catalog_refresh_missing(catalog);
  octavo_library_catalog_sort(catalog);
  return 1;
}

B32
octavo_library_catalog_save(const OctavoLibraryCatalog *catalog,
                              const char *catalog_path)
{
  if (!catalog || !catalog_path || !catalog_path[0] ||
      catalog->entry_count > OctavoLibraryEntryCap)
    return 0;
  OctavoLibraryCatalogFile file = {0};
  file.magic = OCTAVO_LIBRARY_CATALOG_MAGIC;
  file.version = OCTAVO_LIBRARY_CATALOG_VERSION;
  file.entry_count = catalog->entry_count;
  file.next_entry_id = MAX(catalog->next_entry_id, 1ull);
  for (U32 index = 0; index < catalog->entry_count; index += 1)
  {
    file.entries[index] = catalog->entries[index];
    file.entries[index].runtime_missing = 0;
  }
  U64 size = offsetof(OctavoLibraryCatalogFile, entries) +
    sizeof(file.entries[0]) * (U64)file.entry_count;
  return size <= OctavoLibraryCatalogFileCap &&
    os_write_entire_file_atomic(catalog_path, &file, size);
}

void
octavo_library_catalog_refresh_missing(OctavoLibraryCatalog *catalog)
{
  if (!catalog) return;
  for (U32 index = 0; index < catalog->entry_count; index += 1)
  {
    OS_FileProperties properties =
      os_file_properties(catalog->entries[index].source_path);
    catalog->entries[index].runtime_missing =
      !properties.exists || properties.is_directory;
  }
}

FUNCTION S32
octavo_library_entry_compare(const OctavoLibraryEntry *left,
                               const OctavoLibraryEntry *right)
{
  if (left->last_opened_time != right->last_opened_time)
    return left->last_opened_time > right->last_opened_time ? -1 : 1;
  if (left->added_time != right->added_time)
    return left->added_time > right->added_time ? -1 : 1;
  S32 title_order = _stricmp(left->title, right->title);
  if (title_order != 0) return title_order;
  if (left->entry_id == right->entry_id) return 0;
  return left->entry_id < right->entry_id ? -1 : 1;
}

void
octavo_library_catalog_sort(OctavoLibraryCatalog *catalog)
{
  if (!catalog) return;
  for (U32 index = 1; index < catalog->entry_count; index += 1)
  {
    OctavoLibraryEntry entry = catalog->entries[index];
    U32 insert = index;
    while (insert > 0 &&
           octavo_library_entry_compare(&entry,
                                           &catalog->entries[insert - 1]) < 0)
    {
      catalog->entries[insert] = catalog->entries[insert - 1];
      insert -= 1;
    }
    catalog->entries[insert] = entry;
  }
}

OctavoLibraryEntry *
octavo_library_catalog_find_path(OctavoLibraryCatalog *catalog,
                                   const char *normalized_path)
{
  if (!catalog || !normalized_path || !normalized_path[0]) return 0;
  for (U32 index = 0; index < catalog->entry_count; index += 1)
    if (_stricmp(catalog->entries[index].source_path, normalized_path) == 0)
      return catalog->entries + index;
  return 0;
}

OctavoLibraryEntry *
octavo_library_catalog_find_id(OctavoLibraryCatalog *catalog, U64 entry_id)
{
  if (!catalog || entry_id == 0) return 0;
  S32 index = octavo_library_catalog_index_for_id(catalog, entry_id);
  return index >= 0 ? catalog->entries + index : 0;
}

S32
octavo_library_catalog_index_for_id(const OctavoLibraryCatalog *catalog,
                                      U64 entry_id)
{
  if (!catalog || entry_id == 0) return -1;
  for (U32 index = 0; index < catalog->entry_count; index += 1)
    if (catalog->entries[index].entry_id == entry_id) return (S32)index;
  return -1;
}

OctavoLibraryEntry *
octavo_library_catalog_upsert(OctavoLibraryCatalog *catalog,
                                const char *normalized_path,
                                OS_FileProperties properties,
                                U64 opened_time,
                                U64 locate_entry_id,
                                B32 *out_created)
{
  if (out_created) *out_created = 0;
  if (!catalog || !normalized_path || !normalized_path[0] ||
      !properties.exists || properties.is_directory)
    return 0;
  OctavoLibraryEntry *entry = locate_entry_id ?
    octavo_library_catalog_find_id(catalog, locate_entry_id) :
    octavo_library_catalog_find_path(catalog, normalized_path);
  if (!entry)
  {
    if (catalog->entry_count >= OctavoLibraryEntryCap) return 0;
    entry = catalog->entries + catalog->entry_count;
    MemoryZeroStruct(entry);
    entry->entry_id = MAX(catalog->next_entry_id, 1ull);
    catalog->next_entry_id = entry->entry_id + 1;
    entry->added_time = opened_time;
    entry->cover_resource_index = UINT32_MAX;
    catalog->entry_count += 1;
    if (out_created) *out_created = 1;
  }
  octavo_library_copy_cstr(entry->source_path,
                             ARRAY_COUNT(entry->source_path),
                             normalized_path);
  entry->file_size = properties.size;
  entry->file_modified_time = properties.modified_time;
  entry->last_opened_time = opened_time;
  entry->runtime_missing = 0;
  catalog->revision += 1;
  return entry;
}

B32
octavo_library_catalog_remove(OctavoLibraryCatalog *catalog, U64 entry_id)
{
  if (!catalog) return 0;
  S32 found = octavo_library_catalog_index_for_id(catalog, entry_id);
  if (found < 0) return 0;
  U32 index = (U32)found;
  for (U32 move = index + 1; move < catalog->entry_count; move += 1)
    catalog->entries[move - 1] = catalog->entries[move];
  catalog->entry_count -= 1;
  MemoryZeroStruct(catalog->entries + catalog->entry_count);
  catalog->revision += 1;
  return 1;
}

B32
octavo_library_normalize_path(const char *path,
                                char *out_path,
                                U64 out_path_cap)
{
  if (out_path && out_path_cap > 0) out_path[0] = 0;
  if (!path || !path[0] || !out_path || out_path_cap == 0 ||
      out_path_cap > INT32_MAX)
    return 0;
  wchar_t input[OctavoLibraryPathCap] = {0};
  wchar_t absolute[OctavoLibraryPathCap] = {0};
  if (MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, path, -1,
                          input, ARRAY_COUNT(input)) <= 0)
    return 0;
  DWORD size = GetFullPathNameW(input, ARRAY_COUNT(absolute), absolute, 0);
  if (size == 0 || size >= ARRAY_COUNT(absolute)) return 0;
  for (DWORD index = 0; index < size; index += 1)
    if (absolute[index] == L'/') absolute[index] = L'\\';
  return WideCharToMultiByte(CP_UTF8, 0, absolute, -1,
                             out_path, (S32)out_path_cap, 0, 0) > 0;
}

void
octavo_library_fallback_title(const char *path,
                                char *out_title,
                                U64 out_title_cap)
{
  if (!out_title || out_title_cap == 0) return;
  out_title[0] = 0;
  if (!path) return;
  const char *base = path;
  for (const char *at = path; *at; at += 1)
    if (*at == '\\' || *at == '/') base = at + 1;
  octavo_library_copy_cstr(out_title, out_title_cap, base);
  char *dot = strrchr(out_title, '.');
  if (dot && _stricmp(dot, ".epub") == 0) *dot = 0;
}

U64
octavo_library_now(void)
{
  FILETIME time = {0};
  GetSystemTimeAsFileTime(&time);
  ULARGE_INTEGER value = {0};
  value.LowPart = time.dwLowDateTime;
  value.HighPart = time.dwHighDateTime;
  return value.QuadPart;
}

B32
octavo_library_format_last_opened(U64 timestamp,
                                    char *out_text,
                                    U64 out_text_cap)
{
  if (!out_text || out_text_cap == 0 || out_text_cap > INT32_MAX) return 0;
  out_text[0] = 0;
  if (timestamp == 0)
  {
    octavo_library_copy_cstr(out_text, out_text_cap, "Not opened yet");
    return 1;
  }
  ULARGE_INTEGER value = {0};
  value.QuadPart = timestamp;
  FILETIME file_time = {value.LowPart, value.HighPart};
  SYSTEMTIME utc = {0};
  SYSTEMTIME local = {0};
  char date[64] = {0};
  if (!FileTimeToSystemTime(&file_time, &utc) ||
      !SystemTimeToTzSpecificLocalTime(0, &utc, &local) ||
      GetDateFormatA(LOCALE_USER_DEFAULT, DATE_SHORTDATE, &local, 0,
                     date, ARRAY_COUNT(date)) == 0)
    return 0;
  return cstr_format(out_text, out_text_cap, "Opened %s", date) > 0;
}
