@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

:: 1. Locate JAVA_HOME from Android Studio JBR if not set
if not defined JAVA_HOME (
    if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    ) else if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
    )
)
if defined JAVA_HOME set "PATH=!JAVA_HOME!\bin;!PATH!"

:: 2. Bootstrap gradle-wrapper.jar if missing
if not exist "gradle\wrapper" mkdir "gradle\wrapper"
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo [Gradle Wrapper] Downloading official gradle-wrapper.jar...
    where curl.exe >nul 2>nul
    if !ERRORLEVEL! EQU 0 (
        curl.exe -fSL "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" -o "gradle\wrapper\gradle-wrapper.jar"
    )
    if not exist "gradle\wrapper\gradle-wrapper.jar" (
        powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'"
    )
)

:: 3. Execute Gradle Wrapper or fallback to cached Gradle distribution in %USERPROFILE%\.gradle
if exist "gradle\wrapper\gradle-wrapper.jar" (
    java.exe -Xmx64m -Xms64m -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
    exit /b !ERRORLEVEL!
) else (
    for /d %%D in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-*") do (
        for /d %%H in ("%%D\*") do (
            for /d %%G in ("%%H\gradle-*") do (
                if exist "%%G\bin\gradle.bat" (
                    call "%%G\bin\gradle.bat" %*
                    exit /b !ERRORLEVEL!
                )
            )
        )
    )
    echo [ERROR] Could not download gradle-wrapper.jar or find cached Gradle.
    exit /b 1
)
