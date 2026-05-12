# xp-splunk

Experimental repo for integrating Splunk Observability across multiple backend services using OpenTelemetry.

## Architecture

```
                                                                          ┌──────────────────────────┐
                                                                     ┌───►│ Splunk Observability     │
                                                                     │    │ Cloud (traces, metrics,  │
┌──────────────┐       OTLP (gRPC/HTTP)       ┌──────────────────┐   │    │ profiling)               │
│  Spring Boot │  ──────────────────────────►  │  Splunk OTel     │───┘    └──────────────────────────┘
│  + OTel Agent│  traces, metrics, logs        │  Collector       │
└──────────────┘                               │                  │───┐    ┌──────────────────────────┐
                                               └──────────────────┘   │    │ Splunk Enterprise        │
                                                                      └───►│ (logs via HEC, includes  │
                                                                           │ request/response bodies) │
                                                                           └──────────────────────────┘
```

The app is auto-instrumented with the **Splunk OpenTelemetry Java agent** (bundled in the Docker image). It sends traces, metrics, and logs to the **Splunk OTel Collector**, which routes them to two destinations:

| Signal | Destination | Where to view |
|---|---|---|
| **Traces** (APM) | Splunk Observability Cloud | APM > Traces |
| **Metrics** | Splunk Observability Cloud | Infrastructure Monitoring > Metrics |
| **Profiling** | Splunk Observability Cloud | APM > AlwaysOn Profiler |
| **Logs** (including request/response payloads) | Splunk Enterprise (local, via HEC) | Search & Reporting |

Request and response bodies are logged by a servlet filter (`RequestResponseLoggingFilter`) and sanitized for PII (`PiiSanitizer`) before being forwarded to Splunk Enterprise.

## Prerequisites

- Docker Desktop with Kubernetes enabled
- [Helm 3](https://helm.sh/docs/intro/install/)
- [Splunk Enterprise](https://www.splunk.com/en_us/download/splunk-enterprise.html) installed locally (free license, 500MB/day)
- A [Splunk Observability Cloud](https://www.splunk.com/en_us/products/observability.html) account (14-day free trial available, no permanent free tier)

### Getting your Splunk Observability access token and realm

1. Sign up for a [Splunk Observability Cloud free trial](https://www.splunk.com/en_us/products/observability.html) (14 days, no credit card required)
2. Once logged in, find your **realm** in the URL: `https://<realm>.signalfx.com` (e.g. `us0`, `us1`, `eu0`)
3. Go to **Settings** (gear icon) > **Access Tokens**
4. Click **Create New Token** (or use the default token)
5. Copy the token — this is your `SPLUNK_ACCESS_TOKEN`

### Getting your Splunk Enterprise HEC token

1. Open Splunk Enterprise at http://localhost:8000
2. Go to **Settings** > **Data Inputs** > **HTTP Event Collector**
3. Click **Global Settings** — ensure **All Tokens** is **Enabled**
4. Click **New Token**, name it `springboot`, finish the wizard
5. Copy the token — this is your `SPLUNK_HEC_TOKEN`

### Create a `.env` file

Create `springboot/.env` with your credentials:

```
SPLUNK_ACCESS_TOKEN=<your-observability-access-token>
SPLUNK_REALM=<your-realm>
SPLUNK_HEC_TOKEN=<your-hec-token>
```

## Deploy (Kustomize + Helm)

The project uses Kustomize with base/overlay structure and Helm for the collector.

```
k8s/
├── base/                        # Shared manifests
│   ├── kustomization.yml
│   ├── namespace.yml
│   ├── configmap.yml
│   ├── deployment.yml
│   └── service.yml
└── overlays/
    ├── local/                   # Docker Desktop (1 replica, local image)
    └── prod/                    # Production (3 replicas, registry image)
```

### Quick start

```
cd springboot
deploy.bat
```

This builds the Docker image, installs the Splunk OTel Collector via Helm, deploys the app with `kubectl apply -k k8s/overlays/local`, and runs test API calls.

### Teardown

```
teardown.bat
```

### Manual deployment

```bash
# Build image
docker build -t xp-splunk-springboot:latest .

# Install collector
helm repo add splunk-otel-collector-chart https://signalfx.github.io/splunk-otel-collector-chart
helm upgrade --install xp-splunk-otel-collector splunk-otel-collector-chart/splunk-otel-collector --namespace splunk --create-namespace --values k8s/splunk-otel-values.yaml

# Deploy app (local overlay)
kubectl apply -k k8s/overlays/local

# Create secret
kubectl create secret generic splunk-secret --namespace springboot --from-literal=access-token=<your-token>

# Port-forward
kubectl port-forward svc/springboot 8080:80 -n springboot
```

### Test the API

```bash
# Health check
curl http://localhost:8080/actuator/health

# Create an item
curl -X POST http://localhost:8080/api/items -H "Content-Type: application/json" -d "{\"name\": \"test-item\", \"description\": \"hello splunk\"}"

# List items
curl http://localhost:8080/api/items

# Get item by ID (404 to test error path)
curl http://localhost:8080/api/items/does-not-exist
```

### Verify

```bash
# Check pods
kubectl get pods -n springboot

# Check collector
kubectl get pods -n splunk

# Check app logs for OTel agent startup
kubectl logs -n springboot deployment/springboot | head -50
```

## Optional: OpenTelemetry Operator (Alternative to Bundled Agent)

Instead of bundling the Java agent in the Dockerfile, you can use the OpenTelemetry Operator to auto-inject it via annotations. This is useful if you have multiple services and want to manage instrumentation centrally.

### Install the OTel Operator

```bash
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo update
helm install opentelemetry-operator open-telemetry/opentelemetry-operator --namespace opentelemetry --create-namespace
```

### Create an Instrumentation CR

```bash
kubectl apply -f springboot/k8s/operator-instrumentation.yml
```

### Annotate Deployments

```yaml
spec:
  template:
    metadata:
      annotations:
        instrumentation.opentelemetry.io/inject-java: "true"
```

If using the operator approach, remove the `-javaagent` flag from the Dockerfile entrypoint to avoid double-instrumentation.

## Querying Logs in Splunk Enterprise

Open **Search & Reporting** at http://localhost:8000 and use these SPL queries:

```spl
# See everything coming in
index=main

# Find request/response logs
index=main "HTTP_REQUEST" OR "HTTP_RESPONSE"

# See just POST request payloads
index=main "HTTP_REQUEST" method=POST

# See all 404 responses
index=main "HTTP_RESPONSE" status=404

# See requests to a specific endpoint
index=main "HTTP_REQUEST" uri="/api/items"

# See response bodies for a specific status code
index=main "HTTP_RESPONSE" status=201

# Find items by name in payloads
index=main "HTTP_RESPONSE" "splunk-test"
```

## Viewing Traces in Splunk Observability Cloud

1. Go to **APM** > **Traces** in the Splunk Observability UI
2. Filter by service name `springboot`
3. Click a trace to see the full span waterfall (method, path, duration, status)
4. Click **Logs** on a trace to see correlated log lines (if Log Observer Connect is enabled)

## References

- [Splunk OTel Java agent docs](https://docs.splunk.com/observability/en/gdi/get-data-in/application/java/get-started.html)
- [Splunk OTel Collector Helm chart](https://github.com/signalfx/splunk-otel-collector-chart)
- [OpenTelemetry Operator](https://opentelemetry.io/docs/kubernetes/operator/)
