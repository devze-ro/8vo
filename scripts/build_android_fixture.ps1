param(
  [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
if (!$OutputPath) {
  $OutputPath = Join-Path $Root `
    "android\app\src\main\assets\port4\octavo_port4.epub"
} elseif (![System.IO.Path]::IsPathRooted($OutputPath)) {
  $OutputPath = Join-Path $Root $OutputPath
}
$OutputPath = [System.IO.Path]::GetFullPath($OutputPath)

function New-Chapter {
  param(
    [string]$NumberWord,
    [string]$OrdinalWord,
    [int]$ChapterIndex
  )

  $lines = @(
    '<html xmlns="http://www.w3.org/1999/xhtml">',
    "<head><title>$NumberWord</title></head>",
    "<body><h1 id=`"chapter-$ChapterIndex`">Chapter $NumberWord</h1>"
  )
  foreach ($paragraph in 1..256) {
    $lines += "<p><strong>$OrdinalWord chapter</strong> paragraph $paragraph carries <em>readable proportional</em> deterministic Port 4 typography with <strong><em>stable emphasis</em></strong>.</p>"
  }
  $lines += "</body></html>"
  return $lines -join "`n"
}

$container = @(
  '<?xml version="1.0"?>',
  '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">',
  '  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>',
  '</container>'
) -join "`n"

$package = @(
  '<?xml version="1.0" encoding="UTF-8"?>',
  '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">',
  '  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">',
  '    <dc:identifier id="bookid">octavo-android-port4</dc:identifier>',
  '    <dc:title>Octavo Android Port 4</dc:title>',
  '    <dc:language>en</dc:language>',
  '  </metadata>',
  '  <manifest>',
  '    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>',
  '    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>',
  '    <item id="c3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>',
  '    <item id="c4" href="chapter4.xhtml" media-type="application/xhtml+xml"/>',
  '    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>',
  '  </manifest>',
  '  <spine toc="ncx">',
  '    <itemref idref="c1"/><itemref idref="c2"/>',
  '    <itemref idref="c3"/><itemref idref="c4"/>',
  '  </spine>',
  '</package>'
) -join "`n"

$toc = @(
  '<?xml version="1.0" encoding="UTF-8"?>',
  '<ncx xmlns="http://www.daisy.org/2005/ncx/" version="2005-1">',
  '  <head><meta name="dtb:uid" content="octavo-android-port4"/></head>',
  '  <docTitle><text>Octavo Android Port 4</text></docTitle>',
  '  <navMap>',
  '    <navPoint id="nav-one" playOrder="1"><navLabel><text>Chapter One</text></navLabel><content src="chapter1.xhtml#chapter-1"/></navPoint>',
  '    <navPoint id="nav-two" playOrder="2"><navLabel><text>Chapter Two</text></navLabel><content src="chapter2.xhtml#chapter-2"/></navPoint>',
  '    <navPoint id="nav-three" playOrder="3"><navLabel><text>Chapter Three</text></navLabel><content src="chapter3.xhtml#chapter-3"/></navPoint>',
  '    <navPoint id="nav-four" playOrder="4"><navLabel><text>Chapter Four</text></navLabel><content src="chapter4.xhtml#chapter-4"/></navPoint>',
  '  </navMap>',
  '</ncx>'
) -join "`n"

$entries = [ordered]@{
  "mimetype" = "application/epub+zip"
  "META-INF/container.xml" = $container
  "OEBPS/content.opf" = $package
  "OEBPS/toc.ncx" = $toc
  "OEBPS/chapter1.xhtml" = New-Chapter "One" "First" 1
  "OEBPS/chapter2.xhtml" = New-Chapter "Two" "Second" 2
  "OEBPS/chapter3.xhtml" = New-Chapter "Three" "Third" 3
  "OEBPS/chapter4.xhtml" = New-Chapter "Four" "Fourth" 4
}

$directory = Split-Path -Parent $OutputPath
[System.IO.Directory]::CreateDirectory($directory) | Out-Null
$temporary = "$OutputPath.tmp"
if (Test-Path -LiteralPath $temporary) {
  Remove-Item -LiteralPath $temporary -Force
}

Add-Type -AssemblyName System.IO.Compression
$utf8 = New-Object System.Text.UTF8Encoding($false)
$timestamp = [System.DateTimeOffset]::new(
  2000, 1, 1, 0, 0, 0, [System.TimeSpan]::Zero)
$stream = [System.IO.File]::Open(
  $temporary,
  [System.IO.FileMode]::CreateNew,
  [System.IO.FileAccess]::Write,
  [System.IO.FileShare]::None)
try {
  $archive = New-Object System.IO.Compression.ZipArchive(
    $stream, [System.IO.Compression.ZipArchiveMode]::Create, $true)
  try {
    foreach ($item in $entries.GetEnumerator()) {
      $entry = $archive.CreateEntry(
        $item.Key, [System.IO.Compression.CompressionLevel]::NoCompression)
      $entry.LastWriteTime = $timestamp
      $entryStream = $entry.Open()
      try {
        $bytes = $utf8.GetBytes([string]$item.Value)
        $entryStream.Write($bytes, 0, $bytes.Length)
      } finally {
        $entryStream.Dispose()
      }
    }
  } finally {
    $archive.Dispose()
  }
} finally {
  $stream.Dispose()
}

Move-Item -LiteralPath $temporary -Destination $OutputPath -Force
$file = Get-Item -LiteralPath $OutputPath
$hash = (Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256).Hash
Write-Host "android_fixture path=$OutputPath bytes=$($file.Length) sha256=$hash"
