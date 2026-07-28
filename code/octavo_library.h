#ifndef OCTAVO_LIBRARY_H
#define OCTAVO_LIBRARY_H

#include "base/base_core.h"
#include "os/os_file.h"

enum
{
  OctavoLibraryEntryCap = 512,
  OctavoLibraryImportPathCap = 64,
  OctavoLibraryPathCap = 1024,
  OctavoLibraryMetadataCap = 256,
  OctavoLibraryDigestCap = 32,
  OctavoLibraryCatalogFileCap = 2 * 1024 * 1024,
};

typedef enum OctavoLibraryDigestAlgorithm
{
  OctavoLibraryDigest_None,
  OctavoLibraryDigest_SHA256,
} OctavoLibraryDigestAlgorithm;

typedef U32 OctavoLibraryMetadataFlags;
enum
{
  OctavoLibraryMetadata_Title = 1u << 0,
  OctavoLibraryMetadata_Author = 1u << 1,
  OctavoLibraryMetadata_Cover = 1u << 2,
  OctavoLibraryMetadata_Inspected = 1u << 3,
};

typedef struct OctavoLibraryEntry
{
  U64 entry_id;
  U64 added_time;
  U64 last_opened_time;
  U64 file_size;
  U64 file_modified_time;
  U64 progress_byte_offset;
  U32 progress_spine_index;
  U32 progress_percent;
  U32 cover_resource_index;
  OctavoLibraryMetadataFlags metadata_flags;
  OctavoLibraryDigestAlgorithm digest_algorithm;
  U8 digest[OctavoLibraryDigestCap];
  B32 runtime_missing;
  char source_path[OctavoLibraryPathCap];
  char progress_spine_href[OctavoLibraryPathCap];
  char title[OctavoLibraryMetadataCap];
  char author[OctavoLibraryMetadataCap];
} OctavoLibraryEntry;

typedef struct OctavoLibraryCatalog
{
  U32 entry_count;
  U64 next_entry_id;
  U64 revision;
  OctavoLibraryEntry entries[OctavoLibraryEntryCap];
} OctavoLibraryCatalog;

void octavo_library_catalog_init(OctavoLibraryCatalog *catalog);
B32 octavo_library_catalog_load(OctavoLibraryCatalog *catalog,
                                  const char *catalog_path);
B32 octavo_library_catalog_save(const OctavoLibraryCatalog *catalog,
                                  const char *catalog_path);
void octavo_library_catalog_refresh_missing(OctavoLibraryCatalog *catalog);
void octavo_library_catalog_sort(OctavoLibraryCatalog *catalog);
OctavoLibraryEntry *octavo_library_catalog_find_path(
  OctavoLibraryCatalog *catalog,
  const char *normalized_path);
OctavoLibraryEntry *octavo_library_catalog_find_id(
  OctavoLibraryCatalog *catalog,
  U64 entry_id);
S32 octavo_library_catalog_index_for_id(const OctavoLibraryCatalog *catalog,
                                          U64 entry_id);
OctavoLibraryEntry *octavo_library_catalog_upsert(
  OctavoLibraryCatalog *catalog,
  const char *normalized_path,
  OS_FileProperties properties,
  U64 opened_time,
  U64 locate_entry_id,
  B32 *out_created);
B32 octavo_library_catalog_remove(OctavoLibraryCatalog *catalog,
                                    U64 entry_id);
B32 octavo_library_normalize_path(const char *path,
                                    char *out_path,
                                    U64 out_path_cap);
void octavo_library_fallback_title(const char *path,
                                     char *out_title,
                                     U64 out_title_cap);
U64 octavo_library_now(void);
B32 octavo_library_format_last_opened(U64 timestamp,
                                        char *out_text,
                                        U64 out_text_cap);

#endif /* OCTAVO_LIBRARY_H */
