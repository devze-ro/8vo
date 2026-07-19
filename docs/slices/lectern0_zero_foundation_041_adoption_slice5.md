# Lectern0 zero_foundation 0.4.1 adoption slice 5

Date: 2026-07-19

## Scope and ownership

This slice advances Lectern0's exact zero_foundation dependency from
`0.4.0-dev` at
`eee57edc1b0c7af5bef7afca26f3c27a32fb6e7c` to `0.4.1-dev` at
`3eab21c06c4aa0b4915f9e7fcb3830ba1688451f`.

The upstream advance is one commit, `Add explicit sprite resampling`. It adds
explicit nearest, linear, and area sprite sampling, sampled draw helpers, and
the caller-destination `render_resample_bgra8` mechanism. The legacy
`draw_push_sprite` and `draw_push_sprite_clipped` helpers retain nearest
sampling, so existing consumer pixels remain compatible. Presentation Engine
API remains 1.

Lectern0 does not opt into a new sampling policy in this adoption. It continues
to own decoded-image cache and presentation policy, while zero_foundation owns
the product-neutral draw/render mechanisms. No host code, callback table,
provider registry, vtable, event bus, dependency injection, allocation policy,
thread, cache, or process-global mutable state is introduced.

## Baseline and exact dependencies

The dedicated worktree and branch are:

- branch: `codex/lectern-zero-foundation-041-adoption-slice5`;
- base: `92af52220007a76a5db46e25abdce7c5258b612b`;
- implementation: `4f9671c74d50e7b94bf4d9175548715ba0409319`;
- Reader0: `3d7a81a9bd5e2a90d9221f434cc9485f46a633bd`;
- UI0: `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`;
- Readerview0: `26d836390fce2de64198430fa82d6f660fc7fc07`;
- zero_foundation: `3eab21c06c4aa0b4915f9e7fcb3830ba1688451f`;
- zero_foundation version: `0.4.1-dev`; and
- Presentation Engine API: 1.

The fresh-fetch gate found Re10 at
`4d197e23cdaea8b9979c064593fbf8210ffd2d1d`, equal to `origin/main`, clean,
and 0/0 divergent. Reader0, UI0, Readerview0, and zero_foundation were also
clean and 0/0 divergent from their upstreams. Lectern0 remained local-only,
clean on `main` at the base above, with no remote configured. The protected
extraction worktree remained clean on
`codex/reader-core-extraction-slice3` at
`420068fc1509fcf9c9b9eae7f4dcacfe2e046f54`.

## Build failure and correction

The reported canonical command failed because
`scripts/check_dependencies.ps1` correctly compared Lectern0's old exact pin
with the newer canonical sibling zero_foundation checkout. The correction
updates only `vendor/zero_foundation_dependency/COMMIT` and `VERSION`; the
exact-commit, clean-tree, version, and Presentation Engine API guards remain
unchanged.

Earlier Slice 4 validation deliberately supplied an explicit checkout of the
old exact pin from its linked worktree. That proved Slice 4 against its recorded
dependency but did not exercise the user's later canonical no-override command.
This adoption adds that missing verification. With dependency environment
variables cleared and temporary sibling junctions reproducing the canonical
repository layout, the literal command below passed the exact guards,
architecture audit, and strict MSVC C11 `/W4 /WX` compile:

```powershell
.\build\win32_build.bat no_run
```

The temporary junctions were verified against their intended targets and
removed after the test; they were not repository changes.

## Upstream mechanism verification

The canonical zero_foundation commit passed:

- `render_resample_smoke`, hash `a15849667276de55`; and
- `render_perf_smoke`, hash `0c65f35ee18cba20`, measured at 15293 microseconds
  in this run.

These tests cover the new explicit sampling/resampling contract. Lectern0's
consumer image smoke then passed twice with stable application-render hashes:

