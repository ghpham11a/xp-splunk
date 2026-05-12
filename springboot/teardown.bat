@echo off
echo ============================================
echo  xp-splunk Spring Boot - K8s Teardown
echo ============================================
echo.

cd /d "%~dp0"

:: Kill port-forward
echo [1/3] Stopping port-forward...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8080.*LISTENING" 2^>nul') do (
    taskkill /PID %%a /F >nul 2>&1
)
echo       Port-forward stopped.
echo.

:: Delete app resources
echo [2/3] Removing Spring Boot deployment...
kubectl delete -k k8s\overlays\local --ignore-not-found
kubectl delete secret splunk-secret -n springboot --ignore-not-found
echo       App resources removed.
echo.

:: Uninstall collector
echo [3/3] Uninstalling Splunk OTel Collector...
helm uninstall xp-splunk-otel-collector --namespace splunk 2>nul
kubectl delete namespace splunk --ignore-not-found
echo       Collector removed.
echo.

echo ============================================
echo  Teardown complete!
echo ============================================
echo.
