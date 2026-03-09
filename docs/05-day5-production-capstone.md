# Day 5 — Production Architecture & Capstone Project

> **Bootcamp:** Cloud-Native Monitoring & Alerting | **Trainer:** Vaman Rao Deshmukh

## Table of Contents

- [5.1 End-to-End Monitoring Flow](#51-end-to-end-monitoring-flow)
- [5.2 Prometheus Federation & Remote Write](#52-prometheus-federation--remote-write)
- [5.3 Introduction to Thanos / Long-term Storage](#53-introduction-to-thanos--long-term-storage)
- [5.4 Securing Prometheus & Grafana (RBAC Basics)](#54-securing-prometheus--grafana-rbac-basics)
- [5.5 Monitoring Best Practices in Production](#55-monitoring-best-practices-in-production)
- [5.6 Capstone Project](#56-capstone-project)
- [5.7 Complete PromQL Reference Card](#57-complete-promql-reference-card)
- [5.8 Troubleshooting Guide](#58-troubleshooting-guide)

---

## 5.1 End-to-End Monitoring Flow

Before diving into advanced topics, let's trace the **complete path** of a single metric — `clinic_appointments_created_total` — from Java code all the way to a Grafana alert.

```
Java Code              Actuator            Prometheus          Grafana
─────────────────────  ─────────────────   ───────────────     ────────────
appointmentCreated     /actuator/          TSDB stores         Dashboard
Counter.increment() →  prometheus  ←scrape sample:            queries:
                       exposes metric      (t=..., v=8.0)      rate(...[5m])
                       in text format                          shows graph
                                               │
                                        Alert rule evaluates
                                        rate > threshold?
                                               │
                                         Alertmanager routes
                                         to Slack / Email
```

| Step | Component | What happens |
|---|---|---|
| **1. Instrument** | Micrometer in `AppointmentService` | `appointmentCreatedCounter.increment()` called inside `createAppointment()` |
| **2. Expose** | Spring Boot Actuator | Metric serialized to Prometheus text format at `/actuator/prometheus` |
| **3. Scrape** | Prometheus Server | Pulls `/actuator/prometheus` every 15s, stores sample in TSDB |
| **4. Evaluate** | Prometheus alert rules | Evaluates `alert-rules.yml` every 15s against stored data |
| **5. Fire** | Alertmanager | Receives fired alert, applies routing, sends to Slack/email |
| **6. Visualize** | Grafana | Dashboard panels query Prometheus via PromQL on every refresh |

---

## 5.2 Prometheus Federation & Remote Write

### Federation

Federation allows **one Prometheus server to scrape metrics from another**. This is used in hierarchical architectures — multiple regional Prometheus instances each monitor local services, and a global Prometheus federates (collects aggregated metrics) from all of them.

```yaml
# Global Prometheus scraping from a Regional Prometheus
# (The regional instance already collects clinic-api metrics)

scrape_configs:
  - job_name: "federate-region-ap-south"
    honor_labels: true
    metrics_path: "/federate"
    params:
      match[]:
        - '{job="clinic-api"}'                    # Only federate clinic-api metrics
        - 'clinic_appointments_created_total'
    static_configs:
      - targets: ["prometheus-ap-south.internal:9090"]

# Result: the global Prometheus has aggregated clinic-api metrics
# from ALL regions without scraping the clinic-api directly.
```

### Remote Write

Remote write allows Prometheus to **forward all scraped samples** to a long-term storage backend in real time. This is essential in production because Prometheus's local TSDB is not designed for years of data retention.

```yaml
# prometheus.yml — send all metrics to a long-term storage backend
remote_write:
  - url: "http://thanos-receive:19291/api/v1/receive"
    queue_config:
      max_samples_per_send: 1000
      max_shards: 5
    write_relabel_configs:
      # Only forward clinic-api metrics (reduce bandwidth)
      - source_labels: [application]
        regex: "clinic-api"
        action: keep
```

---

## 5.3 Introduction to Thanos / Long-term Storage

Prometheus by default retains data for only **15 days**. Production systems need months or years of history for capacity planning and compliance. **Thanos** solves this.

| Thanos Component | Role |
|---|---|
| **Sidecar** | Runs alongside Prometheus; uploads blocks to object storage (S3, GCS) every 2h |
| **Store Gateway** | Serves historical data from object storage as if it were live Prometheus data |
| **Querier** | A global PromQL endpoint that queries both live Prometheus and Store Gateway |
| **Compactor** | Runs periodic compaction and downsampling on object storage data |
| **Ruler** | Evaluates recording rules and alert rules against the global (federated) view |

> 📝 **Note:** During this bootcamp you use local Prometheus storage. Thanos is introduced conceptually so you understand production trade-offs. The `kube-prometheus-stack` Helm chart can be configured to deploy Thanos components.

---

## 5.4 Securing Prometheus & Grafana (RBAC Basics)

### Grafana RBAC

Grafana has built-in role-based access control.

| Role | Permissions | Clinic API use case |
|---|---|---|
| **Viewer** | Read-only dashboard access | Management, product team — view appointment metrics |
| **Editor** | Create/edit dashboards and alerts | Backend developers maintaining the Clinic API |
| **Admin** | Full access including data sources, users | DevOps / SRE team |

### Securing the `/actuator/prometheus` endpoint

In production, the Prometheus scrape endpoint should not be publicly accessible.

**Option 1: Separate management port**

```yaml
# application.yml
server:
  port: 9999              # Public API port

management:
  server:
    port: 9998            # Internal management port — not exposed outside cluster
  endpoints:
    web:
      exposure:
        include: prometheus, health
```

**Option 2: Kubernetes NetworkPolicy**

```yaml
# Allow only the Prometheus pod (in the monitoring namespace) to scrape port 9999
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-prometheus-scrape
  namespace: clinic
spec:
  podSelector:
    matchLabels:
      app: clinic-api
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring        # Only the monitoring namespace
          podSelector:
            matchLabels:
              app: prometheus         # Only Prometheus pods
      ports:
        - port: 9999
```

---

## 5.5 Monitoring Best Practices in Production

| Practice | Applied to Clinic API |
|---|---|
| **Semantic metric naming** | `clinic_appointments_created_total` follows: `namespace_entity_action_unit_suffix` |
| **Add `application` label to all metrics** | `management.metrics.tags.application` in `application.yml` |
| **Keep cardinality low** | Never use `patient_id` or `doctor_id` as a label — millions of time-series would crash Prometheus |
| **Use histograms over summaries** | `http_server_requests` uses histograms so you can aggregate across instances |
| **Define runbooks for every alert** | Each alert annotation should include a `runbook` URL |
| **Set resource limits on Prometheus** | Use `--storage.tsdb.retention.size` to cap disk usage |
| **Back up Grafana dashboards** | Export dashboard JSON to Git; use Grafana provisioning |
| **Test your alerts** | Regularly simulate failures to confirm alerts fire correctly |

### Label cardinality — the most common Prometheus anti-pattern

```java
// ❌ WRONG — using patient ID as a label creates millions of time-series
Counter.builder("clinic.patient.activity")
       .tag("patient_id", String.valueOf(patient.getPatientId()))  // BAD!
       .register(meterRegistry);

// ✅ CORRECT — use a counter without high-cardinality labels
// Track total activity; drill down via logs when you need per-patient detail
Counter.builder("clinic.appointments.created")
       .description("Total appointments created")
       .register(meterRegistry);

// If you need per-specialization breakdown (low cardinality — ~10 values — OK):
Counter.builder("clinic.appointments.created")
       .tag("specialization", doctor.getSpecialization().name())
       .register(meterRegistry);
```

---

## 5.6 Capstone Project

> 🏆 **Capstone: Full Production Monitoring Stack**
>
> Build a complete, end-to-end monitoring setup for the Clinic API in Kubernetes. This simulates a real production deployment. Work in teams of 2–3.

### Objective

Deploy the Clinic API with a full monitoring stack, create meaningful dashboards, define and test alert rules, and simulate a failure scenario.

---

### Step 1: Deploy the Stack

1. Deploy MySQL in Kubernetes with a `PersistentVolumeClaim`
2. Build the Clinic API Docker image and load it into Minikube
3. Deploy `clinic-api` with 2 replicas and proper Prometheus annotations
4. Verify all pods are Running

```bash
# Build Docker image for Minikube
eval $(minikube docker-env)
cd clinic-api-app-refactored
docker build -t clinic-api:2.0.0 .

# Apply Kubernetes manifests
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/mysql-deployment.yaml
kubectl apply -f k8s/clinic-api-deployment.yaml

# Verify
kubectl get pods -n clinic
kubectl logs -n clinic deployment/clinic-api
```

---

### Step 2: Configure Full Monitoring Stack

1. Install `kube-prometheus-stack` with Helm (if not already done from Day 4)
2. Create a `ServiceMonitor` to tell Prometheus to scrape the clinic-api `Service`
3. Verify clinic-api appears as a target in Prometheus at `/targets`

```yaml
# clinic-api-service-monitor.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: clinic-api
  namespace: monitoring
  labels:
    release: kube-prometheus-stack    # Must match the Helm release label
spec:
  selector:
    matchLabels:
      app: clinic-api
  namespaceSelector:
    matchNames:
      - clinic
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 15s
```

```bash
kubectl apply -f clinic-api-service-monitor.yaml

# Within 1-2 minutes, clinic-api should appear in:
# http://localhost:9090/targets
```

---

### Step 3: Create Comprehensive Grafana Dashboard

Import your Grafana dashboard JSON from Day 4 and enhance it with four rows:

| Row | Panels |
|---|---|
| **Row 1 — Service Health** | Error rate %, p95 latency, uptime, current appointments |
| **Row 2 — Business Metrics** | Creation rate, cancellation rate, today's count, scheduled total |
| **Row 3 — JVM Health** | Heap used %, GC pause time, thread count, DB connection pool |
| **Row 4 — Infrastructure** | CPU %, memory %, pod restart count, replica count |

Additional requirements:
- Add threshold colours: green < 50%, yellow 50–80%, red > 80%
- Add annotations for deployments using the Grafana Annotations API

---

### Step 4: Define and Trigger Alert Rules

Deploy the `PrometheusRule` CRD to define alert rules inside Kubernetes:

```yaml
# clinic-api-alerts.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: clinic-api-alerts
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  groups:
    - name: clinic_api
      rules:
        - alert: ClinicApiDown
          expr: up{job="clinic-api"} == 0
          for: 1m
          labels:
            severity: critical
          annotations:
            summary: "Clinic API is unreachable"

        - alert: ClinicApiHighErrorRate
          expr: |
            (
              sum(rate(http_server_requests_seconds_count{
                application="clinic-api",status=~"5.."}[5m]))
              /
              sum(rate(http_server_requests_seconds_count{application="clinic-api"}[5m]))
            ) * 100 > 5
          for: 3m
          labels:
            severity: critical
          annotations:
            summary: "Clinic API error rate above 5%"
```

```bash
kubectl apply -f clinic-api-alerts.yaml
```

---

### Step 5: Route Alerts via Alertmanager

```bash
# Create Alertmanager config secret in Kubernetes
kubectl create secret generic alertmanager-kube-prometheus-stack-alertmanager \
  --from-file=alertmanager.yaml=./alertmanager.yml \
  --namespace monitoring \
  --dry-run=client -o yaml | kubectl apply -f -
```

---

### Step 6: Simulate Failure Scenarios

This is the most important step — **validate that your monitoring actually works**.

| Failure Scenario | How to simulate | Expected alert |
|---|---|---|
| Service Down | `kubectl scale deployment clinic-api --replicas=0 -n clinic` | `ClinicApiDown` fires in ~2 minutes |
| High Error Rate | Send malformed requests in a loop to trigger 404/422 errors | `ClinicApiHighErrorRate` fires after 3 min |
| High Latency | Add `Thread.sleep(600)` to `createAppointment()` temporarily | `ClinicApiSlowAppointmentCreation` fires |
| Pod Crash Loop | Set an invalid DB URL and redeploy | `PodRestartingTooFrequently` fires |
| Memory Pressure | Reduce JVM heap limit: `-Xmx128m` | `JvmHeapUsageHigh` fires |

```bash
# Scenario 1: Take clinic-api down
kubectl scale deployment clinic-api --replicas=0 -n clinic

# Check Prometheus: http://localhost:9090/alerts
# ClinicApiDown should go Pending → Firing within 2 minutes

# Restore service
kubectl scale deployment clinic-api --replicas=2 -n clinic

# ───────────────────────────────────────────────────────────────────────────────

# Scenario 2: Simulate high error rate
# doctorId 9999 does not exist → ResourceNotFoundException → 404
for i in {1..100}; do
  curl -s -X POST http://localhost:9999/api/appointments \
    -H "Content-Type: application/json" \
    -d '{
      "doctorId": 9999,
      "patientId": 1,
      "appointmentDate": "2026-04-01",
      "slot": "09:00",
      "reason": "test"
    }' > /dev/null
done
```

---

### Capstone Deliverables

Present the following to the group:

1. Screenshot of Grafana dashboard showing all 4 rows with real data
2. Screenshot of Prometheus `/alerts` showing at least one firing alert
3. Screenshot of Alertmanager UI showing the alert received
4. Screenshot of the webhook.site or Slack channel showing the notification
5. The PromQL for your most useful custom query — explain what it measures and why
6. One improvement you would make to the Clinic API to improve observability

---

## 5.7 Complete PromQL Reference Card

| Use Case | PromQL |
|---|---|
| Request rate per endpoint | `sum by (uri) (rate(http_server_requests_seconds_count{application="clinic-api"}[5m]))` |
| Error rate % | `(sum(rate(http_server_requests_seconds_count{application="clinic-api",status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="clinic-api"}[5m]))) * 100` |
| p95 latency per endpoint | `histogram_quantile(0.95, sum by(le,uri) (rate(http_server_requests_seconds_bucket{application="clinic-api"}[5m])))` |
| Appointments created total | `clinic_appointments_created_total` |
| Appointments per minute | `rate(clinic_appointments_created_total[5m]) * 60` |
| Cancellation rate % | `(rate(clinic_appointments_cancelled_total[15m]) / rate(clinic_appointments_created_total[15m])) * 100` |
| Scheduled appointments now | `clinic_appointments_scheduled_total` |
| Today's appointments | `clinic_appointments_today_total` |
| JVM heap used % | `(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100` |
| DB connections active | `hikaricp_connections_active{application="clinic-api"}` |
| DB connections waiting | `hikaricp_connections_pending{application="clinic-api"}` |
| GC pause time p99 | `histogram_quantile(0.99, sum by(le) (rate(jvm_gc_pause_seconds_bucket{application="clinic-api"}[5m])))` |
| CPU usage % | `100 - (avg by(instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)` |
| Memory usage % | `(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100` |
| Pod restart rate | `increase(kube_pod_container_status_restarts_total{namespace="clinic"}[1h])` |
| Available replicas | `kube_deployment_status_replicas_available{deployment="clinic-api"}` |

---

## 5.8 Troubleshooting Guide

| Problem | Diagnosis | Fix |
|---|---|---|
| `/actuator/prometheus` returns 404 | Endpoint not exposed | Add `prometheus` to `management.endpoints.web.exposure.include` in `application.yml` |
| Target shows DOWN in Prometheus | Network issue or wrong port | Check `prometheus.yml` targets; verify with `curl` from within the Prometheus container |
| Metric not appearing in Prometheus | Counter not incremented / wrong name | Check `/actuator/prometheus` directly; metric names use underscores not dots |
| Grafana panel shows "No data" | Wrong data source or bad query | Test the query directly in the Prometheus UI first; check label names match exactly |
| Alert never fires | `for` duration too long or expression wrong | Test the `expr` in Prometheus UI; it should return a non-empty result |
| Alertmanager not receiving alerts | Prometheus not configured to send alerts | Check the `alerting: alertmanagers:` section in `prometheus.yml` |
| High memory on Prometheus | Too many time-series (cardinality problem) | Check for high-cardinality labels; reduce scrape targets; set `--storage.tsdb.retention.size` |
| kube-state-metrics pods show NotFound | ServiceMonitor label mismatch | Verify the `release` label matches the Helm release name exactly |

---

*Previous: [Day 4 — Grafana & Kubernetes Monitoring](./04-day4-grafana-kubernetes.md)*
*Back to: [Introduction](./00-introduction.md)*
