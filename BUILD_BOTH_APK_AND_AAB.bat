@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

echo ============================================================================
echo   Crystal Front: Mobile Command - Building BOTH APK ^& Play Store AAB
echo ============================================================================
echo.

:: Ensure local.properties exists
if not exist "local.properties" (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        echo sdk.dir=%LOCALAPPDATA:\=/%/Android/Sdk> local.properties
    )
)

echo [1/2] Building Signed Release APK (for phone installation)...
call gradlew.bat assembleRelease --stacktrace

echo.
echo [2/2] Building Signed Play Store AAB (for Google Play Console)...
call gradlew.bat bundleRelease --stacktrace

echo.
echo ============================================================================
echo   BUILD SUMMARY
echo ============================================================================
if exist "app\build\outputs\apk\release\app-release.apk" (
    copy /y "app\build\outputs\apk\release\app-release.apk" "%CD%\CrystalFront_Release.apk" >nul
    echo   [SUCCESS] Installable APK: %CD%\CrystalFront_Release.apk
) else (
    echo   [ERROR] Release APK was not generated. Check log above.
)

if exist "app\build\outputs\bundle\release\app-release.aab" (
    copy /y "app\build\outputs\bundle\release\app-release.aab" "%CD%\CrystalFront_PlayStore.aab" >nul
    echo   [SUCCESS] Play Store AAB:  %CD%\CrystalFront_PlayStore.aab
) else (
    echo   [ERROR] Play Store AAB was not generated. Check log above.
)
echo ============================================================================
echo.
explorer.exe "%CD%"
pause
