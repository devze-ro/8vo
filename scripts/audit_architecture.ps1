param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$failures = [System.Collections.Generic.List[string]]::new()
$buildPath = Join-Path $RepoRoot "code\build.c"
$appPath = Join-Path $RepoRoot "code\octavo.c"
$pdfHeaderPath = Join-Path $RepoRoot "code\octavo_pdf.h"
$pdfSourcePath = Join-Path $RepoRoot "code\octavo_pdf.c"
$themeHeaderPath = Join-Path $RepoRoot "code\octavo_theme.h"
$themePath = Join-Path $RepoRoot "code\octavo_theme.c"
$libraryHeaderPath = Join-Path $RepoRoot "code\octavo_library.h"
$libraryPath = Join-Path $RepoRoot "code\octavo_library.c"
$accessibilityPath = Join-Path $RepoRoot "code\platform\win32\octavo_accessibility_win32.c"
$parityPath = Join-Path $RepoRoot "scripts\win32_reader_view_stage2b0_parity.ps1"
$repeatQueuePath = Join-Path $RepoRoot "scripts\win32_octavo_page_repeat_queue_smoke.ps1"
$pageTurnPath = Join-Path $RepoRoot "scripts\win32_octavo_page_turn_regression_smoke.ps1"
$dependencyCheckPath = Join-Path $RepoRoot "scripts\check_dependencies.ps1"
$win32BuildPath = Join-Path $RepoRoot "build\win32_build.bat"
$pdfProvenanceAuditPath = Join-Path $RepoRoot "scripts\audit_win32_pdf_provenance.ps1"
$pdfBuildProvenancePath = Join-Path $RepoRoot "scripts\write_win32_pdf_build_provenance.ps1"
$pdfSmokePath = Join-Path $RepoRoot "scripts\win32_octavo_pdf_stage1_smoke.ps1"
$androidDependencySmokePath =
  Join-Path $RepoRoot "scripts\android_dependency_guard_no_mupdf_smoke.ps1"
$androidCppRoot = Join-Path $RepoRoot "android\app\src\main\cpp"
$androidCMakePath = Join-Path $androidCppRoot "CMakeLists.txt"
$androidBuildPath = Join-Path $RepoRoot "android\app\build.gradle.kts"
$androidJniPath = Join-Path $androidCppRoot "octavo_android_jni.c"
$androidJavaRoot =
  Join-Path $RepoRoot 'android\app\src\main\java\ro\devze\octavo'
$androidUi0ThemeSnapshotPath =
  Join-Path $androidJavaRoot 'Ui0AndroidThemeSnapshot.java'
$androidUi0ThemeAdapterPath =
  Join-Path $androidJavaRoot 'Ui0AndroidThemeAdapter.java'
$androidNavigationPanelPath =
  Join-Path $androidJavaRoot 'OctavoNavigationPanel.java'
$androidLegacyEditorColorPath =
  Join-Path $RepoRoot 'android\app\src\main\res\values\colors.xml'
$androidLegacyEditorThemePath =
  Join-Path $RepoRoot 'android\app\src\main\res\values-v26\styles.xml'
$androidNavigationPath =
  Join-Path $androidCppRoot "octavo_android_port8_navigation.inc"
if (!(Test-Path -LiteralPath $buildPath)) { $failures.Add("missing code/build.c") }
if (!(Test-Path -LiteralPath $appPath)) { $failures.Add("missing code/octavo.c") }
if (!(Test-Path -LiteralPath $pdfHeaderPath)) {
  $failures.Add("missing concrete Win32 PDF host contract")
}
if (!(Test-Path -LiteralPath $pdfSourcePath)) {
  $failures.Add("missing concrete Win32 PDF host implementation")
}
if (!(Test-Path -LiteralPath $themeHeaderPath)) {
  $failures.Add("missing platform-neutral 8vo theme catalog contract")
}
if (!(Test-Path -LiteralPath $themePath)) {
  $failures.Add("missing platform-neutral 8vo theme catalog implementation")
}
if (!(Test-Path -LiteralPath $libraryHeaderPath)) {
  $failures.Add("missing bounded Octavo library records")
}
if (!(Test-Path -LiteralPath $libraryPath)) {
  $failures.Add("missing Octavo library persistence implementation")
}
if (!(Test-Path -LiteralPath $accessibilityPath)) {
  $failures.Add("missing host-owned Win32 accessibility adapter")
}
if (!(Test-Path -LiteralPath $parityPath)) {
  $failures.Add("missing two-host Reader View parity runner")
}
if (!(Test-Path -LiteralPath $repeatQueuePath)) {
  $failures.Add("missing real Win32 page-repeat queue regression")
}
if (!(Test-Path -LiteralPath $pageTurnPath)) {
  $failures.Add("missing exact-book page-turn regression")
}
if (!(Test-Path -LiteralPath $dependencyCheckPath)) {
  $failures.Add("missing dependency revision guard")
}
if (!(Test-Path -LiteralPath $win32BuildPath)) {
  $failures.Add("missing strict Win32 build entry point")
}
if (!(Test-Path -LiteralPath $pdfProvenanceAuditPath)) {
  $failures.Add("missing Win32 PDF compiler/provenance audit")
}
if (!(Test-Path -LiteralPath $pdfBuildProvenancePath)) {
  $failures.Add("missing final Win32 PDF artifact provenance writer")
}
if (!(Test-Path -LiteralPath $pdfSmokePath)) {
  $failures.Add("missing standalone Win32 PDF Stage 1 smoke")
}
if (!(Test-Path -LiteralPath $androidDependencySmokePath)) {
  $failures.Add("missing Android no-MuPDF dependency-guard regression")
}
if (!(Test-Path -LiteralPath $androidCMakePath)) {
  $failures.Add("missing Android native build definition")
}
if (!(Test-Path -LiteralPath $androidBuildPath)) {
  $failures.Add("missing Android application build definition")
}
if (!(Test-Path -LiteralPath $androidJniPath)) {
  $failures.Add("missing Android native host adapter")
}
if (!(Test-Path -LiteralPath $androidNavigationPath)) {
  $failures.Add("missing Android structural-navigation adapter")
}

if (!(Test-Path -LiteralPath $androidUi0ThemeSnapshotPath -PathType Leaf)) {
  $failures.Add('missing typed Android UI0 theme snapshot boundary')
}
if (!(Test-Path -LiteralPath $androidUi0ThemeAdapterPath -PathType Leaf)) {
  $failures.Add('missing product-neutral UI0-to-Android theme adapter')
}
if (!(Test-Path -LiteralPath $androidNavigationPanelPath -PathType Leaf)) {
  $failures.Add('missing Android Navigation panel consumer')
}
if (!(Test-Path -LiteralPath $androidLegacyEditorColorPath -PathType Leaf) -or
    !(Test-Path -LiteralPath $androidLegacyEditorThemePath -PathType Leaf)) {
  $failures.Add('missing Android 8-9 editor compatibility theme resources')
}

