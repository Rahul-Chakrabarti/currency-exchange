@echo off
title Currency Exchange Analyzer

echo.
echo  ================================================
echo   Currency Exchange Analyzer
echo   Starting... this may take 30 seconds.
echo   First-ever run takes 3-5 minutes to build.
echo  ================================================
echo.

:: Start Docker Compose
docker compose up --build -d

echo  Waiting for the app to be ready...
echo.

:: Windows 10 does not have curl built in.
:: Use PowerShell to poll localhost:8080 instead.
:WAIT
timeout /t 3 /nobreak >nul
powershell -Command "try { $r = Invoke-WebRequest -Uri http://localhost:8080 -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if errorlevel 1 goto WAIT

echo  App is ready!
echo.

:: Open the browser
start http://localhost:8080

echo  Browser opened at http://localhost:8080
echo.
echo  Press Ctrl+C to stop the application.
echo  Or close this window to stop.
echo.

:: Stream logs
docker compose logs -f