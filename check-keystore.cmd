@echo off
setlocal
rem Checks whether a password opens the release keystore. Works from Command Prompt, PowerShell, or by double-clicking.
set "KT=C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
if not exist "%KT%" (
  echo keytool was not found at "%KT%". Install Android Studio or edit KT in this file.
  exit /b 1
)
cd /d "%~dp0"
echo Type the keystore password when asked. A line ending in PrivateKeyEntry means it is correct.
echo.
"%KT%" -list -keystore donuts-release.jks
echo.
echo %cmdcmdline% | find /i "/c" >nul && pause
