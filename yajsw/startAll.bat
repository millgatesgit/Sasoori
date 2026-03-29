@echo off
:: Sasoori — start both backend and frontend via YAJSW (console mode)
:: Each server runs in its own window.

set YAJSW=%~dp0yajsw-stable-13.18
set JAVA_HOME=C:\Program Files\Java\jdk-17

echo [Sasoori] Starting backend (Jetty on port 9090)...
start "Sasoori Backend" cmd /k ""%YAJSW%\bat\runConsole.bat" --conf "%~dp0conf\wrapper.backend.conf""

timeout /t 3 /nobreak >nul

echo [Sasoori] Starting frontend (serve on port 3000)...
start "Sasoori Frontend" cmd /k ""%YAJSW%\bat\runConsole.bat" --conf "%~dp0conf\wrapper.frontend.conf""

echo.
echo [Sasoori] Both servers started.
echo   Backend:  http://localhost:9090/api/v1/products
echo   Frontend: http://localhost:3000
