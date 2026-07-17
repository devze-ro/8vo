param(
  [Parameter(Mandatory = $true)][string]$Re10Root,
  [Parameter(Mandatory = $true)][string]$Reader0Root,
  [Parameter(Mandatory = $true)][string]$UI0Root,
  [string]$LecternUI0Root = "",
  [Parameter(Mandatory = $true)][string]$ZeroFoundationRoot,
  [Parameter(Mandatory = $true)][string]$Readerview0Root,
  [string]$LecternReaderview0Root = "",
  [string]$Re10VcpkgRoot = "",
  [string]$OutDir = "local\stage2b0_reader_view_parity",
  [int]$AutoExitMs = 24000,
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$LecternRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$Re10Root = (Resolve-Path -LiteralPath $Re10Root).Path
$Reader0Root = (Resolve-Path -LiteralPath $Reader0Root).Path
$Re10UI0Root = (Resolve-Path -LiteralPath $UI0Root).Path
$LecternUI0Root = if ([string]::IsNullOrWhiteSpace($LecternUI0Root)) {
  $Re10UI0Root
} else {
  (Resolve-Path -LiteralPath $LecternUI0Root).Path
}
$ZeroFoundationRoot = (Resolve-Path -LiteralPath $ZeroFoundationRoot).Path
$Re10Readerview0Root = (Resolve-Path -LiteralPath $Readerview0Root).Path
$LecternReaderview0Root = if ([string]::IsNullOrWhiteSpace($LecternReaderview0Root)) {
  $Re10Readerview0Root
} else {
  (Resolve-Path -LiteralPath $LecternReaderview0Root).Path
}
$OutputRoot = if ([System.IO.Path]::IsPathRooted($OutDir)) {
  [System.IO.Path]::GetFullPath($OutDir)
} else {
  [System.IO.Path]::GetFullPath((Join-Path $LecternRoot $OutDir))
}
$Artifacts = Join-Path $OutputRoot "artifacts"
$Generated = Join-Path $OutputRoot "generated"
$Vaults = Join-Path $OutputRoot "vaults"
$Logs = Join-Path $OutputRoot "logs"
New-Item -ItemType Directory -Force -Path $Artifacts, $Generated, $Vaults, $Logs | Out-Null

function Get-Head([string]$Root) {
  return (& git -C $Root rev-parse HEAD).Trim()
}

function Require-ExactCommit([string]$Name, [string]$Root, [string]$Expected) {
  $actual = Get-Head $Root
  if ($actual -ne $Expected.Trim()) {
    throw "$Name revision mismatch: required=$Expected actual=$actual"
  }
}

$LecternReaderviewCommit = Get-Content -Raw -LiteralPath (Join-Path $LecternRoot "vendor\readerview0_dependency\COMMIT")
$ZeroCommit = Get-Content -Raw -LiteralPath (Join-Path $LecternRoot "vendor\zero_foundation_dependency\COMMIT")
$Reader0Commit = Get-Content -Raw -LiteralPath (Join-Path $LecternRoot "vendor\reader0_dependency\COMMIT")
$LecternUI0Commit = Get-Content -Raw -LiteralPath (Join-Path $LecternRoot "vendor\ui0_dependency\COMMIT")
$Re10ReaderviewCommit = Get-Content -Raw -LiteralPath (Join-Path $Re10Root "vendor\readerview0_dependency\COMMIT")
$Re10UI0Commit = Get-Content -Raw -LiteralPath (Join-Path $Re10Root "vendor\ui0_dependency\COMMIT")
$CrossRevisionConformance =
  $Re10ReaderviewCommit.Trim() -ne $LecternReaderviewCommit.Trim() -or
  $Re10UI0Commit.Trim() -ne $LecternUI0Commit.Trim()
$StageLabel = if ($CrossRevisionConformance) { "Stage 2B-2" } else { "Stage 2B-0" }
Require-ExactCommit "re10 readerview0" $Re10Readerview0Root $Re10ReaderviewCommit
Require-ExactCommit "lectern0 readerview0" $LecternReaderview0Root $LecternReaderviewCommit
Require-ExactCommit "zero_foundation" $ZeroFoundationRoot $ZeroCommit
Require-ExactCommit "reader0" $Reader0Root $Reader0Commit
Require-ExactCommit "re10 ui0" $Re10UI0Root $Re10UI0Commit
Require-ExactCommit "lectern0 ui0" $LecternUI0Root $LecternUI0Commit

if (!$SkipBuild) {
  $env:RE10_READER0_DIR = $Reader0Root
  $env:RE10_UI0_DIR = $Re10UI0Root
  $env:RE10_READERVIEW0_DIR = $Re10Readerview0Root
  $env:ZERO_FOUNDATION_DIR = $ZeroFoundationRoot
  if (![string]::IsNullOrWhiteSpace($Re10VcpkgRoot)) {
    $Re10VcpkgRoot = (Resolve-Path -LiteralPath $Re10VcpkgRoot).Path
    $env:RE10_SQLITE_DIR = $Re10VcpkgRoot
    $env:RE10_CURL_DIR = $Re10VcpkgRoot
    if (Test-Path -LiteralPath (Join-Path $Re10VcpkgRoot "lib\zs.lib")) {
      $env:RE10_CURL_LIBS = "libcurl.lib zs.lib bcrypt.lib advapi32.lib crypt32.lib secur32.lib ws2_32.lib iphlpapi.lib"
    }
  }
  Push-Location $Re10Root
  try {
    & .\build\win32_build.bat no_run
    if ($LASTEXITCODE -ne 0) { throw "re10 build failed: $LASTEXITCODE" }
  } finally {
    Pop-Location
  }

  $env:LECTERN0_READER0_DIR = $Reader0Root
  $env:LECTERN0_UI0_DIR = $LecternUI0Root
  $env:LECTERN0_READERVIEW0_DIR = $LecternReaderview0Root
  $env:LECTERN0_ZERO_FOUNDATION_DIR = $ZeroFoundationRoot
  Push-Location $LecternRoot
  try {
    & .\build\win32_build.bat no_run
    if ($LASTEXITCODE -ne 0) { throw "lectern0 build failed: $LASTEXITCODE" }
  } finally {
    Pop-Location
  }
}

$Re10Exe = (Resolve-Path -LiteralPath (Join-Path $Re10Root "build\win32\re10.exe")).Path
$LecternExe = (Resolve-Path -LiteralPath (Join-Path $LecternRoot "build\win32\lectern0.exe")).Path

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System.Runtime.InteropServices;
public static class ReaderViewParityCursor {
  [DllImport("user32.dll")]
  public static extern bool SetCursorPos(int x, int y);
}
'@

function Add-ZipTextEntry {
  param(
    [System.IO.Compression.ZipArchive]$Zip,
    [string]$Path,
    [string]$Text,
    [System.IO.Compression.CompressionLevel]$Compression = [System.IO.Compression.CompressionLevel]::Optimal
  )
  $entry = $Zip.CreateEntry($Path, $Compression)
  $entry.LastWriteTime = [DateTimeOffset]::new(
    2000, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
  $stream = $entry.Open()
  try {
    # Keep fixture bytes independent of the script checkout's line endings.
    $stableText = $Text.Replace("`r`n", "`n").Replace("`r", "`n")
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($stableText)
    $stream.Write($bytes, 0, $bytes.Length)
  } finally {
    $stream.Dispose()
  }
}

function New-Stage2B0Epub([string]$Path) {
  if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
  $file = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew)
  try {
    $zip = [System.IO.Compression.ZipArchive]::new(
      $file, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
      Add-ZipTextEntry $zip "mimetype" "application/epub+zip" ([System.IO.Compression.CompressionLevel]::NoCompression)
      Add-ZipTextEntry $zip "META-INF/container.xml" @'
<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
'@
      Add-ZipTextEntry $zip "EPUB/package.opf" @'
<?xml version="1.0" encoding="UTF-8"?>
<package version="3.0" unique-identifier="bookid" xmlns="http://www.idpf.org/2007/opf">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:reader-view-stage2b0-fixture</dc:identifier>
    <dc:title>Reader View Parity Fixture</dc:title><dc:language>en</dc:language>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="one" href="chapter-one.xhtml" media-type="application/xhtml+xml"/>
    <item id="two" href="chapter-two.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="one"/><itemref idref="two"/></spine>
</package>
'@
      Add-ZipTextEntry $zip "EPUB/nav.xhtml" @'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Reader View Parity Fixture</title></head><body>
<nav epub:type="toc"><ol><li><a href="chapter-one.xhtml">First Light</a></li><li><a href="chapter-two.xhtml">Second Light</a></li></ol></nav>
</body></html>
'@
      $repeat = (1..18 | ForEach-Object {
        "<p>Alpha reader parity sentence $_ keeps deterministic pagination, search, toolbar, panel, and viewport evidence populated.</p>"
      }) -join "`n"
      Add-ZipTextEntry $zip "EPUB/chapter-one.xhtml" @"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><head><title>First Light</title></head>
<body><h1>First Light</h1><p>A synthetic redistributable EPUB for two-host reader-view comparison.</p>$repeat</body></html>
"@
      Add-ZipTextEntry $zip "EPUB/chapter-two.xhtml" @'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Second Light</title></head>
<body><h1>Second Light</h1><p>Beta closes the fixture with a second semantic navigation destination.</p></body></html>
'@
    } finally {
      $zip.Dispose()
    }
  } finally {
    $file.Dispose()
  }
}

function Read-Evidence([string]$Path) {
  $result = [ordered]@{}
  foreach ($line in Get-Content -LiteralPath $Path) {
    $parts = $line -split "=", 2
    if ($parts.Count -eq 2) { $result[$parts[0]] = $parts[1] }
  }
  return $result
}

function Save-Crop([string]$SourcePath, [string]$DestinationPath,
                   [int]$X, [int]$Y, [int]$Width, [int]$Height) {
  $source = [System.Drawing.Bitmap]::FromFile($SourcePath)
  try {
    if ($X -lt 0 -or $Y -lt 0 -or $X + $Width -gt $source.Width -or
        $Y + $Height -gt $source.Height) {
      throw "crop outside source: source=$($source.Width)x$($source.Height) crop=$X,$Y,$Width,$Height"
    }
    $rect = [System.Drawing.Rectangle]::new($X, $Y, $Width, $Height)
    $crop = $source.Clone($rect, $source.PixelFormat)
    try {
      $crop.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Bmp)
    } finally {
      $crop.Dispose()
    }
  } finally {
    $source.Dispose()
  }
}

