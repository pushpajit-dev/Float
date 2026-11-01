@echo off
start "" "%~dp0Float.exe"
timeout /t 1 /nobreak >nul
start "" "http://localhost:3000"
