# Lectern0 saved-position window restore and highlight close icon

> [!NOTE]
> This is an archived engineering record from before the public 8vo release.
> Repository names, paths, remotes, visibility, and branch status describe
> the historical slice only. For current instructions, see
> [the project README](../../../README.md).

## Consumer integration

Lectern0 pins Reader0 API 5 commit
`98a6a2ba5a4946971b9c088781cf3728aeb16b1a` and ReaderView0 API 3 commit
`f97f9d38cf857c2cff1f90357cf5d2e5cf40dc03`.

Catalog and legacy saved-position restores explicitly request Reader0's
bounded six-page publication before resolving the persisted byte. This avoids
measuring a long target chapter from byte zero during first load.

The ReaderView0 pin also carries the selected-highlight Close-icon fix. Its
portable icon foreground remains the resolved Focus color and its raster
backdrop is the selected swatch color, so Lectern0's existing icon raster
bridge paints an X rather than a solid inner square.

## Regression entry point

The Win32 executable accepts:

```text
lectern0.exe --saved-position-first-load-smoke <epub> <spine> <byte>
```

The smoke requires the restored byte to be owned by the current page, no more
than six active pages, at least one window rebuild, zero full rebuilds, at most
384 built rows, and an open time no greater than 750 ms.

## Validation

The GOTM saved-position smoke passed twice at spine 20, byte 71,567:

- open: 76.056 ms and 156.150 ms
- total: 78.432 ms and 159.597 ms
- active pages: 3
- active rows: 64
- laid-out bytes: 3,015
- window rebuilds: 1
- full rebuilds: 0

Computer Use validation used the isolated worktree executable. Opening GOTM
from the real library card produced the first visible reader page in 207 ms,
including the physical click and screenshot capture. Physical mouse selection
opened the selection popover, and an existing purple highlight displayed a
visible X in the selected purple swatch with no solid inner square.
