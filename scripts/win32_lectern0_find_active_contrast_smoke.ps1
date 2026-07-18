param(
  [string]$OutDir = "local\validation\reader-find-active-contrast-slice1b",
  [string]$BookPath = "C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub",
  [string]$ExePath = "build\win32\lectern0.exe",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ExpectedBookSha256 =
  "D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$Book = (Resolve-Path -LiteralPath $BookPath).Path
$BookHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Book).Hash
if ($BookHash -ne $ExpectedBookSha256) {
  throw "GOTM EPUB hash mismatch: expected=$ExpectedBookSha256 actual=$BookHash path=$Book"
}
$Exe = if ([System.IO.Path]::IsPathRooted($ExePath)) {
  $ExePath
} else { Join-Path $Root $ExePath }
$Out = if ([System.IO.Path]::IsPathRooted($OutDir)) {
  $OutDir
} else { Join-Path $Root $OutDir }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

if (!$SkipBuild) {
  Push-Location $Root
  try {
    & cmd /c build\win32_build.bat no_run
    if ($LASTEXITCODE -ne 0) {
      throw "strict Lectern0 build failed with exit code $LASTEXITCODE"
    }
  } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing Lectern0 executable: $Exe"
}

function Convert-HexRgb {
  param([string]$Hex)
  return @(
    ([Convert]::ToInt32($Hex.Substring(0, 2), 16) / 255.0)
    ([Convert]::ToInt32($Hex.Substring(2, 2), 16) / 255.0)
    ([Convert]::ToInt32($Hex.Substring(4, 2), 16) / 255.0)
  )
}

function Convert-SrgbLinear {
  param([double]$Value)
  if ($Value -le 0.04045) { return $Value / 12.92 }
  return [Math]::Pow(($Value + 0.055) / 1.055, 2.4)
}

function Get-Oklab {
  param([string]$Hex)
  $Rgb = Convert-HexRgb $Hex
  $R = Convert-SrgbLinear $Rgb[0]
  $G = Convert-SrgbLinear $Rgb[1]
  $B = Convert-SrgbLinear $Rgb[2]
  $L = 0.4122214708*$R + 0.5363325363*$G + 0.0514459929*$B
  $M = 0.2119034982*$R + 0.6806995451*$G + 0.1073969566*$B
  $S = 0.0883024619*$R + 0.2817188376*$G + 0.6299787005*$B
  $L = [Math]::Pow($L, 1.0/3.0)
  $M = [Math]::Pow($M, 1.0/3.0)
  $S = [Math]::Pow($S, 1.0/3.0)
  return @(
    (0.2104542553*$L + 0.7936177850*$M - 0.0040720468*$S)
    (1.9779984951*$L - 2.4285922050*$M + 0.4505937099*$S)
    (0.0259040371*$L + 0.7827717662*$M - 0.8086757660*$S)
  )
}

function Get-OklabDistance {
  param([string]$First, [string]$Second)
  $A = Get-Oklab $First
  $B = Get-Oklab $Second
  return [Math]::Sqrt(
    [Math]::Pow($A[0]-$B[0], 2) +
    [Math]::Pow($A[1]-$B[1], 2) +
    [Math]::Pow($A[2]-$B[2], 2))
}

function Get-RelativeLuminance {
  param([string]$Hex)
  $Rgb = Convert-HexRgb $Hex
  return 0.2126*(Convert-SrgbLinear $Rgb[0]) +
         0.7152*(Convert-SrgbLinear $Rgb[1]) +
         0.0722*(Convert-SrgbLinear $Rgb[2])
}

function Get-ContrastRatio {
  param([string]$First, [string]$Second)
  $A = Get-RelativeLuminance $First
  $B = Get-RelativeLuminance $Second
  return ([Math]::Max($A, $B) + 0.05) / ([Math]::Min($A, $B) + 0.05)
}