function Get-DecodedPixelHash([string]$Path) {
  $bitmap = [System.Drawing.Bitmap]::FromFile($Path)
  try {
    $rect = [System.Drawing.Rectangle]::new(0, 0, $bitmap.Width, $bitmap.Height)
    $data = $bitmap.LockBits(
      $rect,
      [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
      [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
      $rowBytes = $bitmap.Width * 4
      $bytes = [byte[]]::new(8 + $rowBytes * $bitmap.Height)
      [System.Buffer]::BlockCopy([System.BitConverter]::GetBytes($bitmap.Width), 0,
                                 $bytes, 0, 4)
      [System.Buffer]::BlockCopy([System.BitConverter]::GetBytes($bitmap.Height), 0,
                                 $bytes, 4, 4)
      for ($y = 0; $y -lt $bitmap.Height; $y += 1) {
        $source = [System.IntPtr]::Add($data.Scan0, $y * $data.Stride)
        [System.Runtime.InteropServices.Marshal]::Copy(
          $source, $bytes, 8 + $y * $rowBytes, $rowBytes)
      }
      $sha = [System.Security.Cryptography.SHA256]::Create()
      try {
        return [System.BitConverter]::ToString(
          $sha.ComputeHash($bytes)).Replace("-", "")
      } finally {
        $sha.Dispose()
      }
    } finally {
      $bitmap.UnlockBits($data)
    }
  } finally {
    $bitmap.Dispose()
  }
}

$Fixture = Join-Path $Generated "reader_view_stage2b0.epub"
New-Stage2B0Epub $Fixture

$Cases = @(
  [pscustomobject]@{ name="wide_light_default"; width=1400; height=780; theme="light"; left="none"; right="closed"; popup="none"; query="-"; re10=@() },
  [pscustomobject]@{ name="narrow_light_default"; width=940; height=520; theme="light"; left="none"; right="closed"; popup="none"; query="-"; re10=@() },
  [pscustomobject]@{ name="wide_dark_contents"; width=1400; height=780; theme="dark"; left="contents"; right="closed"; popup="none"; query="-"; re10=@("click reader.contents", "wait_frames 3") },
  [pscustomobject]@{ name="wide_light_find_alpha"; width=1400; height=780; theme="light"; left="find"; right="closed"; popup="none"; query="alpha"; re10=@("click reader.search", "wait_frames 2", "click reader.nav.search_input", "type_reader_search alpha", "reader_search_submit", "wait_frames 6") },
  [pscustomobject]@{ name="wide_light_bookmark_right"; width=1400; height=780; theme="light"; left="none"; right="bookmark"; popup="none"; query="-"; re10=@("click reader.bookmark", "wait_frames 3", "click reader.context", "wait_frames 3") },
  [pscustomobject]@{ name="wide_light_font_menu"; width=1400; height=780; theme="light"; left="none"; right="closed"; popup="font"; query="-"; re10=@("click reader.font", "wait_frames 3") }
)

function Invoke-Re10Capture([object]$Case, [int]$Run) {
  $caseDir = Join-Path $Artifacts $Case.name
  New-Item -ItemType Directory -Force -Path $caseDir | Out-Null
  $scriptPath = Join-Path $Generated "$($Case.name).re10.$Run.txt"
  $evidence = Join-Path $caseDir "re10.$Run.evidence.txt"
  $fullBmp = Join-Path $caseDir "re10.$Run.full.bmp"
  $cropBmp = Join-Path $caseDir "re10.$Run.reader.bmp"
  $log = Join-Path $Logs "$($Case.name).re10.$Run.log"
  $vault = Join-Path $Vaults "$($Case.name).re10.$Run"
  New-Item -ItemType Directory -Force -Path $vault | Out-Null
  $outerWidth = [int]$Case.width + 20
  $outerHeight = [int]$Case.height + 20
  $lines = @(
    "resize_window $outerWidth $outerHeight",
    "command :reader open $Fixture",
    "wait_frames 10",
    "reader_presentation 1 0 0",
    "wait_frames 4",
    "command :theme $($Case.theme)",
    "wait_frames 3"
  ) + @($Case.re10) + @(
    "dump_reader_view_parity $evidence",
    "screenshot $fullBmp",
    "exit"
  )
  Set-Content -LiteralPath $scriptPath -Encoding ASCII -Value $lines
  [void][ReaderViewParityCursor]::SetCursorPos(0, 0)
  & $Re10Exe --window "--auto_exit_ms=$AutoExitMs" "--window_client_w=$outerWidth" "--window_client_h=$outerHeight" --reader_db=:memory: "--vault=$vault" "--debug_automation=$scriptPath" *> $log
  if ($LASTEXITCODE -ne 0 -or !(Test-Path $evidence) -or !(Test-Path $fullBmp)) {
    throw "re10 capture failed: case=$($Case.name) run=$Run exit=$LASTEXITCODE log=$log"
  }
  Save-Crop $fullBmp $cropBmp 10 10 ([int]$Case.width) ([int]$Case.height)
  return [pscustomobject]@{
    evidence = $evidence
    crop = $cropBmp
    evidence_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $evidence).Hash
    crop_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $cropBmp).Hash
    pixel_sha256 = Get-DecodedPixelHash $cropBmp
    values = Read-Evidence $evidence
  }
}

