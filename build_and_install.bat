@echo off
REM filepath: c:\Users\DELL\AndroidStudioProjects\WallHack\build_and_install.bat
cd /d c:\Users\DELL\AndroidStudioProjects\WallHack

echo Cleaning the project...
call gradlew.bat clean

echo Building the app...
call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo Build failed. Please check the error messages above.
    exit /b 1
)

set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK_PATH%" (
    echo APK file not found at %APK_PATH%. Build might have failed.
    exit /b 1
)

echo Installing the APK on the connected Android device...
adb install -r "%APK_PATH%"

if %ERRORLEVEL% NEQ 0 (
    echo APK installation failed. Please ensure USB debugging is enabled and the device is connected.
    exit /b 1
)

echo Build and installation successful!
exit /b 0