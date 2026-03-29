@echo off
:: Sasoori — start both installed Windows Services
net start SasooriBackend
net start SasooriFrontend
echo.
echo Backend:  http://localhost:9090/api/v1/products
echo Frontend: http://localhost:3000
