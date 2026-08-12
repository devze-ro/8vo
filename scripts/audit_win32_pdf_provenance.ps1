param(
  [Parameter(Mandatory = $true)]
  [string]$Reader0Dir,
  [Parameter(Mandatory = $true)]
  [string]$MupdfDir,
  [Parameter(Mandatory = $true)]
  [string]$CompilerPath,
  [Parameter(Mandatory = $true)]
  [string]$LinkerPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 3.0
$Reader0Dir = (Resolve-Path -LiteralPath $Reader0Dir).Path
$MupdfDir = (Resolve-Path -LiteralPath $MupdfDir).Path
$CompilerPath = (Resolve-Path -LiteralPath $CompilerPath).Path
$LinkerPath = (Resolve-Path -LiteralPath $LinkerPath).Path
$env:READER0_MUPDF_DIR = $MupdfDir

foreach ($name in @("CL", "_CL_", "LINK")) {
  if ([Environment]::GetEnvironmentVariable($name)) {
    throw "ambient $name option injection is not allowed"
  }
}

$verify = Join-Path $Reader0Dir "scripts\verify_mupdf_pdf_core_provenance.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $verify -MupdfDir $MupdfDir
if ($LASTEXITCODE -ne 0) {
  throw "Reader0 MuPDF core provenance verification failed"
}

$provenancePath = Join-Path $Reader0Dir `
  "build\mupdf-pdf-core\x64\Release\reader0_mupdf_pdf.provenance.json"
$provenance = Get-Content -Raw -LiteralPath $provenancePath | ConvertFrom-Json
if ([int]$provenance.schema -ne 1 -or
    [string]$provenance.profile -ne "reader0-mupdf-pdf-core-win32" -or
    [string]$provenance.build_inputs.configuration -ne "Release" -or
    [string]$provenance.build_inputs.platform -ne "x64" -or
    [string]$provenance.build_inputs.platform_toolset -ne "v143") {
  throw "unexpected Reader0 MuPDF core provenance schema/profile/toolset"
}
$vcToolsVersion = [string]$provenance.build_inputs.vctools_version
$windowsSdkVersion = [string]$provenance.build_inputs.windows_sdk_version
$expectedCompilerSuffix =
  "\VC\Tools\MSVC\$vcToolsVersion\bin\Hostx64\x64\cl.exe"
$expectedLinkerSuffix =
  "\VC\Tools\MSVC\$vcToolsVersion\bin\Hostx64\x64\link.exe"
if (!$CompilerPath.EndsWith(
      $expectedCompilerSuffix, [StringComparison]::OrdinalIgnoreCase)) {
  throw "selected compiler path does not match Reader0's exact x64 MSVC toolset"
}
if (!$LinkerPath.EndsWith(
      $expectedLinkerSuffix, [StringComparison]::OrdinalIgnoreCase)) {
  throw "selected linker path does not match Reader0's exact x64 MSVC toolset"
}
if ([IO.Path]::GetDirectoryName($CompilerPath) -ine
    [IO.Path]::GetDirectoryName($LinkerPath)) {
  throw "selected compiler and linker do not share one exact tool directory"
}
$pathLinker = (Get-Command link.exe -CommandType Application `
  -ErrorAction Stop).Source
if ((Resolve-Path -LiteralPath $pathLinker).Path -ine $LinkerPath) {
  throw "PATH does not resolve Reader0's selected x64 linker first"
}
$selectedVcToolsVersion = ([string]$env:VCToolsVersion).Trim().TrimEnd("\")
$selectedWindowsSdkVersion =
  ([string]$env:WindowsSDKVersion).Trim().TrimEnd("\")
if ($selectedVcToolsVersion -cne $vcToolsVersion) {
  throw "selected VCToolsVersion differs from Reader0 core provenance"
}
if ($selectedWindowsSdkVersion -cne $windowsSdkVersion) {
  throw "selected Windows SDK differs from Reader0 core provenance"
}
$compilerHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $CompilerPath).Hash.ToLowerInvariant()
$compilerVersion = [Diagnostics.FileVersionInfo]::GetVersionInfo($CompilerPath).FileVersion
if ($compilerHash -ne [string]$provenance.build_inputs.compiler.sha256) {
  throw "selected cl.exe SHA-256 does not match freshly verified Reader0 core provenance"
}
if ($compilerVersion -ne [string]$provenance.build_inputs.compiler.file_version) {
  throw "selected cl.exe version does not match freshly verified Reader0 core provenance"
}
$linkerHash = (Get-FileHash -Algorithm SHA256 `
  -LiteralPath $LinkerPath).Hash.ToLowerInvariant()
$linkerVersion =
  [Diagnostics.FileVersionInfo]::GetVersionInfo($LinkerPath).FileVersion

Write-Host "8vo PDF core/toolchain provenance: pass"
Write-Host "8vo selected compiler: $CompilerPath"
Write-Host "8vo selected compiler version: $compilerVersion"
Write-Host "8vo selected compiler SHA-256: $compilerHash"
Write-Host "8vo selected linker: $LinkerPath"
Write-Host "8vo selected linker version: $linkerVersion"
Write-Host "8vo selected linker SHA-256: $linkerHash"
Write-Host "8vo selected VCToolsVersion: $selectedVcToolsVersion"
Write-Host "8vo selected Windows SDK: $selectedWindowsSdkVersion"
Write-Host "8vo Reader0 core fingerprint: $($provenance.input_fingerprint_sha256)"