function Invoke-LecternCapture([object]$Case, [int]$Run) {
  $caseDir = Join-Path $Artifacts $Case.name
  New-Item -ItemType Directory -Force -Path $caseDir | Out-Null
  $evidence = Join-Path $caseDir "lectern0.$Run.evidence.txt"
  $bmp = Join-Path $caseDir "lectern0.$Run.reader.bmp"
  $log = Join-Path $Logs "$($Case.name).lectern0.$Run.log"
  [void][ReaderViewParityCursor]::SetCursorPos(0, 0)
  & $LecternExe --reader-view-parity-capture $Fixture ([string]$Case.width) ([string]$Case.height) $Case.theme $Case.left $Case.right $Case.popup $Case.query $evidence $bmp *> $log
  if ($LASTEXITCODE -ne 0 -or !(Test-Path $evidence) -or !(Test-Path $bmp)) {
    throw "lectern0 capture failed: case=$($Case.name) run=$Run exit=$LASTEXITCODE log=$log"
  }
  return [pscustomobject]@{
    evidence = $evidence
    crop = $bmp
    evidence_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $evidence).Hash
    crop_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $bmp).Hash
    pixel_sha256 = Get-DecodedPixelHash $bmp
    values = Read-Evidence $evidence
  }
}

$CompareKeys = @(
  "bounds", "layout_mode", "toolbar_density", "viewport",
  "projection_hash", "layout_hash", "control_hash", "draw_hash",
  "semantic_hash", "action_hash", "control_count", "draw_count",
  "semantic_count", "action_count"
)
$Results = @()
$Deterministic = $true
$AllExact = $true
$AllPixelExact = $true
foreach ($case in $Cases) {
  $re10A = Invoke-Re10Capture $case 1
  $re10B = Invoke-Re10Capture $case 2
  $lecternA = Invoke-LecternCapture $case 1
  $lecternB = Invoke-LecternCapture $case 2
  $re10Stable = $re10A.evidence_sha256 -eq $re10B.evidence_sha256 -and
                $re10A.pixel_sha256 -eq $re10B.pixel_sha256
  $lecternStable = $lecternA.evidence_sha256 -eq $lecternB.evidence_sha256 -and
                   $lecternA.pixel_sha256 -eq $lecternB.pixel_sha256
  if (!$re10Stable -or !$lecternStable) { $Deterministic = $false }
  $componentMatches = [ordered]@{}
  foreach ($key in $CompareKeys) {
    $componentMatches[$key] = $re10A.values[$key] -eq $lecternA.values[$key]
  }
  $pixelsMatch = $re10A.pixel_sha256 -eq $lecternA.pixel_sha256
  if (!$pixelsMatch) { $AllPixelExact = $false }
  $caseExact = $pixelsMatch -and !($componentMatches.Values -contains $false)
  if (!$caseExact) { $AllExact = $false }
  $Results += [pscustomobject]@{
    name = $case.name
    reader_client = "$($case.width)x$($case.height)"
    theme = $case.theme
    left = $case.left
    right = $case.right
    popup = $case.popup
    query = $case.query
    re10_repeatable = $re10Stable
    lectern0_repeatable = $lecternStable
    component_matches = $componentMatches
    pixel_match = $pixelsMatch
    exact_parity = $caseExact
    re10 = $re10A
    lectern0 = $lecternA
  }
  $label = if ($caseExact) {
    "EXACT"
  } elseif ($pixelsMatch) {
    "PIXEL-EXACT-RECORD-DIFFERENT"
  } else {
    "BASELINE-DIFFERENT"
  }
  Write-Host "$label $($case.name) re10_repeatable=$re10Stable lectern0_repeatable=$lecternStable pixels=$pixelsMatch"
}

