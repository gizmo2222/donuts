@echo off
setlocal
rem Creates a NEW upload keystore for Google Play (use when the old password is lost).
rem Run from Command Prompt, PowerShell, or by double-clicking. Type a new password when asked
rem and WRITE IT DOWN somewhere safe (a password manager). Produces:
rem   donuts-upload.jks         the new keystore (keep it, never commit it)
rem   upload_certificate.pem    the public certificate to attach to the Play Console reset request
set "KT=C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
if not exist "%KT%" (
  echo keytool was not found at "%KT%". Install Android Studio or edit KT in this file.
  exit /b 1
)
cd /d "%~dp0"
if exist donuts-upload.jks (
  echo donuts-upload.jks already exists here. Move or rename it first so it is not overwritten.
  exit /b 1
)
echo Step 1 of 2: creating donuts-upload.jks. Choose a new password and type it when asked.
echo (keytool asks for it twice, then asks a few optional questions; press Enter to skip them,
echo  and answer yes at the end.)
echo.
"%KT%" -genkeypair -v -keystore donuts-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Donuts for Steven, O=Donuts, C=US"
if errorlevel 1 ( echo. & echo Key creation failed. Nothing to upload yet. & goto :end )
echo.
echo Step 2 of 2: exporting the public certificate. Type the same password again.
"%KT%" -exportcert -rfc -keystore donuts-upload.jks -alias upload -file upload_certificate.pem
if errorlevel 1 ( echo. & echo Certificate export failed. & goto :end )
echo.
echo Done. Attach upload_certificate.pem to the "Request upload key reset" form in Play Console
echo (Protected with Play, Play Store protection, Manage Play app signing). When Google confirms, put in local.properties:
echo   KEYSTORE_PATH=F:/dev/donuts/donuts-upload.jks
echo   KEY_ALIAS=upload
echo   KEYSTORE_PASSWORD and KEY_PASSWORD = the password you just chose
:end
echo %cmdcmdline% | find /i "/c" >nul && pause
