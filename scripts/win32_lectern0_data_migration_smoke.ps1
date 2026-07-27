param(
  [string]$OutDir = "local\8vo_data_migration_smoke"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\8vo.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing 8vo executable: $Exe"
}

$LocalRoot = [System.IO.Path]::GetFullPath((Join-Path $Root "local"))
$Out = [System.IO.Path]::GetFullPath((Join-Path $Root $OutDir))
$LocalPrefix = $LocalRoot.TrimEnd('\') + '\'
if (!$Out.StartsWith($LocalPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw "migration smoke output must remain under ${LocalRoot}: $Out"
}
if (Test-Path -LiteralPath $Out) {
  Remove-Item -LiteralPath $Out -Recurse -Force
}

$Legacy = Join-Path $Out "lectern0"
$Current = Join-Path $Out "8vo"
New-Item -ItemType Directory -Force -Path $Legacy | Out-Null

$Fixtures = [ordered]@{
  "state.v1" = "legacy-state"
  "library.v1" = "legacy-library"
  "settings.v1" = "legacy-settings"
  "annotations_0123456789abcdef.v1" = "legacy-annotations"
  "thumbnail_0000000000000001.v1" = "legacy-thumbnail"
  "reader_annotations.txt" = "legacy-export"
}
foreach ($Entry in $Fixtures.GetEnumerator()) {
  [System.IO.File]::WriteAllText(
    (Join-Path $Legacy $Entry.Key),
    $Entry.Value,
    [System.Text.Encoding]::UTF8)
}
[System.IO.File]::WriteAllText(
  (Join-Path $Legacy "unrelated.tmp"),
  "must-not-migrate",
  [System.Text.Encoding]::UTF8)

$PreviousLocalAppData = $env:LOCALAPPDATA
try {
  $env:LOCALAPPDATA = $Out
  $Output = & $Exe --data-migration-smoke 2>&1
  if ($LASTEXITCODE -ne 0) {
    $Output | Write-Host
    throw "8vo data migration smoke failed with exit code $LASTEXITCODE"
  }
} finally {
  $env:LOCALAPPDATA = $PreviousLocalAppData
}

$PassLine = $Output | Where-Object {
  $_ -match '^lectern0_data_migration_smoke result=pass '
} | Select-Object -Last 1
if (!$PassLine -or
    $PassLine -notmatch [regex]::Escape("directory=$Current")) {
  $Output | Write-Host
  throw "8vo data migration smoke did not report the expected directory"
}

foreach ($Name in $Fixtures.Keys) {
  $LegacyPath = Join-Path $Legacy $Name
  $CurrentPath = Join-Path $Current $Name
  if (!(Test-Path -LiteralPath $LegacyPath -PathType Leaf) -or
      !(Test-Path -LiteralPath $CurrentPath -PathType Leaf)) {
    throw "migration did not preserve and copy ${Name}"
  }
  if ((Get-FileHash -Algorithm SHA256 -LiteralPath $LegacyPath).Hash -ne
      (Get-FileHash -Algorithm SHA256 -LiteralPath $CurrentPath).Hash) {
    throw "migration content mismatch for ${Name}"
  }
}
if (Test-Path -LiteralPath (Join-Path $Current "unrelated.tmp")) {
  throw "migration copied an unsupported file"
}
$Marker = Join-Path $Current "migration_from_lectern0.v1"
if (!(Test-Path -LiteralPath $Marker -PathType Leaf)) {
  throw "migration marker is missing"
}

$StatePath = Join-Path $Current "state.v1"
$StateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $StatePath).Hash
[System.IO.File]::WriteAllText(
  (Join-Path $Legacy "state.v1"),
  "changed-after-migration",
  [System.Text.Encoding]::UTF8)
try {
  $env:LOCALAPPDATA = $Out
  $RepeatOutput = & $Exe --data-migration-smoke 2>&1
  if ($LASTEXITCODE -ne 0) {
    $RepeatOutput | Write-Host
    throw "8vo repeat migration smoke failed with exit code $LASTEXITCODE"
  }
} finally {
  $env:LOCALAPPDATA = $PreviousLocalAppData
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $StatePath).Hash -ne
    $StateHash) {
  throw "completed migration overwrote 8vo data on a later launch"
}

$RetryRoot = Join-Path $Out "retry"
$RetryLegacy = Join-Path $RetryRoot "lectern0"
$RetryCurrent = Join-Path $RetryRoot "8vo"
$RetryState = Join-Path $RetryLegacy "state.v1"
$RetryDestination = Join-Path $RetryCurrent "state.v1"
$RetryMarker = Join-Path $RetryCurrent "migration_from_lectern0.v1"
New-Item -ItemType Directory -Force -Path $RetryLegacy | Out-Null
New-Item -ItemType Directory -Force -Path $RetryDestination | Out-Null
[System.IO.File]::WriteAllText(
  $RetryState,
  "retry-state",
  [System.Text.Encoding]::UTF8)

$PreviousErrorActionPreference = $ErrorActionPreference
try {
  $env:LOCALAPPDATA = $RetryRoot
  $ErrorActionPreference = "Continue"
  $FailureOutput = & $Exe --data-migration-smoke 2>&1
  $FailureExit = $LASTEXITCODE
} finally {
  $ErrorActionPreference = $PreviousErrorActionPreference
  $env:LOCALAPPDATA = $PreviousLocalAppData
}
if ($FailureExit -eq 0 -or (Test-Path -LiteralPath $RetryMarker)) {
  $FailureOutput | Write-Host
  throw "failed migration was incorrectly marked complete"
}
if (!(Test-Path -LiteralPath $RetryState -PathType Leaf)) {
  throw "failed migration changed legacy data"
}

Remove-Item -LiteralPath $RetryDestination -Force
try {
  $env:LOCALAPPDATA = $RetryRoot
  $RetryOutput = & $Exe --data-migration-smoke 2>&1
  if ($LASTEXITCODE -ne 0) {
    $RetryOutput | Write-Host
    throw "8vo migration retry failed with exit code $LASTEXITCODE"
  }
} finally {
  $env:LOCALAPPDATA = $PreviousLocalAppData
}
if (!(Test-Path -LiteralPath $RetryDestination -PathType Leaf) -or
    (Get-FileHash -Algorithm SHA256 -LiteralPath $RetryState).Hash -ne
    (Get-FileHash -Algorithm SHA256 -LiteralPath $RetryDestination).Hash -or
    !(Test-Path -LiteralPath $RetryMarker -PathType Leaf)) {
  throw "migration retry did not complete cleanly"
}

Write-Host $PassLine
Write-Host "win32_lectern0_data_migration_smoke result=pass legacy=preserved current=8vo idempotent=marker unsupported=ignored retry=verified"
