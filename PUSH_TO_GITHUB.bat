@echo off
setlocal EnableDelayedExpansion

echo ============================================================================
echo   Crystal Front v4.0 (Native 3D Engine) - Auto Git Init ^& GitHub Push
echo ============================================================================
echo.

where git >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Git is not installed or not in your PATH.
    pause
    exit /b 1
)

:: 1. Remember the folder where this script was launched
set "LAUNCH_DIR=%~dp0"

:: 2. Try to rescue any existing GitHub remote URL before changing directories
set "SAVED_URL="
if exist "%LAUNCH_DIR%.git" (
    pushd "%LAUNCH_DIR%"
    for /f "delims=" %%U in ('git remote get-url origin 2^>nul') do set "SAVED_URL=%%U"
    popd
)
if not defined SAVED_URL (
    if exist "C:\Users\JamesHowrey\Desktop\gamedev\.git" (
        pushd "C:\Users\JamesHowrey\Desktop\gamedev"
        for /f "delims=" %%U in ('git remote get-url origin 2^>nul') do set "SAVED_URL=%%U"
        popd
    )
)

:: 3. Switch to C:\Users\JamesHowrey\Desktop\gamedev (or stay in launch folder)
if exist "C:\Users\JamesHowrey\Desktop\gamedev" (
    cd /d "C:\Users\JamesHowrey\Desktop\gamedev"
    echo [OK] Target Project Folder: C:\Users\JamesHowrey\Desktop\gamedev
) else (
    cd /d "%LAUNCH_DIR%"
    echo [OK] Target Project Folder: %CD%
)

:: 4. If this script was launched from an extracted zip folder (e.g. Downloads\crystal_front...), copy all v4.0 files into target folder
if exist "%LAUNCH_DIR%app\src\main\assets\index.html" (
    if /i not "%LAUNCH_DIR%"=="%CD%\" (
        echo [SYNC] Copying v4.0 Native 3D Engine files from "%LAUNCH_DIR%" into "%CD%"...
        xcopy "%LAUNCH_DIR%*" "%CD%\" /E /Y /Q >nul 2>nul
    )
)

:: Also check any crystal_front_android_playstore_project* subfolders in Desktop\gamedev or Downloads
for /d %%D in ("crystal_front_android_playstore_project*" "%USERPROFILE%\Downloads\crystal_front_android_playstore_project*") do (
    if exist "%%~fD\app\src\main\assets\index.html" (
        echo [SYNC] Copying v4.0 Native 3D Engine files from "%%~fD"...
        xcopy "%%~fD\*" "%CD%\" /E /Y /Q >nul 2>nul
    )
)

:: 5. ALWAYS initialize Git repository so "fatal: not a git repository" NEVER happens!
echo [GIT] Ensuring Git repository is initialized in %CD%...
git init

:: Ensure local git user name and email are configured so commit never fails
git config user.name >nul 2>nul
if %ERRORLEVEL% NEQ 0 git config user.name "James Howrey"
git config user.email >nul 2>nul
if %ERRORLEVEL% NEQ 0 git config user.email "jrhowrey@gmail.com"

:: 6. Clean any legacy .gradle.kts, .kt, and shader files
echo [CLEAN] Removing any legacy .gradle.kts and .kt files...
del /f /q "settings.gradle.kts" 2>nul
del /f /q "build.gradle.kts" 2>nul
del /f /q "app\build.gradle.kts" 2>nul
del /f /q "app\src\main\java\com\crystalfront\mobilecommand\MainActivity.kt" 2>nul
del /f /q "app\src\main\assets\index.html" 2>nul
del /f /q "app\src\main\java\com\crystalfront\mobilecommand\HudOverlayView.java" 2>nul
del /f /q "app\src\main\java\com\crystalfront\mobilecommand\MeshBuilder3D.java" 2>nul
del /f /q "app\src\main\java\com\crystalfront\mobilecommand\GameState.java" 2>nul
del /f /q "app\src\main\java\com\crystalfront\mobilecommand\SoundEngine.java" 2>nul
if exist "app\src\main\java\com\tiberianmobile" rmdir /s /q "app\src\main\java\com\tiberianmobile" 2>nul
if exist "app\src\main\shaders" rmdir /s /q "app\src\main\shaders" 2>nul

