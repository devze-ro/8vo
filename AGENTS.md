# 8vo repository instructions

- 8vo is a format-neutral reader application whose only current document backend is EPUB; it is not a generic document framework.
- Consume reader0 only through `reader0.h`; compile `reader0.c` exactly once.
- UI0 may be used by 8vo but must never enter reader0.
- Keep reader, frame storage, layout inputs, and arenas caller-owned and explicit.
- Do not add provider tables, vtables, event buses, DI frameworks, hidden threads, or process-global mutable document state.
- Keep Win32, file-picker, persistence, rendering, and decoded-image policy in 8vo.
- Use the exact dependency metadata under `vendor/` and run the guards before builds.
- Build C11 with `/W4 /WX`; preserve bounded storage and visible failure results.
- Do not add a speculative multi-format abstraction. Introduce any second document backend through a separately designed and tested slice with explicit host ownership.
