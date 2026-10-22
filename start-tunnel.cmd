@echo off
title Racehorse Backend Tunnel
echo ================================================================
echo DANG TAO DUONG HAM HTTPS CHO BACKEND (PORT 8080)
echo ================================================================
echo.
echo Hay dam bao Database va Backend da duoc bat truoc tai http://localhost:8080
echo.
echo Dang lay dia chi HTTPS cong khai...
echo Copy dia chi "your url is: https://..." ben duoi de dan vao VITE_API_URL tren Vercel!
echo ================================================================
echo.
:loop
npx --yes localtunnel --port 8080 --subdomain racehorse-club-api
echo.
echo [!] Mat ket noi, dang tu dong ket noi lai sau 3 giay...
timeout /t 3 /nobreak >nul
goto loop
