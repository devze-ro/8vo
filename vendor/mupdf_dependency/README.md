# MuPDF dependency

8vo's Windows PDF target consumes MuPDF only through Reader0 API 10. The exact
clean MuPDF root and submodule closure are recorded here and must exactly match
Reader0's own dependency metadata before the Reader0 PDF core is built or
linked.

- upstream version: 1.28.2
- upstream revision: `fe374accd98a43174a328fa7980d7675e06d5b0d`
- license: GNU Affero General Public License v3, or a separate Artifex
  commercial license
- feature boundary: Reader0's audited PDF-only Win32 core; JavaScript, XFA,
  OCR, barcode, HTML, and non-PDF document handlers are disabled

8vo neither compiles a second MuPDF copy nor calls MuPDF directly. The build
freshly verifies Reader0's core provenance, requires the selected `cl.exe`
identity to match that provenance, and audits the final 8vo link map.
