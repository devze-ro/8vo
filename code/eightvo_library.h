#ifndef EIGHTVO_LIBRARY_H
#define EIGHTVO_LIBRARY_H

#include "base/base_core.h"
#include "os/os_file.h"

enum
{
  EightvoLibraryEntryCap = 512,
  EightvoLibraryImportPathCap = 64,
  EightvoLibraryPathCap = 1024,
  EightvoLibraryMetadataCap = 256,
  EightvoLibraryDigestCap = 32,
  EightvoLibraryCatalogFileCap = 2 * 1024 * 1024,
};

typedef enum EightvoLibraryDigestAlgorithm
{
  EightvoLibraryDigest_None,
  EightvoLibraryDigest_SHA256,
} EightvoLibraryDigestAlgorithm;

typedef U32 EightvoLibraryMetadataFlags;
enum
{
  EightvoLibraryMetadata_Title = 1u << 0,
  EightvoLibraryMetadata_Author = 1u << 1,
  EightvoLibraryMetadata_Cover = 1u << 2,
  EightvoLibraryMetadata_Inspected = 1u << 3,
};

typedef struct EightvoLibraryEntry
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
  EightvoLibraryMetadataFlags metadata_flags;
  EightvoLibraryDigestAlgorithm digest_algorithm;
  U8 digest[EightvoLibraryDigestCap];
  B32 runtime_missing;
  char source_path[EightvoLibraryPathCap];
  char progress_spine_href[EightvoLibraryPathCap];
  char title[EightvoLibraryMetadataCap];
  char author[EightvoLibraryMetadataCap];
} EightvoLibraryEntry;

typedef struct EightvoLibraryCatalog
{
  U32 entry_count;
  U64 next_entry_id;
  U64 revision;
  EightvoLibraryEntry entries[EightvoLibraryEntryCap];
} EightvoLibraryCatalog;

void eightvo_library_catalog_init(EightvoLibraryCatalog *catalog);
B32 eightvo_library_catalog_load(EightvoLibraryCatalog *catalog,
                                  const char *catalog_path);
B32 eightvo_library_catalog_save(const EightvoLibraryCatalog *catalog,
                                  const char *catalog_path);
void eightvo_library_catalog_refresh_missing(EightvoLibraryCatalog *catalog);
void eightvo_library_catalog_sort(EightvoLibraryCatalog *catalog);
EightvoLibraryEntry *eightvo_library_catalog_find_path(
  EightvoLibraryCatalog *catalog,
  const char *normalized_path);
EightvoLibraryEntry *eightvo_library_catalog_find_id(
  EightvoLibraryCatalog *catalog,
  U64 entry_id);
S32 eightvo_library_catalog_index_for_id(const EightvoLibraryCatalog *catalog,
                                          U64 entry_id);
EightvoLibraryEntry *eightvo_library_catalog_upsert(
  EightvoLibraryCatalog *catalog,
  const char *normalized_path,
  OS_FileProperties properties,
  U64 opened_time,
  U64 locate_entry_id,
  B32 *out_created);
B32 eightvo_library_catalog_remove(EightvoLibraryCatalog *catalog,
                                    U64 entry_id);
B32 eightvo_library_normalize_path(const char *path,
                                    char *out_path,
                                    U64 out_path_cap);
void eightvo_library_fallback_title(const char *path,
                                     char *out_title,
                                     U64 out_title_cap);
U64 eightvo_library_now(void);
B32 eightvo_library_format_last_opened(U64 timestamp,
                                        char *out_text,
                                        U64 out_text_cap);

#endif /* EIGHTVO_LIBRARY_H */
