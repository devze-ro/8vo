param(
  [string]$OutDir = "local\slice5a_image_smoke"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\8vo.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing lectern0 executable: $Exe"
}

$Out = Join-Path $Root $OutDir
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Epub = Join-Path $Out "lectern0_images.epub"
$CoverBmp = Join-Path $Out "cover.bmp"
$InlineBmp = Join-Path $Out "inline.bmp"
if (Test-Path -LiteralPath $Epub) { Remove-Item -LiteralPath $Epub -Force }

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function New-PngBytes {
  param(
    [int]$Width,
    [int]$Height,
    [System.Drawing.Color]$Background,
    [System.Drawing.Color]$Accent
  )
  $bitmap = New-Object System.Drawing.Bitmap($Width, $Height)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  try {
    $graphics.Clear($Background)
    $brush = New-Object System.Drawing.SolidBrush($Accent)
    try {
      $graphics.FillRectangle($brush,
                              [int]($Width / 6),
                              [int]($Height / 6),
                              [int]($Width * 2 / 3),
                              [int]($Height * 2 / 3))
    } finally {
      $brush.Dispose()
    }
    $stream = New-Object System.IO.MemoryStream
    try {
      $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
      return $stream.ToArray()
    } finally {
      $stream.Dispose()
    }
  } finally {
    $graphics.Dispose()
    $bitmap.Dispose()
  }
}

function Write-ZipTextEntry {
  param(
    [System.IO.Compression.ZipArchive]$Zip,
    [string]$Name,
    [string]$Text,
    [System.IO.Compression.CompressionLevel]$Level = [System.IO.Compression.CompressionLevel]::Optimal
  )
  $entry = $Zip.CreateEntry($Name, $Level)
  $stream = $entry.Open()
  try {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $stream.Write($bytes, 0, $bytes.Length)
  } finally {
    $stream.Dispose()
  }
}

function Write-ZipBytesEntry {
  param(
    [System.IO.Compression.ZipArchive]$Zip,
    [string]$Name,
    [byte[]]$Bytes
  )
  $entry = $Zip.CreateEntry($Name, [System.IO.Compression.CompressionLevel]::Optimal)
  $stream = $entry.Open()
  try {
    $stream.Write($Bytes, 0, $Bytes.Length)
  } finally {
    $stream.Dispose()
  }
}

$coverPng = New-PngBytes 180 260 ([System.Drawing.Color]::FromArgb(255, 27, 52, 93)) `
                                   ([System.Drawing.Color]::FromArgb(255, 244, 184, 96))
$inlinePng = New-PngBytes 240 120 ([System.Drawing.Color]::FromArgb(255, 228, 242, 232)) `
                                    ([System.Drawing.Color]::FromArgb(255, 42, 132, 88))

$zip = [System.IO.Compression.ZipFile]::Open($Epub, [System.IO.Compression.ZipArchiveMode]::Create)
try {
  Write-ZipTextEntry $zip "mimetype" "application/epub+zip" ([System.IO.Compression.CompressionLevel]::NoCompression)
  Write-ZipTextEntry $zip "META-INF/container.xml" @"
<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
"@
  Write-ZipTextEntry $zip "OEBPS/content.opf" @"
<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">lectern0-image-slice5a</dc:identifier>
    <dc:title>Lectern0 Image Slice 5A</dc:title>
    <dc:language>en</dc:language>
    <meta name="cover" content="cover-image"/>
  </metadata>
  <manifest>
    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
    <item id="cover-image" href="images/cover.png" media-type="image/png" properties="cover-image"/>
    <item id="inline-image" href="images/inline.png" media-type="image/png"/>
  </manifest>
  <spine><itemref idref="cover"/><itemref idref="chapter"/></spine>
</package>
"@
  Write-ZipTextEntry $zip "OEBPS/cover.xhtml" @"
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Cover</title></head>
<body><div><img src="images/cover.png" alt="Lectern0 Slice 5A cover"/></div></body></html>
"@
  Write-ZipTextEntry $zip "OEBPS/chapter.xhtml" @"
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Images</title></head>
<body><h1>Shared decoder proof</h1><p>The inline resource is decoded by the same foundation mechanism.</p>
<img src="images/inline.png" alt="Lectern0 Slice 5A inline image"/>
<p>The host retains cache identity, status mapping, and final presentation.</p></body></html>
"@
  Write-ZipBytesEntry $zip "OEBPS/images/cover.png" $coverPng
  Write-ZipBytesEntry $zip "OEBPS/images/inline.png" $inlinePng
} finally {
  $zip.Dispose()
}

function Invoke-ImageSmoke {
  $output = & $Exe --image-smoke $Epub $CoverBmp $InlineBmp 2>&1
  if ($LASTEXITCODE -ne 0) {
    $output | Write-Host
    throw "lectern0 image smoke failed with exit code $LASTEXITCODE"
  }
  $line = $output | Where-Object { $_ -match '^lectern0_image_smoke result=pass ' } |
    Select-Object -Last 1
  if (!$line -or
      $line -notmatch 'cover_loaded=([1-9][0-9]*) inline_loaded=([1-9][0-9]*)' -or
      $line -notmatch 'entries=2 lookups=5 hits=3 misses=2' -or
      $line -notmatch 'cover_hash=([0-9a-fA-F]{16}) inline_hash=([0-9a-fA-F]{16})') {
    $output | Write-Host
    throw "lectern0 image smoke did not report loaded cover/inline evidence"
  }
  if (!(Test-Path -LiteralPath $CoverBmp -PathType Leaf) -or
      !(Test-Path -LiteralPath $InlineBmp -PathType Leaf)) {
    throw "lectern0 image smoke did not write both BMP files"
  }
  [pscustomobject]@{
    Line = [string]$line
    CoverFileHash = (Get-FileHash -LiteralPath $CoverBmp -Algorithm SHA256).Hash
    InlineFileHash = (Get-FileHash -LiteralPath $InlineBmp -Algorithm SHA256).Hash
  }
}

$first = Invoke-ImageSmoke
$second = Invoke-ImageSmoke
if ($first.Line -ne $second.Line -or
    $first.CoverFileHash -ne $second.CoverFileHash -or
    $first.InlineFileHash -ne $second.InlineFileHash) {
  throw "lectern0 image smoke is not repeatable"
}

Write-Host $second.Line
Write-Host "win32_lectern0_image_smoke result=pass repeat=2 fixture=$Epub cover=$CoverBmp inline=$InlineBmp"
