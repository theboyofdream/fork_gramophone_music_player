@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
call gradlew.bat assembleDebug assembleRelease
if %ERRORLEVEL% EQU 0 (
    echo.
    echo BUILD SUCCESSFUL
    echo Debug APK:   app\build\outputs\apk\debug\
    echo Release APK: app\build\outputs\apk\release\
) else (
    echo.
    echo BUILD FAILED
    exit /b 1
)