:: 7. Write clean 48-line app\build.gradle
echo [FIX] Writing clean app\build.gradle...
if not exist "app" mkdir "app"
(
echo plugins {
echo     id 'com.android.application'
echo }
echo.
echo android {
echo     namespace 'com.crystalfront.mobilecommand'
echo     compileSdk 34
echo.
echo     defaultConfig {
echo         applicationId "com.crystalfront.mobilecommand"
echo         minSdk 24
echo         targetSdk 34
echo         versionCode 5
echo         versionName "5.0.0-3d-vtol"
echo     }
echo.
echo     sourceSets {
echo         main {
echo             shaders.srcDirs = []
echo         }
echo     }
echo.
echo     buildTypes {
echo         release {
echo             minifyEnabled false
echo             shrinkResources false
echo             signingConfig signingConfigs.debug
echo         }
echo         debug {
echo             applicationIdSuffix ".debug"
echo         }
echo     }
echo.
echo     lint {
echo         checkReleaseBuilds false
echo         abortOnError false
echo     }
echo.
echo     compileOptions {
echo         sourceCompatibility JavaVersion.VERSION_17
echo         targetCompatibility JavaVersion.VERSION_17
echo     }
echo }
echo.
echo dependencies {
echo }
) > "app\build.gradle"

:: 8. Write clean root build.gradle and settings.gradle
(
echo plugins {
echo     id 'com.android.application' version '8.5.2' apply false
echo }
) > "build.gradle"

(
echo pluginManagement {
echo     repositories {
echo         google^(^)
echo         mavenCentral^(^)
echo         gradlePluginPortal^(^)
echo     }
echo }
echo dependencyResolutionManagement {
echo     repositoriesMode.set^(RepositoriesMode.FAIL_ON_PROJECT_REPOS^)
echo     repositories {
echo         google^(^)
echo         mavenCentral^(^)
echo     }
echo }
echo rootProject.name = "CrystalFrontMobileCommand"
echo include ':app'
) > "settings.gradle"

:: 9. Write GitHub Actions workflow (.github\workflows\build-apk-aab.yml)
if not exist ".github\workflows" mkdir ".github\workflows"
(
echo name: Build Signed Android APK ^& Play Store AAB
echo.
echo on:
echo   push:
echo     branches: [ "main", "master" ]
echo   workflow_dispatch:
echo.
echo jobs:
echo   build-android:
echo     runs-on: ubuntu-latest
echo     steps:
echo       - name: Checkout Source
echo         uses: actions/checkout@v4
echo.
echo       - name: Clean Legacy Files
echo         run: ^|
echo           rm -f settings.gradle.kts build.gradle.kts app/build.gradle.kts
echo           rm -rf app/src/main/java/com/tiberianmobile
echo           rm -f app/src/main/java/com/crystalfront/mobilecommand/MainActivity.kt
echo           rm -rf app/src/main/shaders
echo.
echo       - name: Set up JDK 17
echo         uses: actions/setup-java@v4
echo         with:
echo           java-version: '17'
echo           distribution: 'temurin'
echo.
echo       - name: Setup Gradle 8.7
echo         uses: gradle/actions/setup-gradle@v3
echo         with:
echo           gradle-version: '8.7'
echo.
echo       - name: Build Release APK ^& Google Play AAB
echo         run: ^|
echo           gradle assembleRelease bundleRelease --no-daemon --stacktrace
echo.
echo       - name: Upload Signed Release APK
echo         uses: actions/upload-artifact@v4
echo         with:
echo           name: CrystalFront-Release-APK
echo           path: app/build/outputs/apk/release/*.apk
echo.
echo       - name: Upload Google Play Store Bundle (AAB)
echo         uses: actions/upload-artifact@v4
echo         with:
echo           name: CrystalFront-PlayStore-AAB
echo           path: app/build/outputs/bundle/release/*.aab
) > ".github\workflows\build-apk-aab.yml"

:: 10. Resolve GitHub remote URL, commit, and force-push
set "REPO_URL="
for /f "delims=" %%U in ('git remote get-url origin 2^>nul') do set "REPO_URL=%%U"
if not defined REPO_URL set "REPO_URL=!SAVED_URL!"

if defined REPO_URL (
    echo [GIT] Found GitHub remote URL: !REPO_URL!
) else (
    echo.
    echo Please paste your GitHub Repository URL ^(e.g. https://github.com/YourUsername/crystal-front-mobile.git^):
    set /p REPO_URL="Repository URL: "
)

if "!REPO_URL!"=="" (
    echo [ERROR] No GitHub URL provided. Please run again and paste your repository URL.
    pause
    exit /b 1
)

echo.
echo [GIT] Staging all files and committing v4.0 Native 3D Engine...
git add -A
git commit -m "Crystal Front v4.0: Native Android OpenGL ES 3.0 3D Engine + Clean Gradle Build"
git branch -M main
git remote remove origin 2>nul
git remote add origin !REPO_URL!

echo [GIT] Force-pushing to !REPO_URL!...
git push -u origin main --force

echo.
if %ERRORLEVEL% EQU 0 (
    echo ============================================================================
    echo   [SUCCESS] PUSHED TO GITHUB!
    echo   Go to your GitHub repo -> Actions tab -> click the top run ->
    echo   scroll to the bottom "Artifacts" section to download your APK and AAB!
    echo ============================================================================
) else (
    echo ============================================================================
    echo   [ERROR] Push failed. Please verify the repository URL above.
    echo ============================================================================
)
pause