- cover render hash: `c2cca908e35dd8d1`;
- inline render hash: `17989905b776b21d`;
- inspected cover BMP SHA-256:
  `4B5F655F51C82301B21E1C33DDE2056D2EE40247F95FA939A04088B052BF445A`;
  and
- inspected inline BMP SHA-256:
  `C24D5D4F1B1C4ACD0E32F7DC7B746F0E8E00EBD7F07D11E998C5092269CFD07E`.

Direct inspection confirmed that both decoded resources retained their
expected pixels, bounds, and surrounding page composition.

## Exact-book regression evidence

All real-book work used:

- path: `C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub`;
- size: 955125 bytes; and
- SHA-256:
  `D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9`.

The rebuilt Lectern0 executable passed:

- complete Reader View smoke, repeat 2, stable hash
  `bbc068e6c290fee1`;
- empty-document startup interaction smoke;
- post-action arrow workflow, repeat 2, summary SHA-256
  `AB3E44348712DF648EBF65B2BACB16B27E1DECF06AD3F13EA55E950F502484B7`;
- six-theme active Find contrast, summary SHA-256
  `D7FF4BDD73833A42C25B642F7591677F7A66D58999664AC24094E5C29C0B4901`;
- exact-book Find snippet context, summary SHA-256
  `8ACD4E7FC9F38CF95D66A8E52D8146F429AFC3755D04F0BBC95552B3983B3920`;
- selection geometry/menu recovery, repeat 2, summary SHA-256
  `037D0E427DF9768BB8DC5EA8BDC4CE825DA6DCFD005C84A51A431358C24E4CC6`;
  and
- publisher typography and all three spacing choices, summary SHA-256
  `916CE7C79D0734056E3AB734EB43F6839812AFF0FBD815B5F8299EB6FA9F433D`.

Rendered selection/menu and typography evidence was inspected directly. The
selection covered the shaped multi-row range and the compact action menu was
bounded and clamped. Publisher italics, justified prose, and the selected row
spacing remained intact.

## Cross-host acceptance

The final 15-case Re10/Lectern0 matrix used a fresh output root and rebuilt both
hosts in-run at the exact commits above:

- manifest:
  `C:\Temp\lectern0_zero041_final_clean_20260719_1\manifest.json`;
- manifest SHA-256:
  `131399049C59E9584D16A0E5611311F88AE17378F8153BE2BEA827CCE3255B91`;
- deterministic: 15/15;
- decoded-pixel exact: 15/15;
- exact records: 9/15, with the six accepted host-record differences remaining
  pixel-exact;
- clean-tree enforcement: pass;
- in-run builds: pass;
- exact dependency enforcement: pass; and
- acceptance eligible: true.

The frozen 28-case Re10 reference matrix was not rerun because this slice does
not change Re10 or any of its dependencies; Re10 already consumed this exact
zero_foundation head before the Lectern0 metadata adoption. The affected
cross-host matrix and Lectern host, image, Reader View, and real-book workflows
were rerun.

## Deferred real-book image-fit policy

This adoption intentionally does not fix the separately reproduced small
`gotm_new.epub` cover and map rendering. That defect is in Lectern0's
host-owned image-box fit policy, not in decoding or the dependency guard. The
next bounded slice should distinguish image-only page media from true in-flow
publisher images, fit image-only content to the available page while
preserving aspect ratio, choose an explicit sampling mode where scaling needs
it, and compare the cover and every map page directly with Re10.

## Promotion

The user explicitly authorized promotion on 2026-07-19. Canonical Lectern0
`main` was fast-forwarded from
`92af52220007a76a5db46e25abdce7c5258b612b` through the validated
implementation and evidence commits. The canonical no-override command
`.\build\win32_build.bat no_run` then passed the exact dependency guards,
architecture audit, and strict compile against the sibling repositories. This
promotion record is documentation only and is fast-forwarded on top of the
approved tip.

Lectern0 remains local-only with no remote configured. No repository was
created and no push, merge commit, rebase, reset, amendment, cherry-pick, or
history rewrite was performed.
