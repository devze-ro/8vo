[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$DependencyRoot = Join-Path $RepoRoot "local\dependencies"

$Dependencies = @(
  [pscustomobject]@{
    Name = "ground0"
    Repository = "https://github.com/devze-ro/ground0.git"
    Environment = "OCTAVO_GROUND0_DIR"
  },
  [pscustomobject]@{
    Name = "ui0"
    Repository = "https://github.com/devze-ro/ui0.git"
    Environment = "OCTAVO_UI0_DIR"
  },
  [pscustomobject]@{
    Name = "readerview0"
    Repository = "https://github.com/devze-ro/readerview0.git"
    Environment = "OCTAVO_READERVIEW0_DIR"
  },
  [pscustomobject]@{
    Name = "reader0"
    Repository = "https://github.com/devze-ro/reader0.git"
    Environment = "OCTAVO_READER0_DIR"
  }
)

function Invoke-Git {
  param([string[]]$Arguments)

  $Output = @(& git @Arguments)
  if ($LASTEXITCODE -ne 0) {
    throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
  }
  return $Output
}

if (!(Get-Command git -ErrorAction SilentlyContinue)) {
  throw "Git is required to bootstrap 8vo dependencies."
}

New-Item -ItemType Directory -Force -Path $DependencyRoot | Out-Null

foreach ($Dependency in $Dependencies) {
  $Metadata = Join-Path $RepoRoot "vendor\$($Dependency.Name)_dependency"
  $CommitPath = Join-Path $Metadata "COMMIT"
  if (!(Test-Path -LiteralPath $CommitPath -PathType Leaf)) {
    throw "$($Dependency.Name) commit metadata is missing: $CommitPath"
  }

  $RequiredCommit = (Get-Content -Raw -LiteralPath $CommitPath).Trim()
  if ($RequiredCommit -notmatch "^[0-9a-f]{40}$") {
    throw "$($Dependency.Name) commit metadata is not a full lowercase Git commit."
  }

  $Checkout = Join-Path $DependencyRoot $Dependency.Name
  if (!(Test-Path -LiteralPath $Checkout -PathType Container)) {
    Write-Host "Cloning $($Dependency.Name) into $Checkout"
    Invoke-Git @(
      "clone",
      "--filter=blob:none",
      $Dependency.Repository,
      $Checkout
    ) | Out-Host
  }

  $InsideWorkTree = (Invoke-Git @(
    "-C", $Checkout, "rev-parse", "--is-inside-work-tree"
  ) | Select-Object -First 1).Trim()
  if ($InsideWorkTree -ne "true") {
    throw "$Checkout is not a Git working tree."
  }

  $Origin = (Invoke-Git @(
    "-C", $Checkout, "remote", "get-url", "origin"
  ) | Select-Object -First 1).Trim()
  $ExpectedRepository = "devze-ro/$($Dependency.Name)"
  if ($Origin -notmatch "(?i)(^|[:/])$([regex]::Escape($ExpectedRepository))(\.git)?$") {
    throw "$($Dependency.Name) origin is unexpected: $Origin"
  }

  $DirtyBefore = @(Invoke-Git @(
    "-C", $Checkout, "status", "--porcelain", "--untracked-files=all"
  ))
  if ($DirtyBefore.Count -ne 0) {
    Write-Host "$($Dependency.Name) working tree is dirty:"
    $DirtyBefore | Select-Object -First 40 | Out-Host
    throw "Refusing to replace files in a dirty dependency checkout."
  }

  & git -C $Checkout cat-file -e "$RequiredCommit`^{commit}" 2>$null
  if ($LASTEXITCODE -ne 0) {
    Write-Host "Fetching the pinned $($Dependency.Name) revision"
    Invoke-Git @(
      "-C", $Checkout, "fetch", "--no-tags", "origin", $RequiredCommit
    ) | Out-Host
  }

  $CurrentCommit = (Invoke-Git @(
    "-C", $Checkout, "rev-parse", "HEAD"
  ) | Select-Object -First 1).Trim()
  if ($CurrentCommit -ne $RequiredCommit) {
    Invoke-Git @(
      "-C", $Checkout, "checkout", "--detach", "--quiet", $RequiredCommit
    ) | Out-Host
  }

  $VerifiedCommit = (Invoke-Git @(
    "-C", $Checkout, "rev-parse", "HEAD"
  ) | Select-Object -First 1).Trim()
  $DirtyAfter = @(Invoke-Git @(
    "-C", $Checkout, "status", "--porcelain", "--untracked-files=all"
  ))
  if ($VerifiedCommit -ne $RequiredCommit) {
    throw "$($Dependency.Name) checkout did not reach the required commit."
  }
  if ($DirtyAfter.Count -ne 0) {
    throw "$($Dependency.Name) checkout is dirty after bootstrap."
  }

  [Environment]::SetEnvironmentVariable(
    $Dependency.Environment,
    $Checkout,
    "Process")
  Write-Host "$($Dependency.Name) ready at $VerifiedCommit"
}

& powershell -NoProfile -ExecutionPolicy Bypass `
  -File (Join-Path $PSScriptRoot "require_dependencies_current.ps1")
if ($LASTEXITCODE -ne 0) {
  throw "8vo dependency verification failed with exit code $LASTEXITCODE"
}

Write-Host "8vo dependencies are bootstrapped under $DependencyRoot"
