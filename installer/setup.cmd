@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" %*
set "result=%errorlevel%"
echo.
if not "%result%"=="0" echo Einrichtung fehlgeschlagen. Bitte die Meldung oben beachten.
pause
exit /b %result%
