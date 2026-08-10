param(
  [Parameter(Mandatory = $true)][string]$RepoRoot,
  [Parameter(Mandatory = $true)][string]$Reader0Dir,
  [Parameter(Mandatory = $true)][string]$MupdfDir,
  [Parameter(Mandatory = $true)][string]$CompilerPath,
  [Parameter(Mandatory = $true)][string]$LinkerPath,
  [Parameter(Mandatory = $true)][string]$ExePath,
  [Parameter(Mandatory = $true)][string]$MapPath,
  [Parameter(Mandatory = $true)][string]$OutputPath,
  [switch]$AllowDirtyDevelopment
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 3.0
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$Reader0Dir = (Resolve-Path -LiteralPath $Reader0Dir).Path
$MupdfDir = (Resolve-Path -LiteralPath $MupdfDir).Path
$CompilerPath = (Resolve-Path -LiteralPath $CompilerPath).Path
$LinkerPath = (Resolve-Path -LiteralPath $LinkerPath).Path
$ExePath = (Resolve-Path -LiteralPath $ExePath).Path
$MapPath = (Resolve-Path -LiteralPath $MapPath).Path
$OutputDirectory = Split-Path -Parent $OutputPath
if (!(Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
  throw "provenance output directory is missing: $OutputDirectory"
}

$productCommit = (& git -C $RepoRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or !$productCommit) {
  throw "could not resolve the 8vo product commit"
}
$productTree = (& git -C $RepoRoot rev-parse 'HEAD^{tree}').Trim()
if ($LASTEXITCODE -ne 0 -or !$productTree) {
  throw "could not resolve the 8vo product tree"
}
$productStatus = @(
  & git -C $RepoRoot status --porcelain=v1 --untracked-files=all
)
if ($LASTEXITCODE -ne 0) {
  throw "could not inspect the complete 8vo product source state"
}
$productStatus = @($productStatus | ForEach-Object { [string]$_ })
$productVerifiedClean = $productStatus.Count -eq 0
if (!$productVerifiedClean -and !$AllowDirtyDevelopment) {
  throw "release provenance requires a clean 8vo product tree"
}
$productReleaseEligible =
  $productVerifiedClean -and !$AllowDirtyDevelopment
$productProfile = if ($productReleaseEligible) { "release-clean" } else {
  "development-nonrelease"
}

$corePath = Join-Path $Reader0Dir `
  "build\mupdf-pdf-core\x64\Release\reader0_mupdf_pdf.provenance.json"
$core = Get-Content -Raw -LiteralPath $corePath | ConvertFrom-Json
$sourceFiles = @(
  "code\build.c",
  "code\octavo.c",
  "code\platform\win32\octavo_accessibility_win32.c",
  "code\octavo_pdf.h",
  "code\octavo_pdf.c",
  "build\win32_build.bat",
  "scripts\audit_architecture.ps1",
  "scripts\audit_win32_pdf_provenance.ps1",
  "scripts\check_dependencies.ps1",
  "scripts\require_dependencies_current.ps1",
  "scripts\require_win32_product_source_state.ps1",
  "scripts\win32_pdf_product_source_state_smoke.ps1",
  "scripts\win32_octavo_pdf_stage1_smoke.ps1",
  "scripts\write_win32_pdf_build_provenance.ps1",
  "vendor\reader0_dependency\COMMIT",
  "vendor\reader0_dependency\VERSION",
  "vendor\reader0_dependency\API_VERSION",
  "vendor\ground0_dependency\COMMIT",
  "vendor\ground0_dependency\VERSION",
  "vendor\ui0_dependency\COMMIT",
  "vendor\readerview0_dependency\COMMIT",
  "vendor\readerview0_dependency\VERSION",
  "vendor\readerview0_dependency\API_VERSION",
  "vendor\mupdf_dependency\COMMIT",
  "vendor\mupdf_dependency\VERSION",
  "vendor\mupdf_dependency\SUBMODULES"
)
$sourceHashes = [ordered]@{}
foreach ($relative in $sourceFiles) {
  $path = Join-Path $RepoRoot $relative
  if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "provenance input is missing: $relative"
  }
  $sourceHashes[$relative.Replace("\", "/")] =
    (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
}

$compilerVersion = [Diagnostics.FileVersionInfo]::GetVersionInfo(
  $CompilerPath).FileVersion
$compilerHash = (Get-FileHash -Algorithm SHA256 -LiteralPath `
  $CompilerPath).Hash.ToLowerInvariant()
if ($compilerHash -ne [string]$core.build_inputs.compiler.sha256 -or
    $compilerVersion -ne [string]$core.build_inputs.compiler.file_version) {
  throw "final artifact compiler identity differs from verified PDF core"
}
if ([IO.Path]::GetDirectoryName($CompilerPath) -ine
    [IO.Path]::GetDirectoryName($LinkerPath)) {
  throw "final artifact compiler/linker do not share one tool directory"
}
$linkerVersion =
  [Diagnostics.FileVersionInfo]::GetVersionInfo($LinkerPath).FileVersion
$linkerHash = (Get-FileHash -Algorithm SHA256 -LiteralPath `
  $LinkerPath).Hash.ToLowerInvariant()

$document = [ordered]@{
  schema = "devze.8vo.win32-pdf-build-provenance.v2"
  generated_utc = [DateTime]::UtcNow.ToString("o")
  product = [ordered]@{
    git_commit = $productCommit
    git_tree = $productTree
    source_state = [ordered]@{
      profile = $productProfile
      verified_clean = $productVerifiedClean
      release_eligible = $productReleaseEligible
      development_override = [bool]$AllowDirtyDevelopment
      status_porcelain = @($productStatus)
    }
    executable = [ordered]@{
      path = $ExePath
      sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath `
        $ExePath).Hash.ToLowerInvariant()
    }
    link_map = [ordered]@{
      path = $MapPath
      sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath `
        $MapPath).Hash.ToLowerInvariant()
    }
    compile_flags = "/std:c11 /W4 /WX /O2 /MD /DREADER0_WITH_MUPDF=1"
    link_flags = "/OPT:REF /OPT:ICF /INCLUDE:fz_new_search"
    input_sha256 = $sourceHashes
  }
  dependencies = [ordered]@{
    reader0 = (Get-Content -Raw -LiteralPath `
      (Join-Path $RepoRoot "vendor\reader0_dependency\COMMIT")).Trim()
    ground0 = (Get-Content -Raw -LiteralPath `
      (Join-Path $RepoRoot "vendor\ground0_dependency\COMMIT")).Trim()
    ui0 = (Get-Content -Raw -LiteralPath `
      (Join-Path $RepoRoot "vendor\ui0_dependency\COMMIT")).Trim()
    readerview0 = (Get-Content -Raw -LiteralPath `
      (Join-Path $RepoRoot "vendor\readerview0_dependency\COMMIT")).Trim()
    mupdf = (Get-Content -Raw -LiteralPath `
      (Join-Path $RepoRoot "vendor\mupdf_dependency\COMMIT")).Trim()
    mupdf_checkout = $MupdfDir
  }
  compiler = [ordered]@{
    path = $CompilerPath
    file_version = $compilerVersion
    sha256 = $compilerHash
  }
  linker = [ordered]@{
    path = $LinkerPath
    file_version = $linkerVersion
    sha256 = $linkerHash
  }
  reader0_pdf_core = [ordered]@{
    input_fingerprint_sha256 = [string]$core.input_fingerprint_sha256
    core_sha256 = [string]$core.artifacts.core.sha256
    third_party_sha256 = [string]$core.artifacts.libthirdparty.sha256
    resources_sha256 = [string]$core.artifacts.libresources.sha256
  }
}
$json = $document | ConvertTo-Json -Depth 12
$temporary = "$OutputPath.tmp-$([Guid]::NewGuid().ToString('N'))"
try {
  [IO.File]::WriteAllText($temporary, $json + "`n",
                          [Text.UTF8Encoding]::new($false))
  Move-Item -LiteralPath $temporary -Destination $OutputPath -Force
} finally {
  if (Test-Path -LiteralPath $temporary) {
    Remove-Item -LiteralPath $temporary -Force
  }
}

Write-Host "8vo Win32 PDF artifact provenance: $OutputPath"
Write-Host "8vo product provenance profile: $productProfile verified_clean=$($productVerifiedClean.ToString().ToLowerInvariant()) release_eligible=$($productReleaseEligible.ToString().ToLowerInvariant()) development_override=$($AllowDirtyDevelopment.ToString().ToLowerInvariant()) status_count=$($productStatus.Count)"
Write-Host "8vo executable SHA-256: $($document.product.executable.sha256)"
