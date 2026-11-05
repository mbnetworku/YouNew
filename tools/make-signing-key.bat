@echo off
rem Creates the YouTv Plus signing key and opens the GitHub page to store it as secrets.
rem Runs as administrator (asks with the Windows prompt when needed).
net session >nul 2>&1
if %errorlevel% neq 0 (
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0make-signing-key.ps1"
pause