$Prefix = Join-Path $Out "gotm_paran"
$LogPath = Join-Path $Out "run.log"
& $Exe --reader-view-find-active-contrast-smoke $Book $Prefix *> $LogPath
if ($LASTEXITCODE -ne 0) {
  $Tail = (Get-Content -LiteralPath $LogPath -Tail 40) -join "`n"
  throw "Lectern0 Find contrast smoke failed`n$Tail"
}
$PassLine = Get-Content -LiteralPath $LogPath | Where-Object {
  $_ -match '^lectern0_reader_view_find_active_contrast result=pass '
} | Select-Object -Last 1
if (!$PassLine -or $PassLine -notmatch 'checkpoint=5 ' -or
    $PassLine -notmatch 'query=Paran active_index=2 themes=6 ') {
  throw "Lectern0 Find contrast result is incomplete: $PassLine"
}

$Ink = @{
  "dark"="F2F0EA"; "light"="1B1A18";
  "coral-dark"="F5EBDD"; "coral-light"="333230";
  "blue-dark"="EAF0F7"; "blue-light"="121A22"
}
$Results = @()
$Lines = Get-Content -LiteralPath $LogPath | Where-Object {
  $_ -match '^lectern0_reader_view_find_active_contrast theme='
}
if ($Lines.Count -ne 6) { throw "expected six theme evidence lines, found $($Lines.Count)" }
Add-Type -AssemblyName System.Drawing
foreach ($Line in $Lines) {
  $Match = [regex]::Match(
    $Line,
    '^lectern0_reader_view_find_active_contrast theme=([^ ]+) active=([0-9A-F]{6}) inactive=([0-9A-F]{6}) active_ranges=(\d+) inactive_ranges=(\d+) active_draws=(\d+) inactive_draws=(\d+) active_pixels=(\d+) inactive_pixels=(\d+) bmp=(.+)$')
  if (!$Match.Success) { throw "invalid theme evidence line: $Line" }
  $Name = $Match.Groups[1].Value
  $Active = $Match.Groups[2].Value
  $Inactive = $Match.Groups[3].Value
  $Distance = Get-OklabDistance $Active $Inactive
  $TextContrast = Get-ContrastRatio $Active $Ink[$Name]
  if ($Distance -lt 0.10) {
    throw "active/inactive OKLab distance below 0.10 for theme ${Name}: $Distance"
  }
  if ($TextContrast -lt 4.5) {
    throw "active-fill text contrast below 4.5 for theme ${Name}: $TextContrast"
  }
  $Bmp = $Match.Groups[10].Value
  if (!(Test-Path -LiteralPath $Bmp -PathType Leaf) -or
      (Get-Item -LiteralPath $Bmp).Length -le 54) {
    throw "missing rendered theme evidence: $Bmp"
  }
  $Png = Join-Path $Out "$Name.png"
  $Image = [System.Drawing.Image]::FromFile($Bmp)
  try { $Image.Save($Png, [System.Drawing.Imaging.ImageFormat]::Png) }
  finally { $Image.Dispose() }
  $Results += [pscustomobject]@{
    theme=$Name; active=$Active; inactive=$Inactive
    oklab_distance=$Distance; text_contrast=$TextContrast
    active_ranges=[int]$Match.Groups[4].Value
    inactive_ranges=[int]$Match.Groups[5].Value
    active_draws=[int]$Match.Groups[6].Value
    inactive_draws=[int]$Match.Groups[7].Value
    active_pixels=[UInt64]$Match.Groups[8].Value
    inactive_pixels=[UInt64]$Match.Groups[9].Value
    bmp=$Bmp; png=$Png
  }
}

$Summary = [pscustomobject]@{
  generated_at=(Get-Date).ToString("o"); status="pass"
  git_head=(& git -C $Root rev-parse HEAD).Trim()
  git_status=@(& git -C $Root status --porcelain --untracked-files=all)
  executable=@{ path=$Exe; sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Exe).Hash }
  book=@{ path=$Book; sha256=$BookHash; size=(Get-Item -LiteralPath $Book).Length }
  viewport=@{ width=1400; height=780 }; query="Paran"; active_index=2
  acceptance=@{ minimum_oklab_distance=0.10; minimum_text_contrast=4.5 }
  result_line=[string]$PassLine; themes=$Results; log=$LogPath
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 8 | Set-Content -Encoding ASCII -LiteralPath $SummaryPath
Write-Host $PassLine
Write-Host "win32_lectern0_find_active_contrast_smoke result=pass themes=6 summary=$SummaryPath"
