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
$TargetActivity = "ro.devze.octavo/.OctavoActivity"
$UiDumpPath = "/sdcard/octavo-process-restart.xml"

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

function Get-TargetProcessId {
  $output = @(& $AdbPath -s $Serial shell pidof $TargetPackage 2>&1)
  return ($output -join [Environment]::NewLine).Trim()
}

function Require-TargetProcessRunning {
  $processId = ""
  for ($attempt = 0; $attempt -lt 40; ++$attempt) {
    $processId = Get-TargetProcessId
    if (![string]::IsNullOrWhiteSpace($processId)) {
      Write-Host "Confirmed live $TargetPackage process: $processId"
      return
    }
    Start-Sleep -Milliseconds 100
  }
  throw "$TargetPackage has no live process before the external force-stop"
}

function Start-TargetActivity {
  $invocation = @{
    AdbArguments = @("shell", "am", "start", "-W", "-n", $TargetActivity)
    FailureMessage = "Unable to launch $TargetActivity"
  }
  [void](Invoke-AdbChecked @invocation)
  Require-TargetProcessRunning
}

function Get-UiHierarchy {
  $dumpInvocation = @{
    AdbArguments = @("shell", "uiautomator", "dump", $UiDumpPath)
    FailureMessage = "Unable to capture the 8vo UI hierarchy"
  }
  [void](Invoke-AdbChecked @dumpInvocation)
  $readInvocation = @{
    AdbArguments = @("shell", "cat", $UiDumpPath)
    FailureMessage = "Unable to read the 8vo UI hierarchy"
  }
  $lines = @(Invoke-AdbChecked @readInvocation)
  $text = ($lines -join [Environment]::NewLine).Trim()
  if (!$text.StartsWith("<?xml", [StringComparison]::Ordinal)) {
    throw "The 8vo UI hierarchy is not XML"
  }
  return [xml]$text
}

function Wait-UiNode {
  param(
    [string]$ExactText,
    [string]$FailureMessage
  )

  for ($attempt = 0; $attempt -lt 12; ++$attempt) {
    $hierarchy = Get-UiHierarchy
    foreach ($node in $hierarchy.SelectNodes("//node")) {
      if ($node.GetAttribute("text") -ceq $ExactText) {
        return $node
      }
    }
    Start-Sleep -Milliseconds 250
  }
  throw $FailureMessage
}

function Invoke-UiClick {
  param(
    [string]$ExactText,
    [string]$ExpectedText
  )

  $node = Wait-UiNode `
    $ExactText `
    "The expected 8vo action was not visible: $ExactText"
  $bounds = $node.GetAttribute("bounds")
  if ($bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
    throw "The expected 8vo action has invalid bounds: $bounds"
  }
  $left = [int]$Matches[1]
  $top = [int]$Matches[2]
  $right = [int]$Matches[3]
  $bottom = [int]$Matches[4]
  if ($right -le $left -or $bottom -le $top) {
    throw "The expected 8vo action has empty bounds: $bounds"
  }
  $x = $left + [int](($right - $left) / 2)
  $y = $top + [int](($bottom - $top) / 2)
  $tapInvocation = @{
    AdbArguments = @("shell", "input", "tap", "$x", "$y")
    FailureMessage = "Unable to activate the 8vo action: $ExactText"
  }
  [void](Invoke-AdbChecked @tapInvocation)
  for ($attempt = 0; $attempt -lt 3; ++$attempt) {
    $hierarchy = Get-UiHierarchy
    foreach ($candidate in $hierarchy.SelectNodes("//node")) {
      if ($candidate.GetAttribute("text") -ceq $ExpectedText) {
        Write-Host "Confirmed visible 8vo UI: $ExpectedText"
        return
      }
    }
    Start-Sleep -Milliseconds 250
  }

  $focusedAction = $null
  foreach ($candidate in $hierarchy.SelectNodes("//node")) {
    if ($candidate.GetAttribute("text") -ceq $ExactText) {
      $focusedAction = $candidate
      break
    }
  }
  if (($null -eq $focusedAction) -or
      ($focusedAction.GetAttribute("focused") -cne "true")) {
    throw "The expected 8vo action neither opened nor retained focus: $ExactText"
  }
  $enterInvocation = @{
    AdbArguments = @("shell", "input", "keyevent", "KEYCODE_ENTER")
    FailureMessage = "Unable to activate the focused 8vo action: $ExactText"
  }
  [void](Invoke-AdbChecked @enterInvocation)
  [void](Wait-UiNode `
    $ExpectedText `
    "The expected 8vo UI did not appear: $ExpectedText")
  Write-Host "Confirmed visible 8vo UI: $ExpectedText"
}

