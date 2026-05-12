@echo off
setlocal enabledelayedexpansion

echo ============================================
echo  xp-splunk Spring Boot - K8s Deploy
echo ============================================
echo.

cd /d "%~dp0"

:: Load .env file
if not exist .env (
    echo ERROR: .env file not found. Create it with SPLUNK_ACCESS_TOKEN, SPLUNK_REALM, and SPLUNK_HEC_TOKEN.
    exit /b 1
)
for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    set "line=%%a"
    if not "!line:~0,1!"=="#" (
        set "%%a=%%b"
    )
)

if "%SPLUNK_ACCESS_TOKEN%"=="" (
    echo ERROR: SPLUNK_ACCESS_TOKEN not set in .env
    exit /b 1
)
if "%SPLUNK_REALM%"=="" (
    echo ERROR: SPLUNK_REALM not set in .env
    exit /b 1
)

:: ---- Step 1: Build Docker image ----
echo [1/6] Building Docker image...
docker build -t xp-splunk-springboot:latest .
if %errorlevel% neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)
echo       Image built successfully.
echo.

:: ---- Step 2: Install Splunk OTel Collector via Helm ----
echo [2/6] Installing Splunk OTel Collector via Helm...
helm repo add splunk-otel-collector-chart https://signalfx.github.io/splunk-otel-collector-chart >nul 2>&1
helm repo update >nul 2>&1

:: Substitute env vars into Helm values
set "VALUES_FILE=k8s\splunk-otel-values.yaml"
set "VALUES_TMP=k8s\splunk-otel-values-resolved.yaml"
powershell -Command "(Get-Content '%VALUES_FILE%') -replace '\$\{SPLUNK_REALM\}','%SPLUNK_REALM%' -replace '\$\{SPLUNK_ACCESS_TOKEN\}','%SPLUNK_ACCESS_TOKEN%' -replace '\$\{SPLUNK_HEC_TOKEN\}','%SPLUNK_HEC_TOKEN%' | Set-Content '%VALUES_TMP%'"

helm upgrade --install xp-splunk-otel-collector ^
    splunk-otel-collector-chart/splunk-otel-collector ^
    --namespace splunk --create-namespace ^
    --values "%VALUES_TMP%" ^
    --wait --timeout 120s
if %errorlevel% neq 0 (
    echo WARNING: Helm install had issues. Continuing anyway...
)
del "%VALUES_TMP%" 2>nul
echo       Collector installed.
echo.

:: ---- Step 3: Create namespace and secrets ----
echo [3/6] Applying namespace and secrets...
kubectl apply -k k8s\overlays\local
kubectl create secret generic splunk-secret ^
    --namespace springboot ^
    --from-literal=access-token=%SPLUNK_ACCESS_TOKEN% ^
    --dry-run=client -o yaml | kubectl apply -f -
echo       Namespace, secrets, and app resources applied.
echo.

:: ---- Step 4: Wait for pods ----
echo [4/6] Waiting for pods to be ready...
kubectl rollout status deployment/springboot -n springboot --timeout=120s
if %errorlevel% neq 0 (
    echo ERROR: Pods did not become ready in time.
    kubectl get pods -n springboot
    exit /b 1
)
echo       All pods ready.
echo.

:: ---- Step 5: Port-forward ----
echo [5/6] Starting port-forward...
start /b cmd /c "kubectl port-forward svc/springboot 8080:80 -n springboot >nul 2>&1"
timeout /t 3 /nobreak >nul
echo       Port-forward active on localhost:8080.
echo.

:: ---- Step 6: Test the API ----
echo [6/6] Testing API endpoints...
echo.

echo --- Health Check ---
curl -s http://localhost:8080/actuator/health
echo.
echo.

echo --- Create Item 1 ---
curl -s -X POST http://localhost:8080/api/items ^
    -H "Content-Type: application/json" ^
    -d "{\"name\": \"splunk-test\", \"description\": \"Testing Splunk integration\"}"
echo.
echo.

echo --- Create Item 2 ---
curl -s -X POST http://localhost:8080/api/items ^
    -H "Content-Type: application/json" ^
    -d "{\"name\": \"k8s-deploy\", \"description\": \"Deployed on Docker Desktop K8s\"}"
echo.
echo.

echo --- List All Items ---
curl -s http://localhost:8080/api/items
echo.
echo.

echo --- Get Item by ID (will 404 to test error path) ---
curl -s http://localhost:8080/api/items/does-not-exist
echo.
echo.

echo ============================================
echo  Deploy complete!
echo ============================================
echo.
echo  App:               http://localhost:8080/api/items
echo  Health:            http://localhost:8080/actuator/health
echo  Splunk Enterprise: http://localhost:8000 (logs via HEC)
echo  Splunk Observability: https://app.%SPLUNK_REALM%.signalfx.com (traces/metrics)
echo.
echo  Port-forward is running in background.
echo  Run teardown.bat to clean up.
echo.