$Manifest = [pscustomobject]@{
  schema = if ($CrossRevisionConformance) { "reader_view_stage2b2_v1" } else { "reader_view_stage2b0_v1" }
  status = if ($Deterministic) {
    if ($CrossRevisionConformance) { "conformance_recorded" } else { "baseline_recorded" }
  } else { "failed_nondeterministic" }
  exact_parity = $AllExact
  exact_visual_parity = $AllPixelExact
  deterministic = $Deterministic
  fixture = $Fixture
  fixture_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $Fixture).Hash
  fixed_reader_client_pixels = $true
  re10_outer_shell_inset = 10
  re10_head = Get-Head $Re10Root
  lectern0_head = Get-Head $LecternRoot
  re10_readerview0_head = Get-Head $Re10Readerview0Root
  lectern0_readerview0_head = Get-Head $LecternReaderview0Root
  reader0_head = Get-Head $Reader0Root
  re10_ui0_head = Get-Head $Re10UI0Root
  lectern0_ui0_head = Get-Head $LecternUI0Root
  zero_foundation_head = Get-Head $ZeroFoundationRoot
  results = $Results
}
$ManifestPath = Join-Path $OutputRoot "manifest.json"
$Manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $ManifestPath -Encoding ASCII
$DifferenceSummary = if ($AllPixelExact) {
  "Stage 2B-3 visual closure is exact for every scenario after normalizing decoded 32-bit pixels. Remaining record differences, if any, are reported separately and do not represent a rendered-pixel difference."
} elseif ($CrossRevisionConformance) {
  "Remaining cross-host differences are recorded inputs to the Stage 2B-3 pixel-closure gate. Stage 2B-2 fails only for capture/build failure, missing evidence, or per-host nondeterminism."
} else {
  "Cross-host differences are expected at this baseline and are inputs to Stage 2B-1/2B-2. Stage 2B-0 fails only for capture/build failure, missing evidence, or per-host nondeterminism."
}