function Invoke-UiBack {
  param([string]$ExpectedText)

  $backInvocation = @{
    AdbArguments = @("shell", "input", "keyevent", "KEYCODE_BACK")
    FailureMessage = "Unable to send Back to 8vo"
  }
  [void](Invoke-AdbChecked @backInvocation)
  [void](Wait-UiNode `
    $ExpectedText `
    "The expected 8vo UI did not appear after Back: $ExpectedText")
  Write-Host "Confirmed visible 8vo UI after Back: $ExpectedText"
}

function Confirm-TargetProcessStopped {
  param([switch]$RequireLive)

  if ($RequireLive) {
    Require-TargetProcessRunning
  }
  Write-Host "Force-stopping $TargetPackage"
  $forceStopInvocation = @{
    AdbArguments = @("shell", "am", "force-stop", $TargetPackage)
    FailureMessage = "Unable to force-stop $TargetPackage"
  }
  [void](Invoke-AdbChecked @forceStopInvocation)

  $stopped = $false
  $processId = ""
  for ($attempt = 0; $attempt -lt 20; ++$attempt) {
    $processId = Get-TargetProcessId
    if ([string]::IsNullOrWhiteSpace($processId)) {
      $stopped = $true
      break
    }
    Start-Sleep -Milliseconds 100
  }
  if (!$stopped) {
    throw "$TargetPackage still has a live process after force-stop: $processId"
  }
  Write-Host "Confirmed that $TargetPackage has no surviving process"
}

function Invoke-RestartBoundary {
  param(
    [string]$SeedMethod,
    [string]$VerifyMethod,
    [string]$StateLabel
  )

  Write-Host "Seeding durable $StateLabel state on $Serial"
  Invoke-ProbeHalf $SeedMethod "$StateLabel process-restart seed"
  Confirm-TargetProcessStopped
  Write-Host "Verifying durable $StateLabel state in a fresh process on $Serial"
  Invoke-ProbeHalf $VerifyMethod "$StateLabel process-restart verify"
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

Invoke-RestartBoundary `
  "seedDurableReaderState" `
  "verifyDurableReaderStateAfterRestart" `
  "Port 7 reader"

Write-Host "Seeding durable Port 11 Library membership state on $Serial"
Invoke-ProbeHalf `
  "seedDurableLibraryMembershipState" `
  "Port 11 Library membership process-restart seed"
Start-TargetActivity
Invoke-UiClick `
  "Withdraw from synchronized Library" `
  "Withdraw from synchronized Library?"
Confirm-TargetProcessStopped -RequireLive

Write-Host "Verifying confirmation loss and staging durable membership review"
Invoke-ProbeHalf `
  "verifyLibraryMembershipAfterConfirmationRestartAndStageReview" `
  "Port 11 Library membership confirmation-restart verification"
Start-TargetActivity
[void](Wait-UiNode `
  "Review synchronized Library membership" `
  "The retained membership review did not reopen")
Invoke-UiBack "Review pending membership attention"
Confirm-TargetProcessStopped -RequireLive

Write-Host "Verifying durable membership review in a second fresh process"
Invoke-ProbeHalf `
  "verifyDurableLibraryMembershipStateAfterRestart" `
  "Port 11 Library membership retained-review verification"

$result = "android_port7_process_restart result=pass serial=$Serial"
Write-Host "$result seed=1 force_stop=confirmed verify=1 membership_seed=1 membership_confirmation=visible membership_force_stop=confirmed membership_stage=1 membership_deferred=visible membership_second_force_stop=confirmed membership_verify=1"
