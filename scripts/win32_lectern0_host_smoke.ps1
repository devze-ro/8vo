param(
  [string]$OutDir = "local\slice1_host_smoke"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\lectern0.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing lectern0 executable: $Exe"
}

$Out = Join-Path $Root $OutDir
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Epub = Join-Path $Out "lectern0_slice1.epub"
if (Test-Path -LiteralPath $Epub) { Remove-Item -LiteralPath $Epub -Force }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

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
    <dc:identifier id="bookid">lectern0-slice1</dc:identifier>
    <dc:title>Lectern0 Slice 1</dc:title>
    <dc:language>en</dc:language>
  </metadata>
  <manifest>
    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
  </manifest>
  <spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/></spine>
</package>
"@
  Write-ZipTextEntry $zip "OEBPS/toc.ncx" @"
<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="lectern0-slice1"/></head>
  <docTitle><text>Lectern0 Slice 4B</text></docTitle>
  <navMap>
    <navPoint id="nav-one" playOrder="1"><navLabel><text>Chapter One</text></navLabel><content src="chapter1.xhtml"/></navPoint>
    <navPoint id="nav-two" playOrder="2"><navLabel><text>Chapter Two target</text></navLabel><content src="chapter2.xhtml#toc-target"/></navPoint>
  </navMap>
</ncx>
"@
  $body1 = (1..32 | ForEach-Object { "<p>First chapter paragraph $_ carries enough text to paginate the standalone host proof.</p>" }) -join "`n"
  $body2 = (1..12 | ForEach-Object { "<p>Second chapter paragraph $_ proves the concrete cross-spine transition.</p>" }) -join "`n"
  Write-ZipTextEntry $zip "OEBPS/chapter1.xhtml" "<html xmlns=`"http://www.w3.org/1999/xhtml`"><head><title>One</title></head><body><h1>Chapter One</h1>$body1</body></html>"
  Write-ZipTextEntry $zip "OEBPS/chapter2.xhtml" "<html xmlns=`"http://www.w3.org/1999/xhtml`"><head><title>Two</title></head><body><h1 id=`"toc-target`">Chapter Two</h1>$body2</body></html>"
} finally {
  $zip.Dispose()
}

$output = & $Exe --headless $Epub 2>&1
if ($LASTEXITCODE -ne 0) {
  $output | Write-Host
  throw "lectern0 headless host smoke failed with exit code $LASTEXITCODE"
}
$line = ($output | Where-Object { $_ -match '^lectern0_host_smoke result=pass ' } | Select-Object -Last 1)
if (!$line) {
  $output | Write-Host
  throw "lectern0 headless host smoke did not report pass"
}
if ($line -notmatch 'carets=frozen18x32') {
  $output | Write-Host
  throw "lectern0 headless host smoke did not lock the frozen page-caret bridge"
}

$missing = Join-Path $Out "missing.epub"
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
  $missingOutput = & $Exe --headless $missing 2>&1
  $missingExit = $LASTEXITCODE
} finally {
  $ErrorActionPreference = $previousPreference
}
if ($missingExit -eq 0 -or
    !($missingOutput | Where-Object { $_ -match '^lectern0_host_smoke result=fail reason=open$' })) {
  $missingOutput | Write-Host
  throw "lectern0 invalid-path host smoke did not fail visibly"
}

Write-Host $line
Write-Host "win32_lectern0_host_smoke result=pass fixture=$Epub"
