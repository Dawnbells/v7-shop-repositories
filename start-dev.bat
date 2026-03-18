@echo off
setlocal

set "ROOT=E:\V7Soft\Repositories\v7-shop-repositories"
set "TARGET=%~1"

if "%TARGET%"=="" goto :usage

if /I "%TARGET%"=="mall" (
    set "PROJECT=v7-shop-mall"
    goto :run
)

if /I "%TARGET%"=="admin" (
    set "PROJECT=v7-shop-admin"
    goto :run
)

echo [ERROR] Unknown target: %TARGET%
goto :usage

:run
start "%PROJECT%" powershell -NoExit -Command ^
"cd '%ROOT%\%PROJECT%'; ^
$env:NODE_OPTIONS='--use-env-proxy'; ^
$env:HTTP_PROXY='http://127.0.0.1:8800'; ^
$env:HTTPS_PROXY='http://127.0.0.1:8800'; ^
$env:http_proxy='http://127.0.0.1:8800'; ^
$env:https_proxy='http://127.0.0.1:8800'; ^
Write-Host 'Starting %PROJECT%...'; ^
pnpm dev"

exit /b 0

:usage
echo Usage:
echo   start-dev.bat mall
echo   start-dev.bat admin
exit /b 1