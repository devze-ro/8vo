@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"
set "PROVENANCE_STATE_ARGS="
if defined OCTAVO_ALLOW_DIRTY_DEVELOPMENT_BUILD (
  if not "%OCTAVO_ALLOW_DIRTY_DEVELOPMENT_BUILD%"=="1" (
    echo [8vo] OCTAVO_ALLOW_DIRTY_DEVELOPMENT_BUILD accepts only the explicit value 1.
    exit /b 1
  )
  set "PROVENANCE_STATE_ARGS=-AllowDirtyDevelopment"
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\require_win32_product_source_state.ps1" -RepoRoot "%ROOT%" !PROVENANCE_STATE_ARGS!
if errorlevel 1 exit /b 1

set "PF=%ProgramFiles%"
set "PF86=%ProgramFiles(x86)%"

set "CL="
set "_CL_="
set "LINK="
call :find_vcvarsall
if errorlevel 1 (
  echo [8vo] Failed to locate vcvarsall.bat. Install MSVC or set OCTAVO_VCVARS.
  exit /b 1
)
call "!VCVARSALL!" x64
if errorlevel 1 exit /b 1
set "CL="
set "_CL_="
set "LINK="
if not defined VCToolsInstallDir (
  echo [8vo] Fresh Visual Studio environment did not publish VCToolsInstallDir.
  exit /b 1
)
for %%I in ("!VCToolsInstallDir!bin\Hostx64\x64\cl.exe") do set "SELECTED_CL=%%~fI"
for %%I in ("!VCToolsInstallDir!bin\Hostx64\x64\link.exe") do set "SELECTED_LINK=%%~fI"
if not exist "!SELECTED_CL!" (
  echo [8vo] Fresh Visual Studio environment did not select an x64 cl.exe.
  exit /b 1
)
if not exist "!SELECTED_LINK!" (
  echo [8vo] Fresh Visual Studio environment did not select an x64 link.exe.
  exit /b 1
)
set "PATH_LINK="
for /f "delims=" %%I in ('where link.exe 2^>nul') do if not defined PATH_LINK set "PATH_LINK=%%~fI"
if /I not "!PATH_LINK!"=="!SELECTED_LINK!" (
  echo [8vo] PATH does not resolve the selected x64 link.exe first.
  exit /b 1
)

if not defined OCTAVO_READER0_DIR if defined LECTERN0_READER0_DIR if exist "%LECTERN0_READER0_DIR%\." set "OCTAVO_READER0_DIR=%LECTERN0_READER0_DIR%"
if not defined OCTAVO_UI0_DIR if defined LECTERN0_UI0_DIR if exist "%LECTERN0_UI0_DIR%\." set "OCTAVO_UI0_DIR=%LECTERN0_UI0_DIR%"
if not defined OCTAVO_READERVIEW0_DIR if defined LECTERN0_READERVIEW0_DIR if exist "%LECTERN0_READERVIEW0_DIR%\." set "OCTAVO_READERVIEW0_DIR=%LECTERN0_READERVIEW0_DIR%"
if not defined OCTAVO_GROUND0_DIR if defined OCTAVO_ZERO_FOUNDATION_DIR if exist "%OCTAVO_ZERO_FOUNDATION_DIR%\." set "OCTAVO_GROUND0_DIR=%OCTAVO_ZERO_FOUNDATION_DIR%"
if not defined OCTAVO_READER0_DIR if exist "%ROOT%\local\dependencies\reader0\." set "OCTAVO_READER0_DIR=%ROOT%\local\dependencies\reader0"
if not defined OCTAVO_UI0_DIR if exist "%ROOT%\local\dependencies\ui0\." set "OCTAVO_UI0_DIR=%ROOT%\local\dependencies\ui0"
if not defined OCTAVO_READERVIEW0_DIR if exist "%ROOT%\local\dependencies\readerview0\." set "OCTAVO_READERVIEW0_DIR=%ROOT%\local\dependencies\readerview0"
if not defined OCTAVO_GROUND0_DIR if exist "%ROOT%\local\dependencies\ground0\." set "OCTAVO_GROUND0_DIR=%ROOT%\local\dependencies\ground0"
if not defined OCTAVO_MUPDF_DIR if exist "%ROOT%\local\dependencies\mupdf\." set "OCTAVO_MUPDF_DIR=%ROOT%\local\dependencies\mupdf"
if not defined OCTAVO_READER0_DIR for %%I in ("%ROOT%\..\reader0") do set "OCTAVO_READER0_DIR=%%~fI"
if not defined OCTAVO_UI0_DIR for %%I in ("%ROOT%\..\ui0") do set "OCTAVO_UI0_DIR=%%~fI"
if not defined OCTAVO_READERVIEW0_DIR for %%I in ("%ROOT%\..\readerview0") do set "OCTAVO_READERVIEW0_DIR=%%~fI"
if not defined OCTAVO_MUPDF_DIR if defined MUPDF_DIR set "OCTAVO_MUPDF_DIR=%MUPDF_DIR%"
if not defined OCTAVO_MUPDF_DIR for %%I in ("%ROOT%\..\mupdf") do set "OCTAVO_MUPDF_DIR=%%~fI"
if defined OCTAVO_GROUND0_DIR (
  set "ZF_ROOT=%OCTAVO_GROUND0_DIR%"
) else if defined GROUND0_DIR (
  set "ZF_ROOT=%GROUND0_DIR%"
) else if defined ZERO_FOUNDATION_DIR (
  set "ZF_ROOT=%ZERO_FOUNDATION_DIR%"
) else (
  for %%I in ("%ROOT%\..\ground0") do set "ZF_ROOT=%%~fI"
)
set "OCTAVO_GROUND0_DIR=%ZF_ROOT%"
set "OCTAVO_ZERO_FOUNDATION_DIR=%ZF_ROOT%"
set "MUPDF_ROOT=%OCTAVO_MUPDF_DIR%"
set "READER0_GROUND0_DIR=%ZF_ROOT%"
set "READER0_MUPDF_DIR=%MUPDF_ROOT%"

if not exist "%MUPDF_ROOT%\include\mupdf\fitz.h" (
  echo [8vo] Missing pinned MuPDF source at "%MUPDF_ROOT%".
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\require_dependencies_current.ps1" -Target Win32Pdf
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\audit_architecture.ps1"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File "%OCTAVO_READER0_DIR%\scripts\build_mupdf_pdf_core_win32.ps1" -MupdfDir "%MUPDF_ROOT%"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\audit_win32_pdf_provenance.ps1" -Reader0Dir "%OCTAVO_READER0_DIR%" -MupdfDir "%MUPDF_ROOT%" -CompilerPath "!SELECTED_CL!" -LinkerPath "!SELECTED_LINK!"
if errorlevel 1 exit /b 1

set "MUPDF_CORE_LIB=%OCTAVO_READER0_DIR%\build\mupdf-pdf-core\x64\Release\reader0_mupdf_pdf.lib"
set "MUPDF_THIRD_LIB=%MUPDF_ROOT%\platform\win32\x64\Release\libthirdparty.lib"
set "MUPDF_RESOURCES_LIB=%MUPDF_ROOT%\platform\win32\x64\Release\libresources.lib"
for %%L in ("%MUPDF_CORE_LIB%" "%MUPDF_THIRD_LIB%" "%MUPDF_RESOURCES_LIB%") do if not exist "%%~L" (
  echo [8vo] Missing PDF archive: "%%~L".
  exit /b 1
)

set "SRC_UNITY=%ROOT%\code\build.c"
set "OUT_DIR=%ROOT%\build\win32"
set "EXE_NAME=8vo.exe"
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

pushd "%OUT_DIR%"
echo [8vo] Compiling native reader host
"!SELECTED_CL!" /nologo /std:c11 /W4 /WX /Zi /O2 /MD /DUNICODE /D_UNICODE ^
  /DREADER0_WITH_MUPDF=1 ^
  /wd4005 /wd4127 /wd5105 /I "%ROOT%\code" /I "%ZF_ROOT%\code" ^
  /I "%OCTAVO_READER0_DIR%\code" /I "%OCTAVO_UI0_DIR%\code" ^
  /I "%OCTAVO_READERVIEW0_DIR%\code" /I "%MUPDF_ROOT%\include" ^
  /c /Fo"8vo.obj" "%SRC_UNITY%"
if errorlevel 1 (
  echo [8vo] Compile failed.
  popd
  exit /b 1
)
echo [8vo] Linking with exact verified x64 link.exe
"!SELECTED_LINK!" /nologo /OUT:"%EXE_NAME%" /MACHINE:X64 /DEBUG ^
  /PDB:"8vo.pdb" /LTCG /STACK:16777216 /OPT:REF /OPT:ICF ^
  /INCLUDE:fz_new_search /MAP:"8vo.map" "8vo.obj" ^
  "%MUPDF_CORE_LIB%" "%MUPDF_THIRD_LIB%" "%MUPDF_RESOURCES_LIB%" ^
  user32.lib gdi32.lib dwrite.lib ole32.lib oleaut32.lib oleacc.lib ^
  comdlg32.lib windowscodecs.lib uuid.lib shell32.lib winmm.lib
if errorlevel 1 (
  echo [8vo] Build failed.
  popd
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%OCTAVO_READER0_DIR%\scripts\audit_mupdf_pdf_link_map.ps1" -MapPath "%OUT_DIR%\8vo.map"
if errorlevel 1 (
  echo [8vo] Final PDF link-map audit failed.
  popd
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\write_win32_pdf_build_provenance.ps1" -RepoRoot "%ROOT%" -Reader0Dir "%OCTAVO_READER0_DIR%" -MupdfDir "%MUPDF_ROOT%" -CompilerPath "!SELECTED_CL!" -LinkerPath "!SELECTED_LINK!" -ExePath "%OUT_DIR%\%EXE_NAME%" -MapPath "%OUT_DIR%\8vo.map" -OutputPath "%OUT_DIR%\8vo_pdf.provenance.json" !PROVENANCE_STATE_ARGS!
if errorlevel 1 (
  echo [8vo] Final PDF artifact provenance failed.
  popd
  exit /b 1
)
popd
echo [8vo] Build succeeded.

if "%1"=="no_run" endlocal & exit /b 0
if "%1"=="" (
  "%OUT_DIR%\%EXE_NAME%"
) else (
  "%OUT_DIR%\%EXE_NAME%" %*
)
set EXITCODE=%ERRORLEVEL%
endlocal & exit /b %EXITCODE%

:find_vcvarsall
set VCVARSALL=
if defined OCTAVO_VCVARS set "VCVARSALL=%OCTAVO_VCVARS%"
if not defined VCVARSALL if defined LECTERN0_VCVARS set "VCVARSALL=%LECTERN0_VCVARS%"
if not defined VCVARSALL if defined RE10_VCVARS set "VCVARSALL=%RE10_VCVARS%"
if not defined VCVARSALL (
  set "VSWHERE=!PF86!\Microsoft Visual Studio\Installer\vswhere.exe"
  if exist "!VSWHERE!" (
    for /f "usebackq delims=" %%I in (`"!VSWHERE!" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSROOT=%%I"
    if defined VSROOT set "VCVARSALL=!VSROOT!\VC\Auxiliary\Build\vcvarsall.bat"
  )
)
if not defined VCVARSALL call :try_vcvars "!PF!\Microsoft Visual Studio\2022\Community"
if not defined VCVARSALL call :try_vcvars "!PF!\Microsoft Visual Studio\2022\Professional"
if not defined VCVARSALL call :try_vcvars "!PF!\Microsoft Visual Studio\2022\Enterprise"
if not defined VCVARSALL call :try_vcvars "!PF!\Microsoft Visual Studio\2022\BuildTools"
if defined VCVARSALL exit /b 0
exit /b 1

:try_vcvars
set "CANDIDATE=%~1"
if exist "%CANDIDATE%\VC\Auxiliary\Build\vcvarsall.bat" set "VCVARSALL=%CANDIDATE%\VC\Auxiliary\Build\vcvarsall.bat"
exit /b 0
