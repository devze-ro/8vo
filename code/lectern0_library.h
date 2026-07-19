#ifndef LECTERN0_LIBRARY_H
#define LECTERN0_LIBRARY_H

#include "base/base_core.h"
#include "os/os_file.h"

enum
{
  Lectern0LibraryEntryCap = 512,
  Lectern0LibraryImportPathCap = 64,
  Lectern0LibraryPathCap = 1024,
  Lectern0LibraryMetadataCap = 256,
  Lectern0LibraryDigestCap = 32,
  Lectern0LibraryCatalogFileCap = 2 * 1024 * 1024,
};

typedef enum Lectern0LibraryDigestAlgorithm
{
  Lectern0LibraryDigest_None,
  Lectern0LibraryDigest_SHA256,
} Lectern0LibraryDigestAlgorithm;

typedef U32 Lectern0LibraryMetadataFlags;
enum
{
  Lectern0LibraryMetadata_Title = 1u << 0,
  Lectern0LibraryMetadata_Author = 1u << 1,
  Lectern0LibraryMetadata_Cover = 1u << 2,
};

typedef struct Lectern0LibraryEntry
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
  Lectern0LibraryMetadataFlags metadata_flags;
  Lectern0LibraryDigestAlgorithm digest_algorithm;
  U8 digest[Lectern0LibraryDigestCap];
  B32 runtime_missing;
  char source_path[Lectern0LibraryPathCap];
  char progress_spine_href[Lectern0LibraryPathCap];
  char title[Lectern0LibraryMetadataCap];
  char author[Lectern0LibraryMetadataCap];
} Lectern0LibraryEntry;

typedef struct Lectern0LibraryCatalog
{
  U32 entry_count;
  U64 next_entry_id;
  U64 revision;
  Lectern0LibraryEntry entries[Lectern0LibraryEntryCap];
} Lectern0LibraryCatalog;

void lectern0_library_catalog_init(Lectern0LibraryCatalog *catalog);
B32 lectern0_library_catalog_load(Lectern0LibraryCatalog *catalog,
                                  const char *catalog_path);
B32 lectern0_library_catalog_save(const Lectern0LibraryCatalog *catalog,
                                  const char *catalog_path);
void lectern0_library_catalog_refresh_missing(Lectern0LibraryCatalog *catalog);
void lectern0_library_catalog_sort(Lectern0LibraryCatalog *catalog);
Lectern0LibraryEntry *lectern0_library_catalog_find_path(
  Lectern0LibraryCatalog *catalog,
  const char *normalized_path);
Lectern0LibraryEntry *lectern0_library_catalog_find_id(
  Lectern0LibraryCatalog *catalog,
  U64 entry_id);
S32 lectern0_library_catalog_index_for_id(const Lectern0LibraryCatalog *catalog,
                                          U64 entry_id);
Lectern0LibraryEntry *lectern0_library_catalog_upsert(
  Lectern0LibraryCatalog *catalog,
  const char *normalized_path,
  OS_FileProperties properties,
  U64 opened_time,
  U64 locate_entry_id,
  B32 *out_created);
B32 lectern0_library_catalog_remove(Lectern0LibraryCatalog *catalog,
                                    U64 entry_id);
B32 lectern0_library_normalize_path(const char *path,
                                    char *out_path,
                                    U64 out_path_cap);
void lectern0_library_fallback_title(const char *path,
                                     char *out_title,
                                     U64 out_title_cap);
U64 lectern0_library_now(void);
B32 lectern0_library_format_last_opened(U64 timestamp,
                                        char *out_text,
                                        U64 out_text_cap);

#endif /* LECTERN0_LIBRARY_H */
