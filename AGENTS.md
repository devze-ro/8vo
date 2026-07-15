# lectern0 repository instructions

- lectern0 is a concrete EPUB-first application host, not a generic document framework.
- Consume reader0 only through `reader0.h`; compile `reader0.c` exactly once.
- UI0 may be used by lectern0 but must never enter reader0.
- Keep reader, frame storage, layout inputs, and arenas caller-owned and explicit.
- Do not add provider tables, vtables, event buses, DI frameworks, hidden threads, or process-global mutable document state.
- Keep Win32, file-picker, persistence, rendering, and decoded-image policy in lectern0.
- Use the exact dependency metadata under `vendor/` and run the guards before builds.
- Build C11 with `/W4 /WX`; preserve bounded storage and visible failure results.
- Do not add PDF, shared reader chrome, annotations, or accessibility redesign in Slice 1.