if ($failures.Count -eq 0) {
  $build = [System.IO.File]::ReadAllText($buildPath)
  $app = [System.IO.File]::ReadAllText($appPath)
  $pdfHeader = [System.IO.File]::ReadAllText($pdfHeaderPath)
  $pdfSource = [System.IO.File]::ReadAllText($pdfSourcePath)
  $themeHeader = [System.IO.File]::ReadAllText($themeHeaderPath)
  $theme = [System.IO.File]::ReadAllText($themePath)
  $libraryHeader = [System.IO.File]::ReadAllText($libraryHeaderPath)
  $library = [System.IO.File]::ReadAllText($libraryPath)
  $accessibility = [System.IO.File]::ReadAllText($accessibilityPath)
  $parity = [System.IO.File]::ReadAllText($parityPath)
  $repeatQueue = [System.IO.File]::ReadAllText($repeatQueuePath)
  $pageTurn = [System.IO.File]::ReadAllText($pageTurnPath)
  $dependencyCheck = [System.IO.File]::ReadAllText($dependencyCheckPath)
  $win32Build = [System.IO.File]::ReadAllText($win32BuildPath)
  $pdfProvenanceAudit =
    [System.IO.File]::ReadAllText($pdfProvenanceAuditPath)
  $pdfBuildProvenance =
    [System.IO.File]::ReadAllText($pdfBuildProvenancePath)
  $pdfSmoke = [System.IO.File]::ReadAllText($pdfSmokePath)
  $androidDependencySmoke =
    [System.IO.File]::ReadAllText($androidDependencySmokePath)
  $androidCMake = [System.IO.File]::ReadAllText($androidCMakePath)
  $androidBuild = [System.IO.File]::ReadAllText($androidBuildPath)
  $forbiddenGround0Variable = "LECTERN0_ZERO_" + "FOUNDATION_DIR"
  $androidJni = [System.IO.File]::ReadAllText($androidJniPath)
  $androidNavigation =
    [System.IO.File]::ReadAllText($androidNavigationPath)
  $androidUi0ThemeSnapshot =
    [System.IO.File]::ReadAllText($androidUi0ThemeSnapshotPath)
  $androidUi0ThemeAdapter =
    [System.IO.File]::ReadAllText($androidUi0ThemeAdapterPath)
  $androidNavigationPanel =
    [System.IO.File]::ReadAllText($androidNavigationPanelPath)
  $androidLegacyEditorColor =
    [System.IO.File]::ReadAllText($androidLegacyEditorColorPath)
  $androidLegacyEditorTheme =
    [System.IO.File]::ReadAllText($androidLegacyEditorThemePath)
  $nativeUi0SnapshotMarkers = @(
    '#if UI0_API_VERSION != 91',
    'OCTAVO_ANDROID_UI0_SNAPSHOT_PACKET_COUNT == 154',
    'OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_COUNT = 6',
    'packet[19] = 0;',
    'Java_ro_devze_octavo_OctavoNative_ui0AndroidThemeSnapshot',
    'ui0_token_patch_set_color',
    'ui0_token_patch_set_density',
    'UI0DensityRole_ControlHeight',
    'UI0DensityRole_IconButtonSize',
    'UI0DensityRole_RowMinHeight',
    'UI0DensityRole_MenuItemHeight',
    'value < 48 ? 48 : value',
    'ui0_resolve_token_patch',
    'ui0_draw_theme_from_resolved',
    'ui0_tree_style_from_resolved',
    'ui0_control_style_from_resolved',
    'ui0_text_input_style_from_resolved',
    'ui0_tree_draw_record'
  )
  foreach ($marker in $nativeUi0SnapshotMarkers) {
    if ($androidJni.IndexOf($marker) -lt 0) {
      $failures.Add('Android UI0 snapshot must retain its exact API 91 public native derivation')
      break
    }
  }
  $javaUi0SnapshotMarkers = @(
    'static final int MAGIC = 0x4F553941;',
    'static final int VERSION = 1;',
    'static final int UI0_API_VERSION = 91;',
    'static final int PACKET_LENGTH = 154;',
    'enum TextInputMetric',
    'static Ui0AndroidThemeSnapshot parse(int[] source)',
    'source.length != PACKET_LENGTH',
    'int[] packet = source.clone();',
    'packet[0] != MAGIC',
    'packet[1] != VERSION',
    'packet[2] != UI0_API_VERSION',
    'packet[3] != PACKET_LENGTH',
    'packet[18] != TextInputMetric.values().length',
    'packet[19] != 0',
    'validMetrics(',
    'DensityRole.CONTROL_HEIGHT.ordinal()] < 48',
    'int textInput(TextInputMetric metric)',
    'return packet.clone();'
  )
  foreach ($marker in $javaUi0SnapshotMarkers) {
    if ($androidUi0ThemeSnapshot.IndexOf($marker) -lt 0 -or
        $androidUi0ThemeSnapshot.IndexOf('OctavoNative') -ge 0) {
      $failures.Add('Android UI0 snapshot parser must retain strict API 91 packet validation and ownership')
      break
    }
  }
  $androidUi0AdapterMarkers = @(
    'final class Ui0AndroidThemeAdapter {',
    'Ui0AndroidThemeAdapter(Ui0AndroidThemeSnapshot snapshot, float density)',
    'Float.isFinite(density)',
    'int color(Ui0AndroidThemeSnapshot.ColorRole role)',
    'int textSizeSp(Ui0AndroidThemeSnapshot.TypographyRole role)',
    'float relativeTextScale(',
    'int spacingPx(Ui0AndroidThemeSnapshot.SpacingRole role)',
    'int radiusPx(Ui0AndroidThemeSnapshot.RadiusRole role)',
    'int densityPx(Ui0AndroidThemeSnapshot.DensityRole role)',
    'int treePx(Ui0AndroidThemeSnapshot.TreeMetric metric)',
    'int controlPx(Ui0AndroidThemeSnapshot.ControlMetric metric)',
    'int textInputPx(Ui0AndroidThemeSnapshot.TextInputMetric metric)',
    'int hierarchyTextStartPx(int depth)',
    'Ui0AndroidThemeSnapshot.TreeMetric.EXPANDER_SIZE',
    'Ui0AndroidThemeSnapshot.TreeMetric.EXPANDER_GAP',
    'int currentRailInsetPx()',
    'GradientDrawable panelBackground()',
    'LayerDrawable rowBackground(boolean current)',
    'StateListDrawable neutralBackground()',
    'StateListDrawable actionBackground()',
    'StateListDrawable optionBackground()',
    'StateListDrawable inputBackground()',
    'ColorStateList hierarchyTextColors(',
    'ColorStateList neutralTextColors()',
    'ColorStateList actionTextColors()',
    'ColorStateList radioTextColors()',
    'ColorStateList radioTintColors()',
    'ColorStateList inputTextColors()',
    'ColorStateList inputHintColors()',
    'snapshot.drawText(',
    'Ui0AndroidThemeSnapshot.DrawState.DISABLED'
  )
  foreach ($marker in $androidUi0AdapterMarkers) {
    if ($androidUi0ThemeAdapter.IndexOf($marker) -lt 0) {
      $failures.Add(
        'Android UI0 adapter must retain the complete typed native translation surface')
      break
    }
  }
  if ($androidLegacyEditorColor.IndexOf(
        '<color name=''octavo_legacy_editor_accent''>#8B7560</color>') -lt 0 -or
      $androidLegacyEditorTheme.IndexOf(
        '<item name=''android:colorAccent''>@color/octavo_legacy_editor_accent</item>') -lt 0 -or
      $androidLegacyEditorTheme.IndexOf(
        '<item name=''android:colorControlActivated''>@color/octavo_legacy_editor_accent</item>') -lt 0) {
    $failures.Add(
      'Android 8-9 editor compatibility must retain the qualified fixed accent and both public theme bindings')
  }
  $androidUi0AdapterInstanceFields = [regex]::Matches(
    $androidUi0ThemeAdapter,
    '(?m)^    (?![^\r\n;]*\bstatic\b)(?:public |protected |private )?(?:final )?[A-Za-z0-9_<>\[\].?]+\s+[A-Za-z_][A-Za-z0-9_]*(?:\s*=[^;]+)?;\s*$')
  $androidUi0AdapterFieldText = @(
    $androidUi0AdapterInstanceFields |
      ForEach-Object { $_.Value.Trim() })
  $androidUi0AdapterConstructorCount = [regex]::Matches(
    $androidUi0ThemeAdapter,
    '(?m)^    Ui0AndroidThemeAdapter\s*\(').Count
  $androidUi0AdapterConstructor = [regex]::Match(
    $androidUi0ThemeAdapter,
    '(?m)^    Ui0AndroidThemeAdapter\s*\(\s*Ui0AndroidThemeSnapshot\s+snapshot\s*,\s*float\s+density\s*\)')
  if ($androidUi0AdapterInstanceFields.Count -ne 2 -or
      $androidUi0AdapterFieldText -notcontains
        'private final Ui0AndroidThemeSnapshot snapshot;' -or
      $androidUi0AdapterFieldText -notcontains 'private final float density;' -or
      $androidUi0AdapterConstructorCount -ne 1 -or
      !$androidUi0AdapterConstructor.Success) {
    $failures.Add(
      'Android UI0 adapter instance ownership must remain snapshot-and-density only')
  }
  $androidUi0AdapterForbiddenPatterns = @(
    '(?i)\bOctavoActivity\b',
    '(?i)\bOctavoNavigationPanel\b',
    '(?i)\bOctavoNative\b',
    '(?i)\bOctavoDesignTokens\b',
    '(?i)\blistener\b',
    '(?i)\bworkflow\b',
    '(?i)\bstore\b',
    '(?i)\bresources?\b',
    '(?<!android\.)\bR\.[A-Za-z_]',
    '\bgetResources\s*\(',
    '\bgetString\s*\('
  )
  foreach ($pattern in $androidUi0AdapterForbiddenPatterns) {
    if ($androidUi0ThemeAdapter -match $pattern) {
      $failures.Add(
        'Android UI0 adapter must remain free of product workflow and resource coupling')
      break
    }
  }

  $androidNavigationAdapterMarkers = @(
    'private Ui0AndroidThemeAdapter ui0Adapter;',
    'new Ui0AndroidThemeAdapter(',
    'ui0Adapter.panelBackground()',
    'ui0Adapter.rowBackground(',
    'ui0Adapter.neutralBackground()',
    'ui0Adapter.actionBackground()',
    'ui0Adapter.optionBackground()',
    'ui0Adapter.inputBackground()',
    'ui0Adapter.hierarchyTextColors(',
    'ui0Adapter.neutralTextColors()',
    'ui0Adapter.actionTextColors()',
    'ui0Adapter.radioTextColors()',
    'ui0Adapter.radioTintColors()',
    'ui0Adapter.inputTextColors()',
    'ui0Adapter.inputHintColors()',
    '.hierarchyTextStartPx(depth)',
    'ui0Adapter.currentRailInsetPx()',
    'ui0Adapter.textSizeSp(',
    'ui0Adapter.relativeTextScale('
  )
  foreach ($marker in $androidNavigationAdapterMarkers) {
    if ($androidNavigationPanel.IndexOf($marker) -lt 0) {
      $failures.Add(
        'Android Navigation must create and exclusively consume the typed UI0 Android adapter')
      break
    }
  }
  $resolveThemeMatch = [regex]::Match(
    $androidNavigationPanel,
    '(?s)private static Ui0AndroidThemeSnapshot resolveTheme\s*\(\s*OctavoAppearance appearance\s*\)\s*\{.*?return resolved;\s*\}')
  $nativeThemeResolveCount = [regex]::Matches(
    $androidNavigationPanel,
    'OctavoNative\.ui0AndroidThemeSnapshot\s*\(').Count
  $snapshotParseCount = [regex]::Matches(
    $androidNavigationPanel,
    'Ui0AndroidThemeSnapshot\.parse\s*\(').Count
  if (!$resolveThemeMatch.Success -or
      [regex]::Matches($resolveThemeMatch.Value, ';').Count -ne 4 -or
      $nativeThemeResolveCount -ne 1 -or
      $snapshotParseCount -ne 1 -or
      $resolveThemeMatch.Value.IndexOf(
        'OctavoNative.ui0AndroidThemeSnapshot(') -lt 0 -or
      $resolveThemeMatch.Value.IndexOf(
        'Ui0AndroidThemeSnapshot.parse(') -lt 0) {
    $failures.Add(
      'Android Navigation native theme resolution must remain one thin validated snapshot factory')
  }
  $navigationTextSizeCallCount = [regex]::Matches(
    $androidNavigationPanel,
    '\.setTextSize\s*\(').Count
  $navigationSemanticTextSizeCallCount = [regex]::Matches(
    $androidNavigationPanel,
    '\.setTextSize\s*\(\s*ui0Adapter\.textSizeSp\s*\(').Count
  if ($navigationTextSizeCallCount -eq 0 -or
      $navigationTextSizeCallCount -ne $navigationSemanticTextSizeCallCount -or
      $androidNavigationPanel -match
        '\.setTextSize\s*\(\s*(?:android\.util\.TypedValue\.[^,]+\s*,\s*)?\d+(?:\.\d+)?[fFdD]?\s*\)' -or
      $androidNavigationPanel -match
        '\bnew\s+(?:GradientDrawable|StateListDrawable|ColorStateList)\b' -or
      $androidNavigationPanel -match
        '(?m)^\s*private\s+(?:static\s+)?(?:int|float)\s+dp\s*\(' -or
      $androidNavigationPanel.IndexOf('Ui0AndroidThemeSnapshot.resolve(') -ge 0 -or
      $androidNavigationPanel.IndexOf('INDENT_DP') -ge 0 -or
      $androidNavigationPanel.IndexOf(
        'rowBackground(OctavoDesignTokens') -ge 0) {
    $failures.Add(
      'Android Navigation must not retain local theme, drawable, text-size, or dp translation')
  }
  if ($dependencyCheck.IndexOf($forbiddenGround0Variable) -ge 0 -or
      $win32Build.IndexOf($forbiddenGround0Variable) -ge 0) {
    $failures.Add("dependency resolution must not use the forbidden legacy Ground0 environment variable")
  }
  $androidSourceMatches = [regex]::Matches(
    $androidCMake,
    '(?im)^\s*"?([^"\s]+\.c)"?\s*$')
  if ($androidSourceMatches.Count -ne 1) {
    $failures.Add("Android CMake must compile exactly one application unity C source")
  }
  else {
    $androidUnityRelativePath = $androidSourceMatches[0].Groups[1].Value
    $androidUnityName = Split-Path -Leaf $androidUnityRelativePath
    $androidUnityPath = Join-Path $androidCppRoot $androidUnityRelativePath
    if ($androidUnityName -notmatch '^octavo_android_[A-Za-z0-9_]+_build\.c$') {
      $failures.Add("Android CMake C source must be the Octavo application unity source")
    }
    elseif (!(Test-Path -LiteralPath $androidUnityPath -PathType Leaf)) {
      $failures.Add("Android application unity source is missing")
    }
    else {
      $androidUnity = [System.IO.File]::ReadAllText($androidUnityPath)
      $androidPackageIncludes = @(
        @{ Name = "reader0.c"; Pattern = '(?m)^\s*#\s*include\s+"reader0\.c"\s*$' },
        @{ Name = "ui0.c"; Pattern = '(?m)^\s*#\s*include\s+"ui0\.c"\s*$' },
        @{ Name = "readerview0.c"; Pattern = '(?m)^\s*#\s*include\s+"readerview0\.c"\s*$' }
      )
      $androidCFiles = @(Get-ChildItem -LiteralPath $androidCppRoot -Filter "*.c" -File)
      foreach ($package in $androidPackageIncludes) {
        $unityIncludeCount = [regex]::Matches($androidUnity, $package.Pattern).Count
        $allSourceIncludeCount = 0
        foreach ($androidCFile in $androidCFiles) {
          $androidCSource = [System.IO.File]::ReadAllText($androidCFile.FullName)
          $allSourceIncludeCount += [regex]::Matches(
            $androidCSource,
            $package.Pattern).Count
        }
        if ($unityIncludeCount -ne 1 -or $allSourceIncludeCount -ne 1) {
          $failures.Add(
            "Android unity source must compile $($package.Name) exactly once")
        }
      }
    }
  }
  if ([regex]::Matches($build, '#include\s+"reader0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile reader0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"octavo_pdf\.c"').Count -ne 1 -or
      [regex]::Matches($build, '#\s*include\s+"base/base_heap\.c"').Count -ne 1 -or
      [regex]::Matches($build, '#\s*include\s+"os/os_thread\.c"').Count -ne 1 -or
      [regex]::Matches(
        $build,
        '#\s*include\s+"platform/win32/os_thread_win32\.c"').Count -ne 1) {
    $failures.Add("Win32 unity must compile the concrete PDF host and Reader0 heap/thread closure exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"ui0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile ui0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"readerview0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile readerview0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"octavo_theme\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile the 8vo theme catalog exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"octavo_library\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile the host library implementation exactly once")
  }
  if ([regex]::Matches(
        $build,
        '#include\s+"platform/win32/octavo_accessibility_win32\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile the host accessibility adapter exactly once")
  }
  if ([regex]::Matches($build, '#\s*include\s+"os/os_image\.c"').Count -ne 1 -or
      [regex]::Matches($build, '#\s*include\s+"platform/win32/os_image_win32\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile the shared os_image API and Win32 backend exactly once")
  }
  if ([regex]::Matches($build, '#\s*include\s+"presentation_engine/presentation_engine\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile Presentation Engine API 1 exactly once")
  }
  if ($app.IndexOf('#include "reader0.h"') -lt 0) {
    $failures.Add("octavo must consume the reader0 umbrella")
  }
  if ($app.IndexOf('READER0_API_VERSION != 10') -lt 0 -or
      $app.IndexOf('doc_engine_get_author') -lt 0) {
    $failures.Add("octavo must consume Reader0 API 10 including author metadata and PDF")
  }
  if ($pdfHeader.IndexOf('OCTAVO_PDF_RASTER_MEMORY_CAP = 64 * 1024 * 1024') -lt 0 -or
      $pdfHeader.IndexOf('U8 *rgba_pixels') -lt 0 -or
      $pdfHeader.IndexOf('U32 *bgra_pixels') -lt 0 -or
      $pdfSource.IndexOf('arena_alloc(&raster_params)') -lt 0 -or
      $pdfSource.IndexOf('pdf_reader_render_tile') -lt 0 -or
      $pdfSource.IndexOf('pixel[0] = pixel[2]') -lt 0 -or
      $pdfSource.IndexOf('pdf->bgra_pixels = (U32 *)pdf->rgba_pixels') -lt 0 -or
      $pdfSource.IndexOf('octavo_pdf_invalidate_published_raster(pdf);') -lt 0 -or
      $pdfSource.IndexOf('width > max_pixel_count / height') -lt 0 -or
      $pdfSource.IndexOf('sqrt((F64)max_pixel_count / page_area)') -lt 0 -or
      $pdfSource -match '(?i)\b(?:malloc|calloc|realloc|free)\s*\(') {
    $failures.Add("PDF raster ownership must remain one capped Ground0 allocation with transactional in-place RGBA-to-BGRA publication")
  }
  if ($app -match
        'document_state\s*=\s*ReaderViewLoad_Error;\s*octavo_set_statusf\(app,\s*"PDF render failed' -or
      $app.IndexOf('product-render-resize-retry') -lt 0 -or
      $app.IndexOf('product-render-navigation-retry') -lt 0 -or
      $app.IndexOf('landscape-8k-memory-fit') -lt 0 -or
      $pdfSmoke.IndexOf('landscape8k=5792x2896') -lt 0 -or
      $pdfSmoke.IndexOf('retry=resize,navigate') -lt 0) {
    $failures.Add("PDF memory-aware fit and transient render failures must remain bounded and retryable through resize/navigation")
  }
  if ($app.IndexOf('OctavoDocument_None') -lt 0 -or
      $app.IndexOf('OctavoDocument_EPUB') -lt 0 -or
      $app.IndexOf('OctavoDocument_PDF') -lt 0 -or
      $app.IndexOf('octavo_document_invariants_hold') -lt 0 -or
      $app.IndexOf('octavo_pdf_release(&app->pdf)') -lt 0 -or
      $app.IndexOf('EPUB and PDF Documents') -lt 0 -or
      $app.IndexOf('ReaderViewFeature_Progress') -lt 0 -or
      $app.IndexOf('--pdf-stage1-smoke') -lt 0) {
    $failures.Add("8vo must retain explicit EPUB/PDF ownership, picker, Reader View projection, navigation, and lifecycle smoke boundaries")
  }
  if ($androidCMake.IndexOf('octavo_pdf') -ge 0 -or
      $androidJni.IndexOf('PdfReader') -ge 0 -or
      $androidJni.IndexOf('octavo_pdf') -ge 0) {
    $failures.Add("PDF Stage 1 must remain Win32-only")
  }
  if ($win32Build.IndexOf('/DREADER0_WITH_MUPDF=1') -lt 0 -or
      $win32Build.IndexOf('audit_win32_pdf_provenance.ps1') -lt 0 -or
      $win32Build.IndexOf('write_win32_pdf_build_provenance.ps1') -lt 0 -or
      $win32Build.IndexOf('audit_mupdf_pdf_link_map.ps1') -lt 0 -or
      $win32Build.IndexOf('/INCLUDE:fz_new_search') -lt 0 -or
      $win32Build.IndexOf('/MAP:"8vo.map"') -lt 0 -or
      $win32Build.IndexOf('VCToolsInstallDir') -lt 0 -or
      $win32Build.IndexOf('SELECTED_LINK') -lt 0 -or
      $win32Build.IndexOf('"!SELECTED_LINK!" /nologo /OUT:') -lt 0 -or
      $win32Build.IndexOf('-LinkerPath "!SELECTED_LINK!"') -lt 0 -or
      $pdfProvenanceAudit.IndexOf('Get-FileHash -Algorithm SHA256') -lt 0 -or
      $pdfProvenanceAudit.IndexOf('file_version') -lt 0 -or
      $pdfProvenanceAudit.IndexOf('platform_toolset') -lt 0 -or
      $pdfProvenanceAudit.IndexOf('windows_sdk_version') -lt 0 -or
      $pdfProvenanceAudit.IndexOf('Get-Command link.exe') -lt 0 -or
      $pdfBuildProvenance.IndexOf('input_sha256') -lt 0 -or
      $pdfBuildProvenance.IndexOf('executable') -lt 0 -or
      $pdfBuildProvenance.IndexOf('scripts\write_win32_pdf_build_provenance.ps1') -lt 0 -or
      $dependencyCheck.IndexOf('vendor\mupdf_dependency') -lt 0 -or
      $dependencyCheck.IndexOf('[ValidateSet("Win32Pdf", "AndroidEpub")]') -lt 0 -or
      $dependencyCheck.IndexOf('if ($Target -eq "Win32Pdf")') -lt 0 -or
      $win32Build.IndexOf('-Target Win32Pdf') -lt 0 -or
      $androidBuild.IndexOf('"AndroidEpub"') -lt 0 -or
      $androidDependencySmoke.IndexOf('-Target "AndroidEpub"') -lt 0 -or
      $androidDependencySmoke.IndexOf('-Target "Win32Pdf"') -lt 0 -or
      $pdfSmoke.IndexOf('--pdf-stage1-smoke') -lt 0 -or
      $pdfSmoke.IndexOf('could not remove stale PDF smoke bitmap') -lt 0 -or
      $pdfSmoke.IndexOf('repeat=2') -lt 0) {
    $failures.Add("Win32 PDF build must retain exact dependency/compiler provenance, final link-map audit, and deterministic standalone smoke")
  }
  foreach ($unjustifiedPdfLibrary in @(
    'kernel32.lib', 'winspool.lib', 'advapi32.lib', 'odbc32.lib',
    'odbccp32.lib')) {
    if ($win32Build.IndexOf(
          $unjustifiedPdfLibrary,
          [StringComparison]::OrdinalIgnoreCase) -ge 0) {
      $failures.Add("Win32 PDF link must not add unneeded system library $unjustifiedPdfLibrary")
    }
  }
  if ($app.IndexOf('.soft_wrapped = row->soft_wrapped') -lt 0 -or
      $app.IndexOf('octavo_reader_row_is_soft_wrapped') -ge 0 -or
      $androidJni.IndexOf('.soft_wrapped = row->soft_wrapped') -lt 0 -or
      $androidJni.IndexOf('octavo_android_reader_row_is_soft_wrapped') -ge 0) {
    $failures.Add("Windows and Android must retain Reader0 authoritative soft-wrap provenance")
  }
  $reader0StructuralNavigationApis = @(
    'epub_reader_location_summary_for_position',
    'epub_reader_nav_point_destination_summary',
    'epub_reader_current_nav_point_summary_for_position',
    'epub_reader_navigate_to_percentage',
    'epub_reader_navigate_to_current_spine_page',
    'epub_reader_record_presented_navigation'
  )
  foreach ($api in $reader0StructuralNavigationApis) {
    if ($androidNavigation.IndexOf($api) -lt 0) {
      $failures.Add("Android EPUB structural navigation must consume Reader0 API 10 compatibility API $api")
    }
  }
  if ($androidNavigation.IndexOf('.suppress_history = 1') -lt 0 -or
      $androidNavigation.IndexOf('epub_reader_history_begin') -lt 0 -or
      $androidNavigation.IndexOf('epub_reader_history_finish') -lt 0 -or
      $androidJni.IndexOf('semantic_navigation_waiting_for_present') -lt 0 -or
      $androidJni.IndexOf('progress_display_waiting_for_present') -lt 0) {
    $failures.Add(
      "Android structural navigation must defer shared history and durable progress until presentation")
  }

  if ($app.IndexOf('#include "ui0.h"') -lt 0) {
    $failures.Add("octavo must consume the UI0 umbrella")
  }
  if ($app.IndexOf('UI0_API_VERSION != 91') -lt 0 -or
      $app.IndexOf('ui0_icon_rasterize_rgb32') -lt 0) {
    $failures.Add("octavo must consume UI0 API 91 canonical reader icons")
  }
  if ($app.IndexOf('#include "readerview0.h"') -lt 0 -or
      $app.IndexOf('READERVIEW0_API_VERSION != 3') -lt 0) {
    $failures.Add("octavo must consume Reader View API 3 through its umbrella")
  }
  if ($app.IndexOf('reader_view_build') -lt 0 -or
      $app.IndexOf('octavo_prepare_reader_view_projection') -lt 0 -or
      $app.IndexOf('octavo_apply_reader_view_actions') -lt 0) {
    $failures.Add("octavo must project host data into Reader View API 3 and execute returned actions")
  }
  if ($app.IndexOf('.page_surface_rect = resolved_layout.page_surface_rect') -lt 0 -or
      $app.IndexOf('.content_rect = resolved_layout.content_rect') -lt 0 -or
      $app.IndexOf('ReaderViewContentGeometry reader_content_geometry') -lt 0 -or
      $app.IndexOf('host_toolbar_trailing_width = OctavoHostToolbarTrailingWidth') -lt 0) {
    $failures.Add("octavo must adopt Reader View API 3 atomic layout geometry and retain the host toolbar slot")
  }
  if ($app.IndexOf('projection.chrome_title = octavo_reader_view_text("Reader")') -lt 0 -or
      $app.IndexOf('OctavoToolbarHeight = 48') -ge 0 -or
      $app.IndexOf('OctavoFooterHeight = 36') -ge 0) {
    $failures.Add("octavo must project the accepted title and must not retain independent 48/36 reader chrome")
  }
  if ($app.IndexOf('ReaderViewTextStyle_ChromeTitle') -lt 0 -or
      $app.IndexOf('ReaderViewTextStyle_ChromeMetadata') -lt 0 -or
      $app.IndexOf('ReaderViewTextStyle_MenuItem') -lt 0 -or
      $app.IndexOf('binding->style == ReaderViewTextStyle_ChromeTitle ? 2 : 1') -lt 0 -or
      $app.IndexOf('draw_push_text_box') -lt 0) {
    $failures.Add("octavo must map every finite Reader View text binding through its frozen host text-box raster")
  }
  if ($app.IndexOf('octavo_reader_filter_rasterize_rgb32') -lt 0 -or
      $app.IndexOf('768785035519145851ull') -lt 0) {
    $failures.Add("octavo must preserve the frozen re10 SlidersVertical raster for the Reader View Filter intent")
  }
  if ($app.IndexOf('"%llu%%   Location %llu of %llu"') -lt 0 -or
      $app.IndexOf('"Page %llu of %llu"') -lt 0) {
    $failures.Add("octavo must preserve the accepted Reader View progress-label policy")
  }
  if ($app.IndexOf('.kind = UI0ControlKind_IconButton') -lt 0 -or
      $app.IndexOf('ui0_draw_push_icon(&draw') -lt 0 -or
      $app.IndexOf('UI0IconKind_Close') -lt 0 -or
      $app.IndexOf('UI0Control_Quiet') -ge 0) {
    $failures.Add("octavo Close Book must retain host interaction with the nonquiet UI0 IconButton shell and canonical Close icon")
  }
  if ($app.IndexOf('OctavoHostControl_CloseBook') -lt 0 -or
      $app.IndexOf('host_exit_pointer_armed') -lt 0 -or
      $app.IndexOf('octavo_host_keyboard_tab') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_Find') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_Fullscreen') -lt 0 -or
      $app.IndexOf('Close Book') -lt 0 -or
      $accessibility.IndexOf('octavo_accessibility_host_identity') -lt 0 -or
      $accessibility.IndexOf('octavo_accessibility_host_insertion_shared_count') -lt 0 -or
      $app.IndexOf('order=find_close_fullscreen') -lt 0 -or
      $app.IndexOf('UI0DrawCommand commands[5]') -lt 0 -or
      $app.IndexOf('.clip_rect = app->reader_view_layout.host_toolbar_trailing_rect') -lt 0 -or
      $accessibility.IndexOf('octavo_host_control_invoke') -lt 0) {
    $failures.Add("octavo must expose and fully draw one host Close Book record between Find and Fullscreen for pointer, keyboard, focus-visible chrome, and native accessibility routing")
  }
  if ($app.IndexOf('focus=reference13') -lt 0 -or
      $app.IndexOf('panel_focus=toc_find_annotations_progress_boundary') -lt 0 -or
      $app.IndexOf('octavo_reader_view_panel_focus_regression') -lt 0 -or
      $app.IndexOf('keyboard_routing=focused_edit_or_activate') -lt 0 -or
      $app.IndexOf('octavo_reader_view_keyboard_input_routing_regression') -lt 0 -or
      $app.IndexOf('octavo_reader_view_text_editing') -lt 0 -or
      $app.IndexOf('octavo_reader_view_space_activates_focus') -lt 0 -or
      $app.IndexOf('find_shortcut=focused_input') -lt 0 -or
      $app.IndexOf('octavo_reader_view_open_find_from_shortcut') -lt 0 -or
      $app.IndexOf('octavo_reader_view_find_shortcut_focus_regression') -lt 0 -or
      $app.IndexOf('navigation_panels=space_toc_find') -lt 0 -or
      $app.IndexOf('octavo_reader_view_navigation_panel_interaction_regression') -lt 0 -or
      $app.IndexOf('gutters=boundary_roundtrip') -lt 0 -or
      $app.IndexOf('gutter_input=keyboard_pointer') -lt 0 -or
      $app.IndexOf('carets=frozen18x32') -lt 0 -or
      $app.IndexOf('UI0IconKind_PageCaretLeft') -lt 0 -or
      $app.IndexOf('UI0IconKind_PageCaretRight') -lt 0 -or
      $app.IndexOf('OctavoUI0IconRasterMaxWidth = UI0_ICON_RASTER_MAX_WIDTH') -lt 0 -or
      $app.IndexOf('UI0DrawOp_FocusRing') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_PreviousPage') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_NextPage') -lt 0) {
    $failures.Add("octavo must lock the a6b combined focus order and exact keyboard/pointer page-gutter behavior")
  }
  if ($app.IndexOf('toc_identity=noncontiguous') -lt 0 -or
      $app.IndexOf('octavo_reader_view_covers_noncontiguous_toc_identity') -lt 0 -or
      $app.IndexOf('find_execution=commit_only') -lt 0 -or
      $app.IndexOf('find_clear=immediate') -lt 0 -or
      $app.IndexOf('octavo_reader_view_set_find_query') -lt 0 -or
      $app.IndexOf('find_edit_executed') -lt 0 -or
      $app.IndexOf('find_result_action') -lt 0) {
    $failures.Add("octavo must preserve source TOC identity and frozen edit/commit/clear/result Find behavior")
  }
  if ($app.IndexOf('"%s - re10 loc %llu"') -lt 0 -or
      $app.IndexOf('octavo_reader_view_right_secondary') -lt 0 -or
      $app.IndexOf('octavo_reader_view_register_right_source') -lt 0 -or
      $app.IndexOf('octavo_reader_view_sort_right_candidates') -lt 0 -or
      $app.IndexOf('octavo_reader_view_covers_mixed_right_order') -lt 0 -or
      $app.IndexOf('ReaderViewRow_AttachedToPrevious') -lt 0 -or
      $app.IndexOf('octavo_reader_view_find_text_metrics') -lt 0 -or
      $app.IndexOf('find_metrics=bounded_values') -lt 0 -or
      $app.IndexOf('find_match=measured') -lt 0 -or
      $app.IndexOf('octavo_draw_adapter_covers_measured_find_match') -lt 0 -or
      $app.IndexOf('octavo_draw_adapter_covers_find_status_and_metadata') -lt 0 -or
      $app.IndexOf('font_text_baseline_y_in_rect') -lt 0 -or
      $app.IndexOf('ReaderViewNoteTextMetrics') -lt 0 -or
      $app.IndexOf('octavo_reader_view_note_text_metrics') -lt 0 -or
      $app.IndexOf('octavo_reader_view_covers_note_text_metrics') -lt 0 -or
      $app.IndexOf('ReaderViewTextStyle_NoteEditor') -lt 0 -or
      $app.IndexOf('OctavoReaderViewNotePixelHeight = 18') -lt 0 -or
      $app.IndexOf('command->typography_line_height') -lt 0 -or
      $app.IndexOf('note_metrics=bounded_values_18px') -lt 0 -or
      $app.IndexOf('annotations=reference_metadata') -lt 0 -or
      $app.IndexOf('bookmark_star=projected_remove_once') -lt 0 -or
      $app.IndexOf('note ? highlight->note : highlight->text') -lt 0 -or
      $app.IndexOf('.secondary = octavo_reader_view_text(highlight->note)') -ge 0) {
    $failures.Add("octavo must project frozen Highlight excerpts, Note bodies, kind/location metadata, and caller-measured 18px Note TextArea values while retaining host ownership")
  }
  if ($app.IndexOf('ReaderViewSemanticControl_RightFilter') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_RightFilterOption') -lt 0 -or
      $app.IndexOf('annotations-filter') -lt 0 -or
      $app.IndexOf('annotations_interaction=close_filter_edit_menu') -lt 0 -or
      $app.IndexOf('annotations_pointer=open_filter_escape_select_row_star_menu_note_lifecycle_close') -lt 0 -or
      $app.IndexOf('reader_view_close_note_editor') -lt 0 -or
      $app.IndexOf('ReaderViewAction_CancelNote') -lt 0 -or
      $app.IndexOf('note_lifecycle=acknowledged') -lt 0 -or
      $app.IndexOf('octavo_reader_view_annotation_interaction_regression') -lt 0 -or
      $app.IndexOf('octavo_reader_view_annotation_pointer_regression') -lt 0) {
    $failures.Add("octavo must retain semantic annotation close/filter/row-menu parity evidence")
  }
  if ($app.IndexOf('B32 is_highlight;') -lt 0 -or
      $app.IndexOf('OctavoAnnotationFileV2') -lt 0 -or
      $app.IndexOf('octavo_migrate_highlight_v2') -lt 0 -or
      $app.IndexOf('file.version = 3') -lt 0 -or
      $app.IndexOf('octavo_commit_annotations') -lt 0 -or
      $app.IndexOf('octavo_remove_highlight_identity_at') -lt 0 -or
      $app.IndexOf('octavo_delete_note_at_index') -lt 0 -or
      $app.IndexOf('octavo_save_selection_note') -lt 0 -or
      $app.IndexOf('octavo_toggle_highlight_star_at') -lt 0 -or
      $app.IndexOf('annotation_identity=v3_migrate_demote_restart') -lt 0 -or
      $app.IndexOf('note_persistence=atomic_rollback_open') -lt 0 -or
      $app.IndexOf('bookmark_persistence=rollback') -lt 0 -or
      $app.IndexOf('star_persistence=rollback') -lt 0) {
    $failures.Add("octavo must retain V1/V2-to-V3 annotation identity migration, Note-only demotion, atomic mutation rollback, and restart evidence")
  }
  if ($parity.IndexOf('wide_light_contents_panel') -lt 0 -or
      $parity.IndexOf('wide_light_annotations_highlight_note') -lt 0 -or
      $parity.IndexOf('wide_light_annotations_row_actions') -lt 0 -or
      $parity.IndexOf('wide_light_annotations_note_editor') -lt 0 -or
      $parity.IndexOf('wide_light_exit_keyboard_focus') -lt 0 -or
      $parity.IndexOf('wide_light_previous_gutter_keyboard_focus') -lt 0 -or
      $parity.IndexOf('wide_light_previous_enabled_gutter_keyboard_focus') -lt 0 -or
      $parity.IndexOf('wide_light_next_gutter_keyboard_focus') -lt 0) {
    $failures.Add("the two-host matrix must retain same-theme Contents, real Annotations list/menu/editor, and focused Exit plus disabled/enabled Previous and enabled Next gutter cases")
  }
  if ($parity.IndexOf('F7D9F95A174E0CA776E2BA808A6798D2DAF8B178CFF5D77270D225EC5DDA14D8') -lt 0 -or
      $parity.IndexOf('[string]$Re10Reader0Root') -lt 0 -or
      $parity.IndexOf('re10_reader0_head') -lt 0 -or
      $parity.IndexOf('Require-CleanTree') -lt 0 -or
      $parity.IndexOf('[switch]$AllowDirty') -lt 0 -or
      $parity.IndexOf('-SkipBuild is diagnostic-only') -lt 0 -or
      $parity.IndexOf('parity output root already exists') -lt 0 -or
      $parity.IndexOf('diagnostic_dirty_visual_parity') -lt 0 -or
      $parity.IndexOf('acceptance_eligible = !$AllowDirty -and !$SkipBuild') -lt 0) {
    $failures.Add("final two-host evidence must lock the canonical fixture, require a fresh output root and in-run builds, and reject dirty trees unless an explicit diagnostic-only override is supplied")
  }
  if ($themeHeader.IndexOf('OctavoTheme_Dark = 0') -lt 0 -or
      $themeHeader.IndexOf('OctavoTheme_Light = 1') -lt 0 -or
      $themeHeader.IndexOf('OctavoTheme_CoralDark = 2') -lt 0 -or
      $themeHeader.IndexOf('OctavoTheme_CoralLight = 3') -lt 0 -or
      $themeHeader.IndexOf('OctavoTheme_BlueDark = 4') -lt 0 -or
      $themeHeader.IndexOf('OctavoTheme_BlueLight = 5') -lt 0 -or
      $theme.IndexOf('ui0_theme_profile_for_kind(entry->ui0_profile_kind)') -lt 0 -or
      $theme.IndexOf('octavo_theme_catalog_contract') -lt 0 -or
      $app.IndexOf('#include "octavo_theme.h"') -lt 0 -or
      $app.IndexOf('file.version == 1') -lt 0 -or
      $app.IndexOf('.version = 3') -lt 0 -or
      $app.IndexOf('file.version < 3 ?') -lt 0) {
    $failures.Add("octavo must expose six stable product themes through its explicit UI0 mapping and legacy settings migration")
  }
  if ($app -match 'ui0_theme_profile_for_kind\s*\(\s*\(UI0ThemeProfileKind\)') {
    $failures.Add("octavo product theme persistence must not depend on UI0 profile ordinals")
  }
  if ($app.IndexOf('octavo_draw_adapter_covers_all_ops') -lt 0 -or
      $app.IndexOf('octavo_draw_adapter_covers_reference_edges') -lt 0 -or
      $app.IndexOf('unsupported_count') -lt 0 -or
      $app.IndexOf('octavo_draw_ui0_icon') -lt 0 -or
      $app.IndexOf('octavo_draw_ui0_text') -lt 0) {
    $failures.Add("octavo must render and test the complete UI0 draw-operation surface")
  }
  if ($app -match 'OctavoTheme_Sepia' -or
      $app -match 'reader_view_text_is\(text,\s*"Previous page"\)' -or
      $app -match 'reader_view_text_is\(text,\s*"Next page"\)') {
    $failures.Add("octavo must not retain the old three-theme enum or ASCII reader-control substitutions")
  }
  if ($app.IndexOf('octavo_save_settings') -lt 0 -or
      $app.IndexOf('octavo_save_annotations') -lt 0 -or
      $app.IndexOf('octavo_export_annotations') -lt 0) {
    $failures.Add("octavo must retain settings and annotation persistence ownership")
  }
  if ($app.IndexOf('--reader-view-smoke') -lt 0) {
    $failures.Add("octavo must retain deterministic Reader View API 3 action evidence")
  }
  if ($app.IndexOf('--reader-view-startup-interaction-smoke') -lt 0 -or
      $app.IndexOf('octavo_host_pointer_press') -lt 0 -or
      $app.IndexOf('octavo_host_pointer_release') -lt 0 -or
      $app.IndexOf('surface=library catalog=empty') -lt 0) {
    $failures.Add("octavo must retain the empty-library native press/release Add EPUBs regression")
  }
  if ($app -match '(?s)draw_push_text_in_rect\([^;]+Open a book to begin reading\.') {
    $failures.Add("Reader View must be the single owner of empty/loading/error status painting")
  }
  if ($app.IndexOf('--accessibility-smoke') -lt 0 -or
      $app.IndexOf('WM_GETOBJECT') -lt 0 -or
      $accessibility.IndexOf('IAccessible') -lt 0 -or
      $accessibility.IndexOf('reader_view_accessibility_invoke') -lt 0) {
    $failures.Add("octavo must retain its native adapter over shared semantic/action records")
  }
  if ($app.IndexOf('epub_reader_move_page') -lt 0 -or
      $app.IndexOf('doc_engine_get_author') -lt 0 -or
      $app.IndexOf('epub_reader_navigate_to_nav_point') -lt 0 -or
      $app.IndexOf('epub_reader_navigate_to_search_match') -lt 0) {
    $failures.Add("octavo must consume the concrete Reader0 page, metadata, and semantic navigation surface")
  }
  if ($app.IndexOf('epub_reader_prepare_navigation') -lt 0 -or
      $app.IndexOf('epub_reader_forward_page_range') -lt 0 -or
      $app.IndexOf('epub_reader_build_page_frame') -lt 0 -or
      $app.IndexOf('OctavoAdjacentWarmPageCap = 4') -lt 0 -or
      $app.IndexOf('adjacent_warm_direction') -lt 0) {
    $failures.Add("octavo must consume Reader0 API 10 EPUB navigation preparation with bounded four-page host warming")
  }
  $timerResolutionBegins =
    [regex]::Matches($app, 'timeBeginPeriod\s*\(\s*1\s*\)').Count
  $timerResolutionEnds =
    [regex]::Matches($app, 'timeEndPeriod\s*\(\s*1\s*\)').Count
  if ($app.IndexOf('OctavoPageRepeatFrameRate = 60') -lt 0 -or
      $app.IndexOf('OctavoPageRepeatInitialFrames = 24') -lt 0 -or
      $app.IndexOf('OctavoPageRepeatIntervalFrames = 3') -lt 0 -or
      $app.IndexOf('octavo_page_repeat_delay_ticks') -lt 0 -or
      $app.IndexOf('page_repeat_next_move_ticks') -lt 0 -or
      $app.IndexOf('page_repeat_last_action_emitted_ticks') -lt 0 -or
      $app.IndexOf('page_repeat_next_frame_count') -ge 0 -or
      $timerResolutionBegins -ne 1 -or
      $timerResolutionEnds -ne 2 -or
      $app.IndexOf('else if (!win32->app.page_repeat_active && timer_resolution_active)') -lt 0 -or
      $app.IndexOf('OctavoPageRepeatTimerId') -ge 0) {
    $failures.Add("octavo held navigation must retain bounded wall-clock 60 Hz 24/3 policy, hold-scoped timer resolution with transition and exit cleanup, no presentation-frame clock, and no WM_TIMER repeat driver")
  }
  if ($app.IndexOf('OctavoAdjacentPagePixelCap = 4096 * 4096') -lt 0 -or
      $app.IndexOf('adjacent_page_pixels') -lt 0 -or
      $app.IndexOf('octavo_build_adjacent_page_raster') -lt 0 -or
      $app.IndexOf('app->adjacent_warm_distance == 1') -lt 0) {
    $failures.Add("octavo must retain one bounded host-owned next-page raster while Reader0 owns navigation preparation")
  }
  if ($repeatQueue.IndexOf('--page-repeat-win32-smoke') -lt 0 -or
      $repeatQueue.IndexOf('directions=2/2') -lt 0 -or
      $repeatQueue.IndexOf('reversal=forward_then_backward') -lt 0 -or
      $repeatQueue.IndexOf('returned_to_anchor=1/1') -lt 0 -or
      $repeatQueue.IndexOf('cross_spine_directions=2/2') -lt 0 -or
      $repeatQueue.IndexOf('cross_spine_transitions=') -lt 0 -or
      $repeatQueue.IndexOf('schedule=wall_clock_rebased_no_catch_up') -lt 0 -or
      $repeatQueue.IndexOf('repeat_moves=12+12') -lt 0 -or
      $repeatQueue.IndexOf('presented_frames=13+13') -lt 0 -or
      $repeatQueue.IndexOf('idle_presentations=0/0') -lt 0 -or
      $repeatQueue.IndexOf('canonical_pages=26/26') -lt 0 -or
      $repeatQueue.IndexOf('canonical_nonempty_frames=26/26') -lt 0 -or
      $repeatQueue.IndexOf('zero_pages_or_frames=0/0') -lt 0 -or
      $repeatQueue.IndexOf('orphan_text_pages=0/0') -lt 0 -or
      $repeatQueue.IndexOf('invalid_word_start_pages=0/0') -lt 0 -or
      $repeatQueue.IndexOf('boundary_oracle=raw_spine_utf8_word_start') -lt 0 -or
      $repeatQueue.IndexOf('gotm_prose_scope=active_spine_text_ge_128') -lt 0 -or
      $repeatQueue.IndexOf('queue_range_oracle=self_derived_order_only') -lt 0 -or
      $repeatQueue.IndexOf('independent_range_oracle=external_frozen_re10_required_not_evaluated') -lt 0 -or
      $repeatQueue.IndexOf('gotm_minimum_text_bytes') -lt 0 -or
      $repeatQueue.IndexOf('gotm_minimum_text_rows') -lt 0 -or
      $repeatQueue.IndexOf('action_presentations=26/26') -lt 0 -or
      $repeatQueue.IndexOf('action_overlap=0/0') -lt 0 -or
      $repeatQueue.IndexOf('native_repeats=208/208') -lt 0 -or
      $repeatQueue.IndexOf('navigation_prepare_cross_spine_ready=1+2') -lt 0 -or
      $repeatQueue.IndexOf('navigation_prepare_failures=0+0') -lt 0 -or
      $app.IndexOf('forward.navigation_prepare_cross_spine_ready_count == 1') -lt 0 -or
      $app.IndexOf('backward.navigation_prepare_cross_spine_ready_count == 2') -lt 0 -or
      $repeatQueue.IndexOf('prepared_window_moves=') -lt 0 -or
      $repeatQueue.IndexOf('synchronous_window_rebuild_moves=0+0') -lt 0 -or
      $repeatQueue.IndexOf('synchronous_adjacent_measured_moves=0+0') -lt 0 -or
      $repeatQueue.IndexOf('interval_samples=22/22') -lt 0 -or
      $repeatQueue.IndexOf('action_timing_samples=24/24') -lt 0 -or
      $repeatQueue.IndexOf('stable_presentations=26/26') -lt 0 -or
      $repeatQueue.IndexOf('visible_interval_samples=22/22') -lt 0 -or
      $repeatQueue.IndexOf('queue_drain_batch_max') -lt 0 -or
      $repeatQueue.IndexOf('control_cancel=1/1') -lt 0 -or
      $repeatQueue.IndexOf('shift_cancel=1/1') -lt 0 -or
      $repeatQueue.IndexOf('system_modifier_cancel=1/1') -lt 0 -or
      $repeatQueue.IndexOf('modifier_cancel=3/3') -lt 0 -or
      $repeatQueue.IndexOf('cancelled_repeats=5/5') -lt 0 -or
      $repeatQueue.IndexOf('mutation_gate_drops=7/7') -lt 0 -or
      $repeatQueue.IndexOf('mutation_cancel=1/1') -lt 0 -or
      $repeatQueue.IndexOf('capture_failure_page_recovery=1/1') -lt 0 -or
      $repeatQueue.IndexOf('capture_failure_same_page_freshness=1/1') -lt 0 -or
      $repeatQueue.IndexOf('capture_failure_open_catalog_recovery=1/1') -lt 0 -or
      $repeatQueue.IndexOf('image_page_gate=4/4') -lt 0 -or
      $repeatQueue.IndexOf('persistence_deferred=24/24') -lt 0 -or
      $repeatQueue.IndexOf('persistence_rescheduled=8/8') -lt 0 -or
      $repeatQueue.IndexOf('persistence_transactions=8/8') -lt 0 -or
      $repeatQueue.IndexOf('persistence_hold_state_catalog_unchanged=8/8') -lt 0 -or
      $repeatQueue.IndexOf('persistence_post_stop_state_catalog_advanced=8/8') -lt 0 -or
      $repeatQueue.IndexOf('persistence_cleanup=1/1') -lt 0 -or
      $repeatQueue.IndexOf('auxiliary_paint_dispatched=2/2') -lt 0 -or
      $repeatQueue.IndexOf('main_null_paint_dispatched=2/2') -lt 0 -or
      $repeatQueue.IndexOf('paint=real_queue_dispatch') -lt 0 -or
      $repeatQueue.IndexOf('nominal_first_move_ms=400.000') -lt 0 -or
      $repeatQueue.IndexOf('nominal_move_interval_ms=50.000') -lt 0 -or
      $repeatQueue.IndexOf('nominal_elapsed_ms=950.000') -lt 0 -or
      $repeatQueue.IndexOf('forward_interval_min_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_interval_avg_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_interval_max_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_interval_min_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_interval_avg_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_interval_max_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_immediate_visible_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_first_delay_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_interval_min_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_interval_avg_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_interval_max_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_elapsed_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_immediate_visible_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_first_delay_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_interval_min_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_interval_avg_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_interval_max_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_elapsed_ms') -lt 0 -or
      $repeatQueue.IndexOf('interval_tolerance_ms=34') -lt 0 -or
      $repeatQueue.IndexOf('timing_tolerance_ms=200') -lt 0 -or
      $repeatQueue.IndexOf('frame_budget_ms=64') -lt 0 -or
      $repeatQueue.IndexOf('move_prepare_budget_ms=16.667') -lt 0 -or
      $repeatQueue.IndexOf('render_budget_ms=48.000') -lt 0 -or
      $repeatQueue.IndexOf('present_budget_ms=16.667') -lt 0 -or
      $repeatQueue.IndexOf('$First = Invoke-QueueSmoke "run_1"') -lt 0 -or
      $repeatQueue.IndexOf('$Second = Invoke-QueueSmoke "run_2"') -lt 0) {
    $failures.Add("Octavo must lock the real Win32 60 Hz 24/3 queue cadence twice")
  }
  if ($app.IndexOf('OctavoPageRepeatProbeMoveCount = 12') -lt 0 -or
      $app.IndexOf('OctavoGotmMinimumProseSpineBytes = 128') -lt 0 -or
      $app.IndexOf('OctavoGotmMinimumProseTextBytes = 8') -lt 0 -or
      $app.IndexOf('OctavoGotmMinimumProseTextRows = 1') -lt 0 -or
      $app.IndexOf('OctavoPresentationRetryTimerId = 3') -lt 0 -or
      $app.IndexOf('octavo_page_action_schedule_presentation_retry') -lt 0 -or
      $app.IndexOf('page_action_waiting_for_present') -lt 0 -or
      $app.IndexOf('page_action_pending') -lt 0 -or
      $app.IndexOf('OctavoPageRepeatWin32Probe_ShiftModifier') -lt 0 -or
      $app.IndexOf('octavo_gotm_navigation_frame_is_canonical_nonempty') -lt 0 -or
      $app.IndexOf('octavo_gotm_page_start_is_word_boundary') -lt 0 -or
      $app.IndexOf('OctavoPageRepeatQueueDrainCap = 32') -lt 0 -or
      $app.IndexOf('OctavoPageRepeatProbeQueueDrainCap = OctavoPageRepeatQueueDrainCap') -lt 0 -or
      $app.IndexOf('drained_message_count >= OctavoPageRepeatQueueDrainCap') -lt 0 -or
      $app.IndexOf('state_save_transaction_success_count') -lt 0 -or
      $app.IndexOf('octavo_page_repeat_probe_file_snapshot') -lt 0 -or
      $app.IndexOf('octavo_page_repeat_probe_paint_proc') -lt 0) {
    $failures.Add("Octavo page-repeat proof must cover sustained moves, bounded queue drain, a paired host save with individually atomic state/catalog files, and real paint dispatch")
  }
  if ($pageTurn.IndexOf('invalid_word_start_pages=0/0') -lt 0 -or
      $pageTurn.IndexOf('direct_traversal=64+64_exact') -lt 0 -or
      $pageTurn.IndexOf('boundary_oracle=raw_spine_utf8_word_start') -lt 0 -or
      $pageTurn.IndexOf('gotm_prose_scope=active_spine_text_ge_128') -lt 0 -or
      $pageTurn.IndexOf('boundary_oracle_self_test=1/1') -lt 0 -or
      $pageTurn.IndexOf('deferred_reversal_keyup=1/1') -lt 0 -or
      $pageTurn.IndexOf('gotm_minimum_text_bytes') -lt 0 -or
      $pageTurn.IndexOf('gotm_minimum_text_rows') -lt 0) {
    $failures.Add("Octavo exact-book page-turn proof must lock non-orphan prose, raw UTF-8 word starts, and deferred reversal key-up semantics")
  }
  if ($app.IndexOf('font_cache_tag_from_provider') -lt 0 -or
      $app.IndexOf('octavo_push_reader_text_chunks') -lt 0 -or
      $app.IndexOf('DrawTextFlag_Shaped') -lt 0) {
    $failures.Add("octavo must chunk text through the reader-resolved provider and shaped text mode")
  }
  if ($app.IndexOf('octavo_frame_text_rows_are_complete') -lt 0 -or
      $app.IndexOf('--render-smoke') -lt 0) {
    $failures.Add("octavo must retain deterministic canonical-frame visual evidence")
  }
  if ($app.IndexOf('os_image_decode') -lt 0 -or
      $app.IndexOf('OctavoImageCache') -lt 0 -or
      $app.IndexOf('draw_push_sprite_clipped') -lt 0) {
    $failures.Add("octavo must own cache policy while using shared decode and sprite presentation")
  }
  if ($app.IndexOf('--reader-image-fit-smoke') -lt 0 -or
      $app.IndexOf('row->visual_units ? row->visual_units : 18') -lt 0 -or
      $app.IndexOf('SourceReaderLayoutImagePlacement_ImageOnly') -lt 0 -or
      $app.IndexOf('draw_push_sprite_clipped_sampled') -lt 0 -or
      $app.IndexOf('octavo_image_cache_prepare') -lt 0 -or
      $app.IndexOf('render_resample_bgra8') -lt 0 -or
      $app.IndexOf('DrawSpriteSampleKind_Area') -lt 0) {
    $failures.Add("octavo image pages must consume reader0 visual units and retain bounded ground0 area-prepared sampling evidence")
  }
  if ($app.IndexOf('#include "presentation_engine/presentation_engine.h"') -lt 0 -or
      $app.IndexOf('PRESENTATION_ENGINE_API_VERSION != 1') -lt 0 -or
      $app.IndexOf('octavo_build_reader_presentation') -lt 0 -or
      $app.IndexOf('presentation_engine_block_flow_build') -lt 0) {
    $failures.Add("octavo must route canonical-row vertical geometry through Presentation Engine API 1")
  }
  if ($libraryHeader.IndexOf('OctavoLibraryEntryCap = 512') -lt 0 -or
      $libraryHeader.IndexOf('OctavoLibraryCatalogFileCap = 2 * 1024 * 1024') -lt 0 -or
      $libraryHeader.IndexOf('OctavoLibraryDigest_None') -lt 0 -or
      $libraryHeader.IndexOf('OctavoLibraryDigest_SHA256') -lt 0 -or
      $library.IndexOf('os_write_entire_file_atomic') -lt 0 -or
      $library.IndexOf('GetFullPathNameW') -lt 0 -or
      $library.IndexOf('octavo_library_catalog_sort') -lt 0) {
    $failures.Add("Octavo must retain bounded atomic local catalog records, normalized paths, MRU ordering, and an algorithm-tagged future digest seam")
  }
  if ($app.IndexOf('octavo_draw_library') -lt 0 -or
      $app.IndexOf('OFN_ALLOWMULTISELECT') -lt 0 -or
      $app.IndexOf('OctavoHostControlAction_LocateBook') -lt 0 -or
      $app.IndexOf('Removed from library; source file was not deleted') -lt 0 -or
      $app.IndexOf('octavo_close_book') -lt 0 -or
      $app.IndexOf('--library-smoke') -lt 0 -or
      $app.IndexOf('responsive=wide_and_compact') -lt 0) {
    $failures.Add("Octavo must retain its library-first shell, native import, missing-file recovery, source-preserving removal, Close Book return, and bounded evidence")
  }
  if (($libraryHeader + $library) -match '(?i)sqlite|event\s*bus|vtable|provider\s*table|dependency\s*injection') {
    $failures.Add("the local library core must not introduce a database or indirect provider/event architecture")
  }
  if ($app -match 'IWIC|CLSID_WIC|wincodec\.h') {
    $failures.Add("octavo must not duplicate the ground0 WIC backend")
  }
  if ($build -match '(?i)re10' -or
      $app -match '(?im)^\s*#\s*include[^\r\n]*re10' -or
      $app -match '(?i)\bre10_[A-Za-z0-9_]+\b') {
    $failures.Add("octavo source closure must not depend on re10")
  }
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}

Write-Host "octavo architecture audit: pass"
Write-Host "boundary: Octavo explicit EPUB/PDF host + Ground0-owned PDF raster + readerview0/UI0 chrome + Reader0 API 10 EPUB and Win32 MuPDF core"
