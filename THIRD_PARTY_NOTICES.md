# Third-party notices

The following bundled or source-consumed components are not covered by 8vo's
Mozilla Public License 2.0. They remain available under their original
licenses.

## MuPDF

- **Project:** [MuPDF](https://mupdf.com/)
- **Version:** 1.28.2
- **Source revision:** `fe374accd98a43174a328fa7980d7675e06d5b0d`
- **Use:** Reader0's audited PDF-only Win32 render core
- **License:** GNU Affero General Public License v3, unless separately licensed
  commercially from Artifex
- **Complete license text:** `COPYING` in the exact pinned MuPDF checkout and
  in the release source archive emitted by Reader0's
  `scripts/package_mupdf_agpl_source.ps1`

The PDF-enabled Win32 executable links MuPDF. A binary distributor must use
Reader0's verified source-package workflow and satisfy the AGPL's complete
corresponding-source and license requirements, or hold a suitable commercial
Artifex license. Merely retaining 8vo's MPL notice is not sufficient for that
binary.

## Gradle Wrapper

- **Project:** [Gradle](https://github.com/gradle/gradle)
- **Version:** 9.5.0
- **Source revision:** `v9.5.0`
- **Use:** Reproducible Android build bootstrap
- **License:** Apache License 2.0
- **Complete license text:** `META-INF/LICENSE` inside
  [`android/gradle/wrapper/gradle-wrapper.jar`](android/gradle/wrapper/gradle-wrapper.jar)

## miniz

- **Project:** [miniz](https://github.com/richgel999/miniz)
- **Version:** 3.1.0
- **Source revision:** `174573d60290f447c13a2b1b3405de2b96e27d6c`
- **Use:** EPUB ZIP reading through reader0
- **License:** MIT
- **Complete consolidated license text:**
  [`third_party_licenses/miniz-LICENSE`](third_party_licenses/miniz-LICENSE)

The consolidated notice includes the copyright lines carried by the consumed
miniz sources, including Martin Raiber's copyright from `miniz_zip.c`.

## Noto Serif

- **Project:** [Google Fonts](https://github.com/google/fonts), `ofl/notoserif`
- **Source revision:** `49af86b54514f1390e126f3139ced33824f5d72e`
- **Use:** Reader font family bundled through reader0
- **License:** SIL Open Font License 1.1
- **Complete license text:**
  [`third_party_licenses/noto-serif-OFL-1.1.txt`](third_party_licenses/noto-serif-OFL-1.1.txt)

## Lucide Icons and Feather-derived icons

- **Project:** [Lucide Icons](https://github.com/lucide-icons/lucide)
- **Source revision:** `a2ec77fc7fe8525c60eaa15a3c4a829ece9a6e8b`
- **Use:** Selected icon geometry adapted into UI0's deterministic icon path
- **Licenses:** ISC for Lucide; MIT for the Feather-derived icons identified
  by Lucide
- **Complete combined upstream license text:**
  [`third_party_licenses/lucide-LICENSE`](third_party_licenses/lucide-LICENSE)

## Unicode data

Ground0 contains generated lookup tables derived from Unicode data:

- Unicode 15.0 full case-fold mappings generated through CPython 3
  `str.casefold()`
- Unicode 15.1 grapheme properties and the default grapheme-break
  conformance corpus

Unicode data files are provided under the Unicode License v3
(`Unicode-3.0`). The complete license text is retained in
[`third_party_licenses/unicode-LICENSE`](third_party_licenses/unicode-LICENSE).
Exact source-file hashes and generation details are recorded in ground0's
`third_party/unicode/README.md`.

Binary distributions of EPUB-only 8vo builds must include these license texts
along with the MPL-2.0 license and corresponding source-code availability
information. PDF-enabled Win32 distributions additionally follow the MuPDF
AGPL/commercial boundary above.
