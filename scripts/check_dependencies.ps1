param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

function Resolve-DependencyPath {
  param([string]$EnvironmentName, [string]$SiblingName)
  $value = [Environment]::GetEnvironmentVariable($EnvironmentName)
  if ($value) { return (Resolve-Path -LiteralPath $value).Path }
  return (Resolve-Path -LiteralPath (Join-Path $RepoRoot "..\$SiblingName")).Path
}

function Require-Dependency {
  param(
    [string]$Name,
    [string]$Path,
    [string]$MetadataDirectory,
    [string]$VersionHeader,
    [string]$VersionMacro,
    [string]$ApiMacro = ""
  )

  $requiredCommit = (Get-Content -Raw -LiteralPath (Join-Path $MetadataDirectory "COMMIT")).Trim()
  $requiredVersion = (Get-Content -Raw -LiteralPath (Join-Path $MetadataDirectory "VERSION")).Trim()
  $currentCommit = (git -C $Path rev-parse HEAD).Trim()
  $dirty = @(git -C $Path status --porcelain)
  $headerText = [System.IO.File]::ReadAllText((Join-Path $Path $VersionHeader))
  $versionMatch = [regex]::Match($headerText, "#define\s+$VersionMacro\s+`"([^`"]+)`"")
  if (!$versionMatch.Success) { throw "$Name version macro missing: $VersionMacro" }
  $currentVersion = $versionMatch.Groups[1].Value

  Write-Host "$Name required commit:  $requiredCommit"
  Write-Host "$Name current commit:   $currentCommit"
  Write-Host "$Name required version: $requiredVersion"
  Write-Host "$Name current version:  $currentVersion"

  if ($currentCommit -ne $requiredCommit) { throw "$Name exact commit mismatch" }
  if ($dirty.Count -ne 0) { throw "$Name working tree is dirty" }
  if ($currentVersion -ne $requiredVersion) { throw "$Name version mismatch" }

  if ($ApiMacro) {
    $requiredApi = [int](Get-Content -Raw -LiteralPath (Join-Path $MetadataDirectory "API_VERSION")).Trim()
    $apiMatch = [regex]::Match($headerText, "#define\s+$ApiMacro\s+(\d+)")
    if (!$apiMatch.Success) { throw "$Name API macro missing: $ApiMacro" }
    $currentApi = [int]$apiMatch.Groups[1].Value
    Write-Host "$Name required API:     $requiredApi"
    Write-Host "$Name current API:      $currentApi"
    if ($currentApi -ne $requiredApi) { throw "$Name API mismatch" }
  }
}

$reader0 = Resolve-DependencyPath "LECTERN0_READER0_DIR" "reader0"
$ui0 = Resolve-DependencyPath "LECTERN0_UI0_DIR" "ui0"
$readerview0 = Resolve-DependencyPath "LECTERN0_READERVIEW0_DIR" "readerview0"
$zero = if ($env:LECTERN0_ZERO_FOUNDATION_DIR) {
  (Resolve-Path -LiteralPath $env:LECTERN0_ZERO_FOUNDATION_DIR).Path
} elseif ($env:ZERO_FOUNDATION_DIR) {
  (Resolve-Path -LiteralPath $env:ZERO_FOUNDATION_DIR).Path
} else {
  Resolve-DependencyPath "LECTERN0_ZERO_FOUNDATION_DIR" "zero_foundation"
}

Require-Dependency "reader0" $reader0 (Join-Path $RepoRoot "vendor\reader0_dependency") `
  "code\reader0_version.h" "READER0_VERSION_STRING" "READER0_API_VERSION"
Require-Dependency "ui0" $ui0 (Join-Path $RepoRoot "vendor\ui0_dependency") `
  "code\ui0_version.h" "UI0_VERSION_STRING" "UI0_API_VERSION"
Require-Dependency "readerview0" $readerview0 (Join-Path $RepoRoot "vendor\readerview0_dependency") `
  "code\readerview0_version.h" "READERVIEW0_VERSION_STRING" "READERVIEW0_API_VERSION"
Require-Dependency "zero_foundation" $zero (Join-Path $RepoRoot "vendor\zero_foundation_dependency") `
  "code\foundation\version.h" "ZERO_FOUNDATION_VERSION_STRING"

$presentationMetadata = Join-Path $RepoRoot "vendor\zero_foundation_dependency\PRESENTATION_ENGINE_API_VERSION"
$presentationHeader = Join-Path $zero "code\presentation_engine\presentation_engine.h"
if (!(Test-Path -LiteralPath $presentationMetadata -PathType Leaf)) {
  throw "zero_foundation Presentation Engine API metadata is missing"
}
if (!(Test-Path -LiteralPath $presentationHeader -PathType Leaf)) {
  throw "zero_foundation Presentation Engine header is missing"
}
$requiredPresentationApi = [int](Get-Content -Raw -LiteralPath $presentationMetadata).Trim()
$presentationHeaderText = [System.IO.File]::ReadAllText($presentationHeader)
$presentationApiMatch = [regex]::Match(
  $presentationHeaderText,
  "#define\s+PRESENTATION_ENGINE_API_VERSION\s+(\d+)")
if (!$presentationApiMatch.Success) {
  throw "zero_foundation Presentation Engine API macro is missing"
}
$currentPresentationApi = [int]$presentationApiMatch.Groups[1].Value
Write-Host "zero_foundation Presentation Engine required API: $requiredPresentationApi"
Write-Host "zero_foundation Presentation Engine current API:  $currentPresentationApi"
if ($currentPresentationApi -ne $requiredPresentationApi) {
  throw "zero_foundation Presentation Engine API mismatch"
}

Write-Host "lectern0 dependency status: current"
