$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\8vo.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing eightvo executable: $Exe"
}

$output = & $Exe --reader-view-startup-interaction-smoke 2>&1
if ($LASTEXITCODE -ne 0) {
  $output | Write-Host
  throw "eightvo startup interaction smoke failed with exit code $LASTEXITCODE"
}
$line = $output | Where-Object {
  $_ -match '^eightvo_reader_view_startup_interaction result=pass '
} | Select-Object -Last 1
if (!$line -or
    $line -notmatch 'surface=library' -or
    $line -notmatch 'catalog=empty' -or
    $line -notmatch 'lifecycle=press_release' -or
    $line -notmatch 'capture=cancel_and_release' -or
    $line -notmatch 'action=add_books' -or
    $line -notmatch 'picker=suppressed' -or
    $line -notmatch 'focus=pointer_keyboard' -or
    $line -notmatch 'accessibility=host_semantics') {
  $output | Write-Host
  throw "eightvo startup interaction smoke did not report the required contract"
}

Write-Host $line
Write-Host "win32_eightvo_reader_view_startup_interaction_smoke result=pass"
