@echo off
setlocal enabledelayedexpansion

set "PF=%ProgramFiles%"
set "PF86=%ProgramFiles(x86)%"

where cl >nul 2>nul
if errorlevel 1 (
  call :find_vcvarsall
  if errorlevel 1 (
    echo [lectern0] Failed to locate vcvarsall.bat. Install MSVC or set LECTERN0_VCVARS.
    exit /b 1
  )
  call "!VCVARSALL!" x64
  if errorlevel 1 exit /b 1
)

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"
if not defined LECTERN0_READER0_DIR for %%I in ("%ROOT%\..\reader0") do set "LECTERN0_READER0_DIR=%%~fI"
if not defined LECTERN0_UI0_DIR for %%I in ("%ROOT%\..\ui0") do set "LECTERN0_UI0_DIR=%%~fI"
if defined LECTERN0_ZERO_FOUNDATION_DIR (
  set "ZF_ROOT=%LECTERN0_ZERO_FOUNDATION_DIR%"
) else if defined ZERO_FOUNDATION_DIR (
  set "ZF_ROOT=%ZERO_FOUNDATION_DIR%"
) else (
  for %%I in ("%ROOT%\..\zero_foundation") do set "ZF_ROOT=%%~fI"
)
set "LECTERN0_ZERO_FOUNDATION_DIR=%ZF_ROOT%"

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\require_dependencies_current.ps1"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\audit_architecture.ps1"
if errorlevel 1 exit /b 1

set "SRC_UNITY=%ROOT%\code\build.c"
set "OUT_DIR=%ROOT%\build\win32"
set "EXE_NAME=lectern0.exe"
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

pushd "%OUT_DIR%"
echo [lectern0] Compiling native EPUB host
cl /nologo /std:c11 /W4 /WX /Zi /Od /MD /DUNICODE /D_UNICODE ^
  /wd4005 /wd4127 /wd5105 /I "%ROOT%\code" /I "%ZF_ROOT%\code" ^
  /I "%LECTERN0_READER0_DIR%\code" /I "%LECTERN0_UI0_DIR%\code" ^
  /Fe"%EXE_NAME%" "%SRC_UNITY%" ^
  /link /STACK:16777216 user32.lib gdi32.lib dwrite.lib ole32.lib oleaut32.lib ^
  comdlg32.lib windowscodecs.lib uuid.lib shell32.lib
if errorlevel 1 (
  echo [lectern0] Build failed.
  popd
  exit /b 1
)
popd
echo [lectern0] Build succeeded.

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
if defined LECTERN0_VCVARS set "VCVARSALL=%LECTERN0_VCVARS%"
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
