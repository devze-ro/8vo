param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

function Resolve-EnvironmentDirectory {
  param(
    [string]$EnvironmentName,
    [switch]$IgnoreMissing
  )
  $value = [Environment]::GetEnvironmentVariable($EnvironmentName)
  if (!$value) { return "" }
  if (Test-Path -LiteralPath $value -PathType Container) {
    return (Resolve-Path -LiteralPath $value).Path
  }
  if ($IgnoreMissing) {
    Write-Warning "ignoring stale $EnvironmentName path: $value"
    return ""
  }
  throw "$EnvironmentName does not name an existing directory: $value"
}

function Resolve-DependencyPath {
  param(
    [string]$EnvironmentName,
    [string]$LegacyEnvironmentName,
    [string]$SiblingName
  )
  $path = Resolve-EnvironmentDirectory $EnvironmentName
  if ($path) { return $path }
  if ($LegacyEnvironmentName) {
    $path = Resolve-EnvironmentDirectory $LegacyEnvironmentName -IgnoreMissing
    if ($path) { return $path }
  }
  $local = Join-Path $RepoRoot "local\dependencies\$SiblingName"
  if (Test-Path -LiteralPath $local -PathType Container) {
    return (Resolve-Path -LiteralPath $local).Path
  }
  $sibling = Join-Path $RepoRoot "..\$SiblingName"
  if (!(Test-Path -LiteralPath $sibling -PathType Container)) {
    throw "$SiblingName checkout not found; set $EnvironmentName"
  }
  return (Resolve-Path -LiteralPath $sibling).Path
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

$reader0 = Resolve-DependencyPath "OCTAVO_READER0_DIR" "LECTERN0_READER0_DIR" "reader0"
$ui0 = Resolve-DependencyPath "OCTAVO_UI0_DIR" "LECTERN0_UI0_DIR" "ui0"
$readerview0 = Resolve-DependencyPath `
  "OCTAVO_READERVIEW0_DIR" "LECTERN0_READERVIEW0_DIR" "readerview0"
$ground0 = Resolve-EnvironmentDirectory "OCTAVO_GROUND0_DIR"
if (!$ground0) {
  foreach ($legacyName in @(
    "OCTAVO_ZERO_FOUNDATION_DIR",
    "ZERO_FOUNDATION_DIR"
  )) {
    $ground0 = Resolve-EnvironmentDirectory $legacyName -IgnoreMissing
    if ($ground0) { break }
  }
}
if (!$ground0) {
  $ground0 = Resolve-EnvironmentDirectory "GROUND0_DIR"
}
if (!$ground0) {
  $ground0 = Resolve-DependencyPath "OCTAVO_GROUND0_DIR" "" "ground0"
}

Require-Dependency "reader0" $reader0 (Join-Path $RepoRoot "vendor\reader0_dependency") `
  "code\reader0_version.h" "READER0_VERSION_STRING" "READER0_API_VERSION"
Require-Dependency "ui0" $ui0 (Join-Path $RepoRoot "vendor\ui0_dependency") `
  "code\ui0_version.h" "UI0_VERSION_STRING" "UI0_API_VERSION"
Require-Dependency "readerview0" $readerview0 (Join-Path $RepoRoot "vendor\readerview0_dependency") `
  "code\readerview0_version.h" "READERVIEW0_VERSION_STRING" "READERVIEW0_API_VERSION"
Require-Dependency "ground0" $ground0 (Join-Path $RepoRoot "vendor\ground0_dependency") `
  "code\foundation\version.h" "ZERO_FOUNDATION_VERSION_STRING"

$presentationMetadata = Join-Path $RepoRoot "vendor\ground0_dependency\PRESENTATION_ENGINE_API_VERSION"
$presentationHeader = Join-Path $ground0 "code\presentation_engine\presentation_engine.h"
if (!(Test-Path -LiteralPath $presentationMetadata -PathType Leaf)) {
  throw "ground0 Presentation Engine API metadata is missing"
}
if (!(Test-Path -LiteralPath $presentationHeader -PathType Leaf)) {
  throw "ground0 Presentation Engine header is missing"
}
$requiredPresentationApi = [int](Get-Content -Raw -LiteralPath $presentationMetadata).Trim()
$presentationHeaderText = [System.IO.File]::ReadAllText($presentationHeader)
$presentationApiMatch = [regex]::Match(
  $presentationHeaderText,
  "#define\s+PRESENTATION_ENGINE_API_VERSION\s+(\d+)")
if (!$presentationApiMatch.Success) {
  throw "ground0 Presentation Engine API macro is missing"
}
$currentPresentationApi = [int]$presentationApiMatch.Groups[1].Value
Write-Host "ground0 Presentation Engine required API: $requiredPresentationApi"
Write-Host "ground0 Presentation Engine current API:  $currentPresentationApi"
if ($currentPresentationApi -ne $requiredPresentationApi) {
  throw "ground0 Presentation Engine API mismatch"
}

Write-Host "octavo dependency status: current"
