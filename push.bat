@echo off
setlocal
echo ========================================================
echo   Float - GitHub Sync ^& Push Script
echo ========================================================
echo.

git status
echo.
echo Pushing branch 'main' to GitHub (https://github.com/pushpajit-dev/Float.git)...
git push origin main

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] Float repository pushed successfully to GitHub!
) else (
    echo.
    echo [ERROR] Push failed. Check your internet connection or git credentials.
)
echo.
pause
