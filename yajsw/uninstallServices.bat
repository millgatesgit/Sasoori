@echo off
:: Sasoori — remove both Windows Services (run as Administrator)

set YAJSW=%~dp0yajsw-stable-13.18

call "%YAJSW%\bat\uninstallService.bat" --conf "%~dp0conf\wrapper.backend.conf"
call "%YAJSW%\bat\uninstallService.bat" --conf "%~dp0conf\wrapper.frontend.conf"
echo [Sasoori] Services uninstalled.
