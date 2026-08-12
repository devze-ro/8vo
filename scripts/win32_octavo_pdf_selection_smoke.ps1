param(
  [string]$OutDir = "local\win32_pdf_selection_smoke"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\8vo.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing 8vo executable: $Exe"
}

$Out = Join-Path $Root $OutDir
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Pdf = Join-Path $Out "selection.pdf"
$ReplacementPdf = Join-Path $Out "selection-replacement.pdf"

function Write-DeterministicSelectionPdf {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$Page2Label = "Link destination page"
  )

  $selectionLines = [Collections.Generic.List[string]]::new()
  $selectionLines.Add('Alpha caf\351 selection')
  $selectionLines.Add('Second line copy')
  for ($row = 1; $row -le 18; $row++) {
    $selectionLines.Add(
      ('Selection copy row {0:D2} owns deterministic counted text for exact clipboard verification.' -f $row))
  }
  $page1Commands = [Collections.Generic.List[string]]::new()
  $page1Commands.Add('BT')
  $page1Commands.Add('/F1 9 Tf')
  $page1Commands.Add('40 460 Td')
  for ($index = 0; $index -lt $selectionLines.Count; $index++) {
    if ($index -gt 0) { $page1Commands.Add('0 -16 Td') }
    $page1Commands.Add('(' + $selectionLines[$index] + ') Tj')
  }
  $page1Commands.Add('ET')
  $page1 = $page1Commands -join "`n"
  $page2 = @"
BT
/F1 20 Tf
40 450 Td
($Page2Label) Tj
ET
"@.Trim()
  $objects = @(
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 500] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R /Annots [8 0 R] >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 500] /Resources << /Font << /F1 5 0 R >> >> /Contents 7 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
    "<< /Length $([Text.Encoding]::ASCII.GetByteCount($page1)) >>`nstream`n$page1`nendstream",
    "<< /Length $([Text.Encoding]::ASCII.GetByteCount($page2)) >>`nstream`n$page2`nendstream",
    "<< /Type /Annot /Subtype /Link /Rect [38 445 190 475] /Border [0 0 0] /A << /S /GoTo /D [4 0 R /Fit] >> >>"
  )

  $encoding = [Text.Encoding]::ASCII
  $builder = [Text.StringBuilder]::new()
  [void]$builder.Append("%PDF-1.7`n%8vo-selection`n")
  $offsets = [Collections.Generic.List[int]]::new()
  $offsets.Add(0)
  for ($index = 0; $index -lt $objects.Count; $index++) {
    $offsets.Add($encoding.GetByteCount($builder.ToString()))
    [void]$builder.Append("$($index + 1) 0 obj`n$($objects[$index])`nendobj`n")
  }
  $xrefOffset = $encoding.GetByteCount($builder.ToString())
  [void]$builder.Append("xref`n0 $($objects.Count + 1)`n")
  [void]$builder.Append("0000000000 65535 f `n")
  for ($index = 1; $index -lt $offsets.Count; $index++) {
    [void]$builder.Append(
      $offsets[$index].ToString("0000000000") + " 00000 n `n")
  }
  [void]$builder.Append(
    "trailer`n<< /Size $($objects.Count + 1) /Root 1 0 R >>`n")
  [void]$builder.Append("startxref`n$xrefOffset`n%%EOF`n")
  [IO.File]::WriteAllBytes($Path, $encoding.GetBytes($builder.ToString()))
}

Write-DeterministicSelectionPdf -Path $Pdf
Write-DeterministicSelectionPdf `
  -Path $ReplacementPdf `
  -Page2Label "Replacement destination page"
$outputs = @()
for ($run = 1; $run -le 2; $run++) {
  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = & $Exe --pdf-selection-smoke $Pdf $ReplacementPdf 2>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
  }
  $text = ($output | ForEach-Object { $_.ToString() }) -join "`n"
  if ($exitCode -ne 0) {
    Write-Host $text
    throw "8vo PDF selection smoke run $run failed with exit code $exitCode"
  }
  $pass = @($output | Where-Object {
    $_ -match '^octavo_pdf_selection_smoke result=pass '
  })
  if ($pass.Count -ne 1) {
    Write-Host $text
    throw "8vo PDF selection smoke run $run did not publish one pass record"
  }
  $outputs += $pass[0].ToString()
}
if ($outputs[0] -ne $outputs[1]) {
  throw "8vo PDF selection smoke output was not deterministic"
}

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Pdf).Hash.ToLowerInvariant()
$replacementHash = (Get-FileHash -Algorithm SHA256 `
  -LiteralPath $ReplacementPdf).Hash.ToLowerInvariant()
Write-Host "$($outputs[1]) repeat=2 fixture_sha256=$hash replacement_sha256=$replacementHash fixture=$Pdf replacement=$ReplacementPdf"
