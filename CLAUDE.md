# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.14 (Java 21) REST API integrated with Splunk for observability. Traces and metrics go to **Splunk Observability Cloud**, logs (including request/response payloads) go to **Splunk Enterprise** via HEC. Deployed to Kubernetes (Docker Desktop) using Kustomize + Helm.

## Build & Run Commands

```bash
# Build (from springboot/ directory)
./mvnw clean package -DskipTests -B

# Run tests
./mvnw test

# Build Docker image
docker build -t xp-splunk-springboot:latest .

# Deploy to K8s (builds, installs collector, deploys app, runs test calls)
deploy.bat

# Tear down everything
teardown.bat

# Apply Kustomize overlay directly
kubectl apply -k k8s/overlays/local    # Docker Desktop
kubectl apply -k k8s/overlays/prod     # Production
```

## Architecture

The app is a standard Spring Boot layered architecture with an observability layer on top:

```
Controller (ItemController)
    ↓ validates via Jakarta annotations on DTOs
Service (ItemServiceImpl)
    ↓ in-memory ConcurrentHashMap store
    ↓ registers Micrometer metrics (counters, timers, gauges, distributions)
Model (Item)
```

**Observability pipeline:**
- **Splunk OTel Java agent** (`-javaagent` in Dockerfile) auto-instruments traces and forwards logs
- **Micrometer OTLP registry** (configured via `application.properties`) exports custom metrics
- **RequestResponseLoggingFilter** logs request/response bodies, sanitized by **PiiSanitizer**
- **Splunk OTel Collector** (Helm chart in K8s) receives OTLP and routes to Splunk backends

## Key Design Decisions

- **No database** — `ItemServiceImpl` uses an in-memory `ConcurrentHashMap`. This is intentional for demo purposes.
- **No custom OTel config class** — OTLP metrics export is handled entirely by Spring Boot auto-configuration via `management.otlp.metrics.export.*` properties.
- **PII sanitization** happens at the filter level before logging, not at the Splunk collector level. `PiiSanitizer` redacts sensitive JSON fields by name and detects PII patterns (SSN, email, phone, credit card) in free text.
- **Kustomize base/overlay** structure: `k8s/base/` has environment-agnostic manifests, overlays patch replicas, image pull policy, Spring profile, and collector endpoint per environment.

## Credentials & Environment

All secrets live in `springboot/.env` (gitignored). The `deploy.bat` script reads this file and creates K8s secrets dynamically — never edit `k8s/secret.yml` with real values.

Required `.env` variables:
- `SPLUNK_ACCESS_TOKEN` — Splunk Observability Cloud access token
- `SPLUNK_REALM` — Splunk realm (e.g. `us1`)
- `SPLUNK_HEC_TOKEN` — Splunk Enterprise HTTP Event Collector token

## K8s Collector

The Splunk OTel Collector is installed via Helm (not part of Kustomize). Values are in `k8s/splunk-otel-values.yaml`. The app pods send OTLP to the collector's ClusterIP service at `xp-splunk-otel-collector-agent.splunk.svc.cluster.local:4318`.
