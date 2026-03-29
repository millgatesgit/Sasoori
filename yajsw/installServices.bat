@echo off
:: Sasoori — install both servers as Windows Services (run as Administrator)

set YAJSW=%~dp0yajsw-stable-13.18
set JAVA_HOME=C:\Program Files\Java\jdk-17

echo [Sasoori] Installing Windows Services...

echo Installing SasooriBackend service...
call "%YAJSW%\bat\installService.bat" --conf "%~dp0conf\wrapper.backend.conf"

echo Installing SasooriFrontend service...
call "%YAJSW%\bat\installService.bat" --conf "%~dp0conf\wrapper.frontend.conf"

echo.
echo [Sasoori] Services installed. Start with:
echo   net start SasooriBackend
echo   net start SasooriFrontend
echo.
echo Or use: startServices.bat
