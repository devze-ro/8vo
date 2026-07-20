# Lectern0 Reader0 backward-adjacency adoption slice 3

Date: 2026-07-20

## Objective

Adopt Reader0's bounded correction for a missing retained backward boundary.
Lectern0 advances its exact Reader0 dependency from `3b86a1f` to `2ffae37`;
Reader0 remains API 5 and version `0.5.0-dev`.

## Ownership

Reader0 owns proof of the exact preceding canonical page and canonical
cross-spine tail, refusing the superseded `current.first_byte - 1` and
`text_size - 1` containing-page fallbacks. Lectern0 keeps
its existing key-repeat timing, navigation preparation, one-page presentation
snapshot, rendering, persistence, and window policy.

Adjacent-spine preparation preserves the exact visible page range while it
warms target pagination.

No host-local pagination algorithm, API expansion, or unrelated dependency
advance is included.

## Acceptance

- all exact dependency checks and the architecture audit pass;
- strict MSVC C11 `/W4 /WX` build and retained reader regressions pass;
- exact-book forward/backward navigation remains exactly adjacent; and
- source and protected worktrees remain unchanged and clean.
