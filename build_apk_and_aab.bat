@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

echo ============================================================================
echo   Crystal Front: Mobile Command v2.0 (AGDK + Vulkan TBDR + C++20 NDK)
echo   One-Click Self-Healing Android Studio ^& Gradle APK/AAB Compiler
echo ============================================================================

:: 1. Clean any stale v1 duplicate package directory (com.tiberianmobile.commandops)
if exist "app\src\main\java\com\tiberianmobile" (
    echo [CLEAN] Removing legacy v1 duplicate package folder app\src\main\java\com\tiberianmobile...
    rmdir /s /q "app\src\main\java\com\tiberianmobile"
)
if exist "app\build" (
    echo [CLEAN] Clearing stale app\build cache...
    rmdir /s /q "app\build"
)

:: 2. Auto-detect Android Studio bundled JDK (JBR)
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    set "PATH=!JAVA_HOME!\bin;!PATH!"
    echo [OK] Using Android Studio Bundled JDK: !JAVA_HOME!
) else if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
    set "PATH=!JAVA_HOME!\bin;!PATH!"
    echo [OK] Using Android Studio Bundled JDK: !JAVA_HOME!
)

:: 3. Auto-detect Android SDK and write local.properties
if not defined ANDROID_HOME (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    ) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk" (
        set "ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk"
    )
)

if defined ANDROID_HOME (
    echo [OK] Found Android Studio SDK: !ANDROID_HOME!
    set "ESCAPED_SDK=!ANDROID_HOME:\=\\!"
    echo sdk.dir=!ESCAPED_SDK!> local.properties
)

:: 4. Run Gradle Wrapper to compile Release APK and Play Store AAB
echo [BUILD] Starting Gradle Release Build (assembleRelease bundleRelease)...
call gradlew.bat assembleRelease bundleRelease --stacktrace

if exist "app\build\outputs\apk\release\app-release.apk" (
    echo.
    echo ============================================================================
    echo   BUILD SUCCESSFUL!
    echo   Signed APK:     %~dp0app\build\outputs\apk\release\app-release.apk
    echo   Play Store AAB: %~dp0app\build\outputs\bundle\release\app-release.aab
    echo ============================================================================
    explorer "%~dp0app\build\outputs"
) else (
    echo.
    echo [INFO] Opening project in Android Studio...
    if exist "C:\Program Files\Android\Android Studio\bin\studio64.exe" (
        start "" "C:\Program Files\Android\Android Studio\bin\studio64.exe" "%~dp0"
    ) else if exist "%LOCALAPPDATA%\Programs\Android Studio\bin\studio64.exe" (
        start "" "%LOCALAPPDATA%\Programs\Android Studio\bin\studio64.exe" "%~dp0"
    )
)
pause
