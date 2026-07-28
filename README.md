# 8vo

8vo is an experimental desktop reader for Windows. It currently supports
EPUB; other document and comic-book formats may be explored later.

The name `8vo` is the bibliographic abbreviation for *octavo* and is
pronounced *octavo*.

## Why this project exists

I am building 8vo to explore how far coding agents such as Codex can go in
creating a large, working software project. I set the direction, review the
results, and evaluate the software, while agents do the implementation work.

The experiment asks a simple question: **Can AI coding agents build and
maintain substantial software that actually works?**

8vo is early-stage research software. It is under active development, has no
public binary release yet, and is not ready for general use.

## Build and test

Building requires 64-bit Windows, Git, PowerShell 5.1 or newer, and Visual
Studio 2022 or Build Tools 2022 with the C++ workload and a Windows SDK.

8vo compiles four first-party repositories from exact revisions. See
[DEPENDENCIES.md](DEPENDENCIES.md) for the required source checkouts and
environment variables. Once they are configured:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run_public_smoke.ps1
```

Architecture details are in [docs/architecture.md](docs/architecture.md).

## License

First-party source code and documentation are licensed under the
[Mozilla Public License 2.0](LICENSE). Third-party components retain their
original licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
