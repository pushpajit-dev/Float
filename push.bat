@echo off
setlocal
echo ========================================================
echo   Float - GitHub Sync ^& Push Script
echo ========================================================
echo.

git remote -v
echo.
echo Pushing branch 'main' to GitHub origin...
git push -u origin main

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] Float repository pushed successfully to GitHub!
) else (
    echo.
    echo [NOTE] If your repository has a different name or URL:
    echo   git remote set-url origin https://github.com/pushpajit-dev/YOUR_REPO_NAME.git
    echo   git push -u origin main
)
echo.
pause
