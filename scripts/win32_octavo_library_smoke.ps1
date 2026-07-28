param(
  [string]$OutDir = "local\validation\library-v1",
  [Parameter(Mandatory = $true)][string]$BookPath,
  [string]$ExePath = "build\win32\8vo.exe",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ExpectedBookSha256 =
  "D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9"
$ExpectedBookSize = 955125
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
if (!(Test-Path -LiteralPath $BookPath -PathType Leaf)) {
  throw "missing GOTM EPUB: $BookPath"
}
$Book = (Resolve-Path -LiteralPath $BookPath).Path
$BookItem = Get-Item -LiteralPath $Book
$BookHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Book).Hash
if ($BookHash -ne $ExpectedBookSha256 -or $BookItem.Length -ne $ExpectedBookSize) {
  throw "GOTM EPUB identity mismatch: expected=$ExpectedBookSha256/$ExpectedBookSize actual=$BookHash/$($BookItem.Length) path=$Book"
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
      throw "strict Octavo build failed with exit code $LASTEXITCODE"
    }
  } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing Octavo executable: $Exe"
}

function Invoke-LibrarySmoke {
  param([string]$Name)
  $RunDir = Join-Path $Out $Name
  New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
  $Prefix = Join-Path $RunDir "library"
  $Log = Join-Path $RunDir "run.log"
  & $Exe --library-smoke $Book $Prefix *> $Log
  if ($LASTEXITCODE -ne 0) {
    $Tail = (Get-Content -LiteralPath $Log -Tail 100) -join "`n"
    throw "Octavo library smoke failed`n$Tail"
  }
  $PassLine = Get-Content -LiteralPath $Log | Where-Object {
    $_ -match '^octavo_library_smoke result=pass '
  } | Select-Object -Last 1
  foreach ($Token in @(
    "catalog=bounded_atomic_v1", "ordering=mru", "migration=legacy_state",
    "metadata=title_author", "cover=first_library_frame_reused_on_open",
    "progress=canonical",
    "thumbnail=area_v2",
    "close=library", "restart=persisted",
    "interaction=pointer_keyboard_card_open_arrow", "states=idle_hover_pressed",
    "missing=locate_remove",
    "remove=source_preserved", "responsive=wide_and_compact",
    "accessibility=host_semantics", "digest=reserved_none")) {
    if (!$PassLine -or $PassLine -notmatch [regex]::Escape($Token)) {
      throw "library result is incomplete: missing $Token in $PassLine"
    }
  }
  $Evidence = @{}
  foreach ($State in @("empty", "populated", "restart", "restart_repeat",
                        "compact", "missing", "hover", "pressed")) {
    $Path = "${Prefix}_${State}.bmp"
    if (!(Test-Path -LiteralPath $Path -PathType Leaf) -or
        (Get-Item -LiteralPath $Path).Length -le 54) {
      throw "missing library evidence: $Path"
    }
    $Evidence[$State] = [pscustomobject]@{
      path=$Path
      sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
      size=(Get-Item -LiteralPath $Path).Length
    }
  }
  if ($Evidence.restart.sha256 -ne $Evidence.restart_repeat.sha256) {
    throw "restart render is not repeatable"
  }
  [pscustomobject]@{
    pass_line=[string]$PassLine
    evidence=$Evidence
    log=$Log
  }
}

$First = Invoke-LibrarySmoke "run_1"
$Second = Invoke-LibrarySmoke "run_2"
foreach ($State in @("empty", "populated", "restart", "restart_repeat",
                      "compact", "missing", "hover", "pressed")) {
  if ($First.evidence[$State].sha256 -ne $Second.evidence[$State].sha256) {
    throw "library evidence is not repeatable: $State"
  }
}

$Dependencies = @{}
foreach ($Name in @("reader0", "ui0", "readerview0", "ground0")) {
  $CommitPath = Join-Path $Root "vendor\${Name}_dependency\COMMIT"
  $Dependencies[$Name] = (Get-Content -Raw -LiteralPath $CommitPath).Trim()
}
$Summary = [pscustomobject]@{
  generated_at=(Get-Date).ToString("o")
  status="pass"
  git_head=(& git -C $Root rev-parse HEAD).Trim()
  git_status=@(& git -C $Root status --porcelain --untracked-files=all)
  dependencies=$Dependencies
  executable=@{
    path=$Exe
    sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Exe).Hash
  }
  book=@{ path=$Book; sha256=$BookHash; size=$BookItem.Length }
  repeat=2
  result=$Second.pass_line
  states=$Second.evidence
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 7 |
  Set-Content -Encoding ASCII -LiteralPath $SummaryPath
Write-Host "win32_octavo_library_smoke result=pass repeat=2 summary=$SummaryPath"
