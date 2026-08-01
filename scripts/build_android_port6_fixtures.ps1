param(
  [string]$OutputPath = "",
  [string]$AlphaOutputPath = "",
  [string]$BetaOutputPath = ""
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
if (!$OutputPath) {
  $OutputPath = Join-Path $Root `
    "android\app\src\main\assets\port6\octavo_port6.epub"
} elseif (![System.IO.Path]::IsPathRooted($OutputPath)) {
  $OutputPath = Join-Path $Root $OutputPath
}
if (!$AlphaOutputPath) {
  $AlphaOutputPath = Join-Path $Root `
    "android\app\src\main\assets\port6\octavo_port6_alpha.epub"
} elseif (![System.IO.Path]::IsPathRooted($AlphaOutputPath)) {
  $AlphaOutputPath = Join-Path $Root $AlphaOutputPath
}
if (!$BetaOutputPath) {
  $BetaOutputPath = Join-Path $Root `
    "android\app\src\main\assets\port6\octavo_port6_beta.epub"
} elseif (![System.IO.Path]::IsPathRooted($BetaOutputPath)) {
  $BetaOutputPath = Join-Path $Root $BetaOutputPath
}
$OutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$AlphaOutputPath = [System.IO.Path]::GetFullPath($AlphaOutputPath)
$BetaOutputPath = [System.IO.Path]::GetFullPath($BetaOutputPath)

Add-Type -AssemblyName System.IO.Compression
$Utf8 = New-Object System.Text.UTF8Encoding($false)
$Timestamp = [System.DateTimeOffset]::new(
  2000, 1, 1, 0, 0, 0, [System.TimeSpan]::Zero)

function New-Chapter {
  param(
    [string]$NumberWord,
    [string]$OrdinalWord,
    [int]$ChapterIndex,
    [int]$ParagraphCount,
    [string]$RegularText,
    [string]$ItalicText,
    [string]$BoldItalicText
  )

  $lines = @(
    '<html xmlns="http://www.w3.org/1999/xhtml">',
    "<head><title>$NumberWord</title></head>",
    "<body><h1 id=`"chapter-$ChapterIndex`">Chapter $NumberWord</h1>"
  )
  foreach ($paragraph in 1..$ParagraphCount) {
    $lines += "<p><strong>$OrdinalWord chapter</strong> paragraph $paragraph $RegularText <em>$ItalicText</em> with <strong><em>$BoldItalicText</em></strong>.</p>"
  }
  $lines += "</body></html>"
  return $lines -join "`n"
}

function Write-EpubFixture {
  param(
    [string]$Path,
    [string]$Identifier,
    [string]$Title,
    [int]$ChapterCount,
    [int]$ParagraphCount,
    [string]$RegularText,
    [string]$ItalicText,
    [string]$BoldItalicText
  )

  $numberWords = @("One", "Two", "Three", "Four")
  $ordinalWords = @("First", "Second", "Third", "Fourth")
  if ($ChapterCount -lt 1 -or $ChapterCount -gt $numberWords.Count) {
    throw "unsupported chapter count $ChapterCount"
  }

  $container = @(
    '<?xml version="1.0"?>',
    '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">',
    '  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>',
    '</container>'
  ) -join "`n"

  $manifest = @()
  $spine = @()
  $navMap = @()
  foreach ($index in 1..$ChapterCount) {
    $manifest += "    <item id=`"c$index`" href=`"chapter$index.xhtml`" media-type=`"application/xhtml+xml`"/>"
    $spine += "    <itemref idref=`"c$index`"/>"
    $number = $numberWords[$index - 1]
    $navMap += "    <navPoint id=`"nav-$index`" playOrder=`"$index`"><navLabel><text>Chapter $number</text></navLabel><content src=`"chapter$index.xhtml#chapter-$index`"/></navPoint>"
  }

  $package = @(
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">',
    '  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">',
    "    <dc:identifier id=`"bookid`">$Identifier</dc:identifier>",
    "    <dc:title>$Title</dc:title>",
    '    <dc:language>en</dc:language>',
    '  </metadata>',
    '  <manifest>'
  ) + $manifest + @(
    '    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>',
    '  </manifest>',
    '  <spine toc="ncx">'
  ) + $spine + @(
    '  </spine>',
    '</package>'
  )
  $package = $package -join "`n"

  $toc = @(
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<ncx xmlns="http://www.daisy.org/2005/ncx/" version="2005-1">',
    "  <head><meta name=`"dtb:uid`" content=`"$Identifier`"/></head>",
    "  <docTitle><text>$Title</text></docTitle>",
    '  <navMap>'
  ) + $navMap + @(
    '  </navMap>',
    '</ncx>'
  )
  $toc = $toc -join "`n"

  $entries = [ordered]@{
    "mimetype" = "application/epub+zip"
    "META-INF/container.xml" = $container
    "OEBPS/content.opf" = $package
    "OEBPS/toc.ncx" = $toc
  }
  foreach ($index in 1..$ChapterCount) {
    $entries["OEBPS/chapter$index.xhtml"] = New-Chapter `
      -NumberWord $numberWords[$index - 1] `
      -OrdinalWord $ordinalWords[$index - 1] `
      -ChapterIndex $index `
      -ParagraphCount $ParagraphCount `
      -RegularText $RegularText `
      -ItalicText $ItalicText `
      -BoldItalicText $BoldItalicText
  }

  $directory = Split-Path -Parent $Path
  [System.IO.Directory]::CreateDirectory($directory) | Out-Null
  $temporary = "$Path.tmp"
  if (Test-Path -LiteralPath $temporary) {
    Remove-Item -LiteralPath $temporary -Force
  }

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
        $entry.LastWriteTime = $Timestamp
        $entryStream = $entry.Open()
        try {
          $bytes = $Utf8.GetBytes([string]$item.Value)
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

  Move-Item -LiteralPath $temporary -Destination $Path -Force
  $file = Get-Item -LiteralPath $Path
  $hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
  Write-Host "android_fixture path=$Path bytes=$($file.Length) sha256=$hash"
}

Write-EpubFixture `
  -Path $OutputPath `
  -Identifier "octavo-android-port6" `
  -Title "Octavo Android Port 6" `
  -ChapterCount 4 `
  -ParagraphCount 256 `
  -RegularText "carries readable proportional deterministic Port 6 library content with" `
  -ItalicText "multi-book catalog architecture" `
  -BoldItalicText "stable emphasis and per-book resume"

Write-EpubFixture `
  -Path $AlphaOutputPath `
  -Identifier "octavo-android-port6-alpha" `
  -Title "Port 6 Alpha Book" `
  -ChapterCount 2 `
  -ParagraphCount 96 `
  -RegularText "proves that the first app-private library EPUB publishes" `
  -ItalicText "visibly distinct alpha-book text" `
  -BoldItalicText "durable independent Reader0 resume"

Write-EpubFixture `
  -Path $BetaOutputPath `
  -Identifier "octavo-android-port6-beta" `
  -Title "Port 6 Beta Book" `
  -ChapterCount 3 `
  -ParagraphCount 80 `
  -RegularText "proves that the second app-private library EPUB publishes" `
  -ItalicText "visibly distinct beta-book text" `
  -BoldItalicText "separate catalog identity and progress"
