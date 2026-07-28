# Third-party notices

The following bundled or source-consumed components are not covered by 8vo's
Mozilla Public License 2.0. They remain available under their original
licenses.

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

Binary distributions of 8vo must include these license texts along with the
MPL-2.0 license and corresponding source-code availability information.
