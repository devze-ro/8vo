param(
  [string]$OutDir = "local\validation\reader-find-snippet-context-slice2",
  [string]$BookPath = "C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub",
  [string]$ExePath = "build\win32\8vo.exe",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ExpectedBookSha256 =
  "D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
if (!(Test-Path -LiteralPath $BookPath -PathType Leaf)) {
  throw "missing GOTM EPUB: $BookPath"
}
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
      throw "strict Eightvo build failed with exit code $LASTEXITCODE"
    }
  } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing Eightvo executable: $Exe"
}

$Bmp = Join-Path $Out "gotm_paran_first_result.bmp"
$Png = Join-Path $Out "gotm_paran_first_result.png"
$Log = Join-Path $Out "run.log"
& $Exe --reader-view-find-snippet-context-smoke $Book $Bmp *> $Log
if ($LASTEXITCODE -ne 0) {
  $Tail = (Get-Content -LiteralPath $Log -Tail 50) -join "`n"
  throw "Eightvo Find snippet-context smoke failed`n$Tail"
}
$PassLine = Get-Content -LiteralPath $Log | Where-Object {
  $_ -match '^eightvo_reader_view_find_snippet_context result=pass '
} | Select-Object -Last 1
if (!$PassLine) { throw "Eightvo Find snippet-context pass line is missing" }
$Match = [regex]::Match(
  $PassLine,
  '^eightvo_reader_view_find_snippet_context result=pass checkpoint=5 query=Paran active_index=0 visible_bytes=(\d+) match_start=(\d+) match_size=5 highlight_draws=(\d+) highlight_pixels=(\d+) bmp=(.+)$')
if (!$Match.Success -or [int]$Match.Groups[1].Value -le 5 -or
    [int]$Match.Groups[3].Value -le 0 -or
    [UInt64]$Match.Groups[4].Value -eq 0) {
  throw "Eightvo Find snippet-context result is incomplete: $PassLine"
}
if (!(Test-Path -LiteralPath $Bmp -PathType Leaf) -or
    (Get-Item -LiteralPath $Bmp).Length -le 54) {
  throw "missing rendered Eightvo evidence: $Bmp"
}
Add-Type -AssemblyName System.Drawing
$Image = [System.Drawing.Image]::FromFile($Bmp)
try { $Image.Save($Png, [System.Drawing.Imaging.ImageFormat]::Png) }
finally { $Image.Dispose() }

$ReaderViewCommitPath =
  Join-Path $Root "vendor\readerview0_dependency\COMMIT"
$ReaderViewCommit =
  (Get-Content -Raw -LiteralPath $ReaderViewCommitPath).Trim()
$Summary = [pscustomobject]@{
  generated_at=(Get-Date).ToString("o")
  status="pass"
  git_head=(& git -C $Root rev-parse HEAD).Trim()
  git_status=@(& git -C $Root status --porcelain --untracked-files=all)
  readerview0_commit=$ReaderViewCommit
  executable=@{ path=$Exe; sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Exe).Hash }
  book=@{ path=$Book; sha256=$BookHash; size=(Get-Item $Book).Length }
  viewport=@{ width=1400; height=780 }
  theme="light"
  typography=@{ text_size=3; line_spacing=0; font_family=2 }
  query="Paran"
  active_index=0
  visible_bytes=[int]$Match.Groups[1].Value
  remapped_match_start=[int]$Match.Groups[2].Value
  match_size=5
  highlight_draws=[int]$Match.Groups[3].Value
  highlight_pixels=[UInt64]$Match.Groups[4].Value
  bmp=$Bmp
  screenshot=$Png
  log=$Log
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 6 |
  Set-Content -Encoding ASCII -LiteralPath $SummaryPath
Write-Host "win32_eightvo_find_snippet_context_smoke result=pass summary=$SummaryPath"
