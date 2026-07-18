$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\lectern0.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing lectern0 executable: $Exe"
}

$output = & $Exe --reader-view-startup-interaction-smoke 2>&1
if ($LASTEXITCODE -ne 0) {
  $output | Write-Host
  throw "lectern0 startup interaction smoke failed with exit code $LASTEXITCODE"
}
$line = $output | Where-Object {
  $_ -match '^lectern0_reader_view_startup_interaction result=pass '
} | Select-Object -Last 1
if (!$line -or
    $line -notmatch 'document=empty' -or
    $line -notmatch 'lifecycle=press_release' -or
    $line -notmatch 'capture=cancel_and_release' -or
    $line -notmatch 'action=open' -or
    $line -notmatch 'dialog=not_invoked' -or
    $line -notmatch 'status_owner=reader_view' -or
    $line -notmatch 'exit_pointer=armed_release' -or
    $line -notmatch 'exit_keyboard=tab_activate' -or
    $line -notmatch 'exit_focus=icon_ring') {
  $output | Write-Host
  throw "lectern0 startup interaction smoke did not report the required contract"
}

Write-Host $line
Write-Host "win32_lectern0_reader_view_startup_interaction_smoke result=pass"
