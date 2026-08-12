param(
  [string]$OutDir = "local\win32_pdf_content_smoke"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\8vo.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing 8vo executable: $Exe"
}

$Out = Join-Path $Root $OutDir
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Pdf = Join-Path $Out "content.pdf"

function Write-DeterministicContentPdf {
  param([Parameter(Mandatory = $true)][string]$Path)

  $page1 = @"
BT
/F1 18 Tf
36 150 Td
(Needle first needle second) Tj
ET
"@.Trim()
  $page2 = @"
BT
/F1 18 Tf
36 150 Td
(Needle third destination) Tj
ET
"@.Trim()
  $objects = @(
    "<< /Type /Catalog /Pages 2 0 R /Outlines 10 0 R /PageLabels 14 0 R /PageMode /UseOutlines >>",
    "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 700 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R /Annots [8 0 R 16 0 R 9 0 R 15 0 R] >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 700 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 7 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
    "<< /Length $([Text.Encoding]::ASCII.GetByteCount($page1)) >>`nstream`n$page1`nendstream",
    "<< /Length $([Text.Encoding]::ASCII.GetByteCount($page2)) >>`nstream`n$page2`nendstream",
    "<< /Type /Annot /Subtype /Link /Rect [20 20 160 45] /Border [0 0 0] /A << /S /GoTo /D [4 0 R /Fit] >> >>",
    "<< /Type /Annot /Subtype /Link /Rect [180 20 360 45] /Border [0 0 0] /A << /S /URI /URI (https://example.com/8vo) >> >>",
    "<< /Type /Outlines /First 11 0 R /Last 13 0 R /Count 3 >>",
    "<< /Title (Chapter One) /Parent 10 0 R /Dest [3 0 R /Fit] /First 12 0 R /Last 12 0 R /Count 1 /Next 13 0 R >>",
    "<< /Title (Section Two) /Parent 11 0 R /Dest [4 0 R /Fit] >>",
    "<< /Title (Appendix) /Parent 10 0 R /Prev 11 0 R /Dest [4 0 R /Fit] >>",
    "<< /Nums [0 << /S /r >> 1 << /P (A-) /S /D /St 1 >>] >>",
    "<< /Type /Annot /Subtype /Link /Rect [380 20 560 45] /Border [0 0 0] /A << /S /URI /URI (file:///C:/private/book.pdf) >> >>",
    "<< /Type /Annot /Subtype /Link /Rect [20 60 160 85] /Border [0 0 0] /A << /S /GoTo /D [4 0 R /XYZ 40 80 2] >> >>"
  )

  $encoding = [Text.Encoding]::ASCII
  $builder = [Text.StringBuilder]::new()
  [void]$builder.Append("%PDF-1.7`n%8vo-content`n")
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

Write-DeterministicContentPdf -Path $Pdf
$outputs = @()
for ($run = 1; $run -le 2; $run++) {
  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = & $Exe --pdf-content-smoke $Pdf 2>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
  }
  $text = ($output | ForEach-Object { $_.ToString() }) -join "`n"
  if ($exitCode -ne 0) {
    Write-Host $text
    throw "8vo PDF content smoke run $run failed with exit code $exitCode"
  }
  $pass = @($output | Where-Object {
    $_ -match '^octavo_pdf_content_smoke result=pass '
  })
  if ($pass.Count -ne 1) {
    Write-Host $text
    throw "8vo PDF content smoke run $run did not publish one pass record"
  }
  $outputs += $pass[0].ToString()
}
if ($outputs[0] -ne $outputs[1]) {
  throw "8vo PDF content smoke output was not deterministic"
}

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Pdf).Hash.ToLowerInvariant()
Write-Host "$($outputs[1]) repeat=2 fixture_sha256=$hash fixture=$Pdf"
