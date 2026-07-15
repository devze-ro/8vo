# lectern0 architecture

## Concrete boundary

Lectern0 is a Windows EPUB application. It owns one caller-allocated
`EpubReader`, one bounded `EpubReaderFrameStorage`, the current layout key and
configuration, a Win32 window/backbuffer, UI0 frame contexts, zero_foundation
draw/render state, and a versioned last-location record.

Reader0 owns the EPUB document engine, source layout, typography, pagination,
page transitions, search/selection state, and canonical frame. Lectern0 passes
its viewport-derived layout values directly to reader0 API 2. No reader state is
mirrored in application storage.

UI0 owns generic toolbar layout, signal, control, token, and draw records. A
narrow lectern0 adapter converts those records into zero_foundation draw
commands. UI0 does not render EPUB content and is not a reader0 dependency.

Zero_foundation owns arenas, file/atomic-write facilities, font providers,
draw commands, software rendering, and the Win32 DIB graphics seam. Lectern0
owns HWND/WndProc, DPI/input mapping, the native EPUB picker, and presentation.

## Slice 1 exclusions

No PDF backend, generic document interface, library database, search/TOC UI,
annotations, decoded-image cache, shared reader view/chrome, theme selector,
or accessibility redesign is included. Image rows render bounded alt-text
placeholders until a lectern-owned decoded-image cache is justified.
