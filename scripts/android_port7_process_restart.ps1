param(
  [ValidateNotNullOrEmpty()]
  [string]$Serial = "emulator-5554"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$TargetPackage = "ro.devze.octavo"
$TestRunner =
  "ro.devze.octavo.test/androidx.test.runner.AndroidJUnitRunner"
$ProbeClass = "ro.devze.octavo.OctavoProcessRestartTest"

function Resolve-AdbPath {
  foreach ($environmentName in @("ANDROID_SDK_ROOT", "ANDROID_HOME")) {
    $sdkRoot = [Environment]::GetEnvironmentVariable($environmentName)
    if (!$sdkRoot) { continue }
    $candidate = Join-Path $sdkRoot "platform-tools\adb.exe"
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
      return (Resolve-Path -LiteralPath $candidate).Path
    }
  }

  $command = Get-Command adb.exe -ErrorAction SilentlyContinue
  if (!$command) {
    $command = Get-Command adb -ErrorAction SilentlyContinue
  }
  if ($command) { return $command.Source }
  throw "adb was not found; set ANDROID_SDK_ROOT/ANDROID_HOME or add adb to PATH"
}

function Invoke-AdbChecked {
  param(
    [string[]]$AdbArguments,
    [string]$FailureMessage
  )

  $output = @(& $AdbPath -s $Serial @AdbArguments 2>&1)
  if ($LASTEXITCODE -ne 0) {
    $detail = ($output -join [Environment]::NewLine).Trim()
    throw "$FailureMessage (adb exit $LASTEXITCODE): $detail"
  }
  return $output
}

function Invoke-ProbeHalf {
  param(
    [string]$MethodName,
    [string]$Label
  )

  $probe = "$ProbeClass#$MethodName"
  $arguments = @(
    "shell", "am", "instrument", "-w", "-r", "-e", "class",
    $probe, $TestRunner
  )
  $invocation = @{
    AdbArguments = $arguments
    FailureMessage = "$Label instrumentation did not complete"
  }
  $output = @(Invoke-AdbChecked @invocation)
  $text = $output -join [Environment]::NewLine
  Write-Host $text
  $failed = $text -match "(?m)^FAILURES!!!\s*$"
  $failed = $failed -or $text -match "INSTRUMENTATION_FAILED"
  $failed = $failed -or $text -notmatch "(?m)^OK \(1 test\)\s*$"
  $failed = $failed -or $text -notmatch "(?m)^INSTRUMENTATION_CODE: -1\s*$"
  if ($failed) {
    throw "$Label instrumentation did not report one passing test"
  }
}

$AdbPath = Resolve-AdbPath
$deviceInvocation = @{
  AdbArguments = @("get-state")
  FailureMessage = "Unable to query $Serial"
}
$deviceOutput = Invoke-AdbChecked @deviceInvocation
$deviceState = ($deviceOutput -join "").Trim()
if ($deviceState -ne "device") {
  throw "Android target $Serial is not ready: $deviceState"
}

Write-Host "Seeding durable Port 7 reader state on $Serial"
Invoke-ProbeHalf "seedDurableReaderState" "Process-restart seed"

Write-Host "Force-stopping $TargetPackage"
$forceStopArguments = @("shell", "am", "force-stop", $TargetPackage)
$forceStopInvocation = @{
  AdbArguments = $forceStopArguments
  FailureMessage = "Unable to force-stop $TargetPackage"
}
[void](Invoke-AdbChecked @forceStopInvocation)

$stopped = $false
$pidText = ""
for ($attempt = 0; $attempt -lt 20; ++$attempt) {
  $pidOutput = @(
    & $AdbPath -s $Serial shell pidof $TargetPackage 2>&1
  )
  $pidText = ($pidOutput -join [Environment]::NewLine).Trim()
  if ([string]::IsNullOrWhiteSpace($pidText)) {
    $stopped = $true
    break
  }
  Start-Sleep -Milliseconds 100
}
if (!$stopped) {
  throw "$TargetPackage still has a live process after force-stop: $pidText"
}
Write-Host "Confirmed that $TargetPackage has no surviving process"

Write-Host "Verifying durable state in a fresh process on $Serial"
Invoke-ProbeHalf "verifyDurableReaderStateAfterRestart" "Process-restart verify"

$result = "android_port7_process_restart result=pass serial=$Serial"
Write-Host "$result seed=1 force_stop=confirmed verify=1"