$Report = @(
  if ($CrossRevisionConformance) {
    "# Reader View Stage 2B-2 lectern0 reference conformance"
  } else {
    "# Reader View Stage 2B-0 deterministic parity baseline"
  },
  "",
  "Status: $($Manifest.status)",
  "",
  "Exact cross-host parity: $AllExact",
  "",
  "Exact visual parity: $AllPixelExact",
  "",
  "Fixture SHA-256: $($Manifest.fixture_sha256)",
  "",
  "| Case | re10 repeatable | lectern0 repeatable | record equality | pixel equality |",
  "| --- | --- | --- | --- | --- |"
)
foreach ($result in $Results) {
  $recordEqual = !($result.component_matches.Values -contains $false)
  $Report += "| $($result.name) | $($result.re10_repeatable) | $($result.lectern0_repeatable) | $recordEqual | $($result.pixel_match) |"
}
$Report += @(
  "",
  $DifferenceSummary,
  "",
  "Machine-readable details: manifest.json"
)
$Report | Set-Content -LiteralPath (Join-Path $OutputRoot "report.md") -Encoding ASCII

if (!$Deterministic) { throw "$StageLabel parity capture was not deterministic; see $ManifestPath" }
Write-Host "Reader View $StageLabel evidence recorded: $ManifestPath"
exit 0
