param(
  [string]$OutDir = "local\win32_pdf_stage1_smoke"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\8vo.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing 8vo executable: $Exe"
}

$Out = Join-Path $Root $OutDir
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Pdf = Join-Path $Out "stage1.pdf"
$Epub = Join-Path $Out "stage1.epub"
$InvalidPdf = Join-Path $Out "missing.pdf"
$InvalidEpub = Join-Path $Out "missing.epub"
$LateFailureEpub = Join-Path $Out "late-failure.epub"

function Write-DeterministicPdf {
  param([Parameter(Mandatory = $true)][string]$Path)

  $page1 = @"
1 1 1 rg 0 0 300 400 re f
1 0 0 rg 30 40 120 80 re f
0 0 0 rg BT /F1 20 Tf 30 360 Td (Stage 1 PDF) Tj ET
0 0 0 rg BT /F1 11 Tf 30 335 Td (Text, vector color, image, and link fixture) Tj ET
q 60 0 0 60 200 270 cm /Im1 Do Q
"@.Trim()
  $page2 = @"
0.92 0.96 1 rg 0 0 300 400 re f
0 0.25 0.75 rg 35 90 230 170 re f
1 1 1 rg BT /F1 24 Tf 55 185 Td (Page Two) Tj ET
"@.Trim()
  $page3 = @"
1 0.96 0.88 rg 0 0 300 400 re f
0.1 0.55 0.2 rg 45 75 210 220 re f
1 1 1 rg BT /F1 24 Tf 70 190 Td (Page Three) Tj ET
"@.Trim()
  $imageHex = "FF000000FF000000FFFFFFFF00>"
  $objects = @(
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R 6 0 R 8 0 R] /Count 3 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 400] /Resources << /Font << /F1 4 0 R >> /XObject << /Im1 11 0 R >> >> /Contents 5 0 R /Annots [10 0 R] >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    "<< /Length $([Text.Encoding]::ASCII.GetByteCount($page1)) >>`nstream`n$page1`nendstream",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 400] /Resources << /Font << /F1 4 0 R >> >> /Contents 7 0 R >>",
    "<< /Length $([Text.Encoding]::ASCII.GetByteCount($page2)) >>`nstream`n$page2`nendstream",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 800 400] /Resources << /Font << /F1 4 0 R >> >> /Contents 9 0 R >>",
    "<< /Length $([Text.Encoding]::ASCII.GetByteCount($page3)) >>`nstream`n$page3`nendstream",
    "<< /Type /Annot /Subtype /Link /Rect [30 40 150 120] /Border [0 0 1] /A << /S /URI /URI (https://example.invalid/stage1) >> >>",
    "<< /Type /XObject /Subtype /Image /Width 2 /Height 2 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /ASCIIHexDecode /Length $([Text.Encoding]::ASCII.GetByteCount($imageHex)) >>`nstream`n$imageHex`nendstream"
  )

  $encoding = [Text.Encoding]::ASCII
  $builder = [Text.StringBuilder]::new()
  [void]$builder.Append("%PDF-1.7`n")
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
    [void]$builder.Append(($offsets[$index].ToString("0000000000") + " 00000 n `n"))
  }
  [void]$builder.Append("trailer`n<< /Size $($objects.Count + 1) /Root 1 0 R >>`n")
  [void]$builder.Append("startxref`n$xrefOffset`n%%EOF`n")
  [IO.File]::WriteAllBytes($Path, $encoding.GetBytes($builder.ToString()))
}

function Write-ZipTextEntry {
  param(
    [IO.Compression.ZipArchive]$Zip,
    [string]$Name,
    [string]$Text,
    [IO.Compression.CompressionLevel]$Level = [IO.Compression.CompressionLevel]::Optimal
  )
  $entry = $Zip.CreateEntry($Name, $Level)
  $stream = $entry.Open()
  try {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    $stream.Write($bytes, 0, $bytes.Length)
  } finally {
    $stream.Dispose()
  }
}

function Write-DeterministicEpub {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [switch]$OmitSpineResource
  )
  Add-Type -AssemblyName System.IO.Compression
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
  $zip = [IO.Compression.ZipFile]::Open(
    $Path, [IO.Compression.ZipArchiveMode]::Create)
  try {
    Write-ZipTextEntry $zip "mimetype" "application/epub+zip" `
      ([IO.Compression.CompressionLevel]::NoCompression)
    Write-ZipTextEntry $zip "META-INF/container.xml" @"
<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>
"@
    Write-ZipTextEntry $zip "OEBPS/content.opf" @"
<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="bookid">octavo-pdf-stage1</dc:identifier><dc:title>PDF replacement fixture</dc:title><dc:language>en</dc:language></metadata><manifest><item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>
"@
    $paragraphs = (1..24 | ForEach-Object {
      "<p>EPUB replacement paragraph $_ verifies that the existing reflow reader remains intact.</p>"
    }) -join "`n"
    if (!$OmitSpineResource) {
      Write-ZipTextEntry $zip "OEBPS/chapter.xhtml" "<html xmlns=`"http://www.w3.org/1999/xhtml`"><head><title>Replacement</title></head><body><h1>EPUB replacement</h1>$paragraphs</body></html>"
    }
  } finally {
    $zip.Dispose()
  }
}

Write-DeterministicPdf $Pdf
Write-DeterministicEpub $Epub
Write-DeterministicEpub -Path $LateFailureEpub -OmitSpineResource
foreach ($missing in @($InvalidPdf, $InvalidEpub)) {
  if (Test-Path -LiteralPath $missing) {
    Remove-Item -LiteralPath $missing -Force
  }
}

$passes = @()
for ($run = 1; $run -le 2; $run++) {
  $Bmp = Join-Path $Out "stage1-$run.bmp"
  if (Test-Path -LiteralPath $Bmp) {
    Remove-Item -LiteralPath $Bmp -Force
  }
  if (Test-Path -LiteralPath $Bmp) {
    throw "could not remove stale PDF smoke bitmap: $Bmp"
  }
  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = & $Exe --pdf-stage1-smoke $Pdf $Epub $InvalidPdf `
      $InvalidEpub $LateFailureEpub $Bmp 2>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
  }
  if ($exitCode -ne 0) {
    $output | Write-Host
    throw "8vo PDF Stage 1 smoke run $run failed with exit code $exitCode"
  }
  $pass = $output | Where-Object {
    $_ -match '^octavo_pdf_stage1_smoke result=pass '
  } | Select-Object -Last 1
  if (!$pass -or !(Test-Path -LiteralPath $Bmp -PathType Leaf)) {
    $output | Write-Host
    throw "8vo PDF Stage 1 smoke run $run did not produce pass evidence"
  }
  if ([string]$pass -notmatch
      'landscape8k=5792x2896 landscape_bytes=67094528 retry=resize,navigate') {
    $output | Write-Host
    throw "8vo PDF Stage 1 smoke run $run lacks bounded 8K/retry evidence"
  }
  $passes += [pscustomobject]@{
    Line = [string]$pass
    Hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Bmp).Hash.ToLowerInvariant()
  }
}

if ($passes[0].Hash -ne $passes[1].Hash) {
  throw "8vo PDF Stage 1 bitmap is not deterministic across identical runs"
}
$passes[1].Line | Write-Host
Write-Host "win32_octavo_pdf_stage1_smoke result=pass repeat=2 bmp_sha256=$($passes[1].Hash) fixture=$Pdf"
