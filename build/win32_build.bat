@echo off
setlocal enabledelayedexpansion

set "PF=%ProgramFiles%"
set "PF86=%ProgramFiles(x86)%"

where cl >nul 2>nul
if errorlevel 1 (
  call :find_vcvarsall
  if errorlevel 1 (
    echo [8vo] Failed to locate vcvarsall.bat. Install MSVC or set OCTAVO_VCVARS.
    exit /b 1
  )
  call "!VCVARSALL!" x64
  if errorlevel 1 exit /b 1
)

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"
if not defined OCTAVO_READER0_DIR if defined LECTERN0_READER0_DIR if exist "%LECTERN0_READER0_DIR%\." set "OCTAVO_READER0_DIR=%LECTERN0_READER0_DIR%"
if not defined OCTAVO_UI0_DIR if defined LECTERN0_UI0_DIR if exist "%LECTERN0_UI0_DIR%\." set "OCTAVO_UI0_DIR=%LECTERN0_UI0_DIR%"
if not defined OCTAVO_READERVIEW0_DIR if defined LECTERN0_READERVIEW0_DIR if exist "%LECTERN0_READERVIEW0_DIR%\." set "OCTAVO_READERVIEW0_DIR=%LECTERN0_READERVIEW0_DIR%"
if not defined OCTAVO_GROUND0_DIR if defined OCTAVO_ZERO_FOUNDATION_DIR if exist "%OCTAVO_ZERO_FOUNDATION_DIR%\." set "OCTAVO_GROUND0_DIR=%OCTAVO_ZERO_FOUNDATION_DIR%"
if not defined OCTAVO_GROUND0_DIR if defined LECTERN0_ZERO_FOUNDATION_DIR if exist "%LECTERN0_ZERO_FOUNDATION_DIR%\." set "OCTAVO_GROUND0_DIR=%LECTERN0_ZERO_FOUNDATION_DIR%"
if not defined OCTAVO_READER0_DIR if exist "%ROOT%\local\dependencies\reader0\." set "OCTAVO_READER0_DIR=%ROOT%\local\dependencies\reader0"
if not defined OCTAVO_UI0_DIR if exist "%ROOT%\local\dependencies\ui0\." set "OCTAVO_UI0_DIR=%ROOT%\local\dependencies\ui0"
if not defined OCTAVO_READERVIEW0_DIR if exist "%ROOT%\local\dependencies\readerview0\." set "OCTAVO_READERVIEW0_DIR=%ROOT%\local\dependencies\readerview0"
if not defined OCTAVO_GROUND0_DIR if exist "%ROOT%\local\dependencies\ground0\." set "OCTAVO_GROUND0_DIR=%ROOT%\local\dependencies\ground0"
if not defined OCTAVO_READER0_DIR for %%I in ("%ROOT%\..\reader0") do set "OCTAVO_READER0_DIR=%%~fI"
if not defined OCTAVO_UI0_DIR for %%I in ("%ROOT%\..\ui0") do set "OCTAVO_UI0_DIR=%%~fI"
if not defined OCTAVO_READERVIEW0_DIR for %%I in ("%ROOT%\..\readerview0") do set "OCTAVO_READERVIEW0_DIR=%%~fI"
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

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\require_dependencies_current.ps1"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\audit_architecture.ps1"
if errorlevel 1 exit /b 1

set "SRC_UNITY=%ROOT%\code\build.c"
set "OUT_DIR=%ROOT%\build\win32"
set "EXE_NAME=8vo.exe"
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

pushd "%OUT_DIR%"
echo [8vo] Compiling native reader host
cl /nologo /std:c11 /W4 /WX /Zi /Od /MD /DUNICODE /D_UNICODE ^
  /wd4005 /wd4127 /wd5105 /I "%ROOT%\code" /I "%ZF_ROOT%\code" ^
  /I "%OCTAVO_READER0_DIR%\code" /I "%OCTAVO_UI0_DIR%\code" ^
  /I "%OCTAVO_READERVIEW0_DIR%\code" ^
  /Fe"%EXE_NAME%" "%SRC_UNITY%" ^
  /link /STACK:16777216 user32.lib gdi32.lib dwrite.lib ole32.lib oleaut32.lib ^
  oleacc.lib comdlg32.lib windowscodecs.lib uuid.lib shell32.lib winmm.lib
if errorlevel 1 (
  echo [8vo] Build failed.
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
