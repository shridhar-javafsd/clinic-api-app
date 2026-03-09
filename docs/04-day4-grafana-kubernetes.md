# Day 4 — Grafana & Kubernetes Monitoring

> **Bootcamp:** Cloud-Native Monitoring & Alerting | **Trainer:** Vaman Rao Deshmukh

## Table of Contents

- [4.1 Grafana Architecture](#41-grafana-architecture)
- [4.2 Connecting Prometheus as Data Source](#42-connecting-prometheus-as-data-source)
- [4.3 Panels, Dashboards, Variables & Templating](#43-panels-dashboards-variables--templating)
- [4.4 Dashboard Best Practices](#44-dashboard-best-practices)
- [4.5 Grafana Unified Alerting](#45-grafana-unified-alerting)
- [4.6 Kubernetes Monitoring Architecture](#46-kubernetes-monitoring-architecture)
- [4.7 Deploying kube-prometheus-stack via Helm](#47-deploying-kube-prometheus-stack-via-helm)
- [Lab: Day 4](#lab-day-4)

---

## 4.1 Grafana Architecture

Grafana is a **visualization and analytics platform**. It does not store data — it queries data sources and renders the results as dashboards.

```
┌───────────────────────────────────────────────────────────┐
│                         GRAFANA                           │
│                                                           │
│  Dashboard → Panel → Query → Data Source Plugin          │
│                                     │                    │
│                              PromQL / LogQL              │
└───────────────────────────────────────────────────────────┘
                                   │             │
                             Prometheus        Loki
                           (metrics)         (logs)
```

| Grafana Concept | Description |
|---|---|
| **Data Source** | Connection to a backend: Prometheus, Loki, MySQL, etc. |
| **Dashboard** | Collection of panels that share a time range and variables |
| **Panel** | A single visualization: time series, gauge, stat, table, heatmap |
| **Query** | A PromQL expression typed into a panel |
| **Variable** | A dropdown filter that changes all panels simultaneously |
| **Template** | A dashboard that uses variables — makes one dashboard work for many services |
| **Annotation** | Marks a point in time on a graph (e.g. "deployed v2.1 here") |
| **Unified Alert** | Grafana's own alerting engine — queries data sources directly |

---

## 4.2 Connecting Prometheus as Data Source

```bash
# Run Grafana via Docker
docker run -d \
  --name grafana \
  -p 3000:3000 \
  grafana/grafana

# Open http://localhost:3000
# Default credentials: admin / admin
```

**Add Prometheus data source:**
1. Go to **Connections → Data Sources → Add new data source**
2. Select **Prometheus**
3. URL: `http://host.docker.internal:9090`
4. Click **Save & test** — should show "Data source is working"

---

## 4.3 Panels, Dashboards, Variables & Templating

### Panel types and when to use them

| Panel Type | Best for | Clinic API use case |
|---|---|---|
| **Time series** | Metrics that change over time | Appointment creation rate, HTTP request rate |
| **Stat** | Single current value with colour threshold | Current scheduled appointments count |
| **Gauge** | Value between a min/max with arc visualization | JVM heap used % |
| **Bar gauge** | Comparing values across categories | Request count by endpoint |
| **Table** | Multi-dimensional data | All endpoints with count, p95 latency, error rate |
| **Heatmap** | Distribution of values over time | Request latency distribution (from Histogram) |
| **Pie chart** | Proportional breakdown | Appointments by status |
| **Logs** | Log lines from Loki | Application log stream |

### Infrastructure Dashboard — Panel Queries

Create a new dashboard and add the following panels:

```promql
# Panel 1 — CPU Usage (Time Series)
100 - (avg by (instance)
  (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# Panel 2 — Memory Usage % (Gauge)
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100

# Panel 3 — JVM Heap Used % (Gauge)
(
  jvm_memory_used_bytes{area="heap", application="clinic-api"}
  / jvm_memory_max_bytes{area="heap", application="clinic-api"}
) * 100

# Panel 4 — DB Connection Pool (Time Series)
hikaricp_connections_active{application="clinic-api"}
hikaricp_connections_idle{application="clinic-api"}
hikaricp_connections_pending{application="clinic-api"}

# Panel 5 — Disk Usage % (Stat)
(1 - (node_filesystem_avail_bytes{mountpoint="/"}
  / node_filesystem_size_bytes{mountpoint="/"})) * 100
```

### Application Dashboard — Panel Queries

```promql
# Panel 1 — Request Rate per Endpoint (Time Series)
sum by (uri) (
  rate(http_server_requests_seconds_count{application="clinic-api"}[5m])
)

# Panel 2 — p95 Latency per Endpoint (Time Series)
histogram_quantile(0.95,
  sum by (le, uri) (
    rate(http_server_requests_seconds_bucket{application="clinic-api"}[5m])
  )
)

# Panel 3 — Error Rate % (Time Series)
(
  sum by (uri) (
    rate(http_server_requests_seconds_count{application="clinic-api",status=~"5.."}[5m])
  )
  / sum by (uri) (
    rate(http_server_requests_seconds_count{application="clinic-api"}[5m])
  )
) * 100

# Panel 4 — Total Appointments Created (Stat)
clinic_appointments_created_total

# Panel 5 — Scheduled Appointments Right Now (Gauge, 0–200)
clinic_appointments_scheduled_total

# Panel 6 — Today's Appointments (Stat)
clinic_appointments_today_total

# Panel 7 — Appointment Cancellation Rate (Time Series)
(
  rate(clinic_appointments_cancelled_total[15m])
  / rate(clinic_appointments_created_total[15m])
) * 100
```

### Variables & Templating

Variables turn a static dashboard into a **reusable template**. Instead of hard-coding `"clinic-api"` in every query, create a variable:

```
# Create a variable called $application:
# Dashboard Settings → Variables → New Variable
# Type: Query
# Query: label_values(http_server_requests_seconds_count, application)
# This populates a dropdown with all values of the "application" label
```

Use `$application` in every panel query:

```promql
sum by (uri) (
  rate(http_server_requests_seconds_count{application="$application"}[5m])
)
```

When you select a different app from the dropdown, **all panels update simultaneously**.

---

## 4.4 Dashboard Best Practices

- **Top row: key health stats** — error rate, p95 latency, uptime. The "at a glance" row.
- **Use consistent time ranges** — set a dashboard-level time range (e.g. last 1h)
- **Add thresholds** to Stat/Gauge panels: green = OK, yellow = warning, red = critical
- **Meaningful titles and descriptions** — others will read this dashboard at 3am
- **Import community dashboards first**:
  - Node Exporter Full: ID `1860`
  - Spring Boot Statistics: ID `12900`
- **Export dashboards as JSON** and store them in Git alongside your application code

---

## 4.5 Grafana Unified Alerting

Grafana 9+ has its own alerting engine that can query any data source directly, independent of Prometheus alert rules.

```
# Grafana Unified Alert for "Clinic API Down":
# Alerting → Alert Rules → New alert rule

# Query A:
up{job="clinic-api"}

# Condition: WHEN last() OF A IS BELOW 1
# Evaluate: every 1m FOR 1m

# Notification policy → route to contact point (email / Slack)
```

**Key advantage:** Grafana alerts can span multiple data sources — e.g. alert when Prometheus shows high error rate **and** Loki shows DB errors at the same time.

---

## 4.6 Kubernetes Monitoring Architecture

When the Clinic API runs in Kubernetes, you need three additional components alongside Prometheus for full visibility.

| Component | What it monitors | Key metrics |
|---|---|---|
| **kube-state-metrics** | K8s object state: are pods running? | `kube_pod_status_phase`, `kube_deployment_status_replicas_available` |
| **cAdvisor** | Container resource usage (built into kubelet) | `container_cpu_usage_seconds_total`, `container_memory_usage_bytes` |
| **Node Exporter** (per node) | OS-level metrics for each k8s node | `node_cpu_*`, `node_memory_*`, `node_filesystem_*` |
| **Prometheus** (via Helm) | Scrapes all of the above automatically | All of the above |

### kube-state-metrics queries for the Clinic API deployment

```promql
# Is the clinic-api deployment healthy?
kube_deployment_status_replicas_available{deployment="clinic-api"}

# How many pods are in a non-running state?
kube_pod_status_phase{namespace="clinic",phase!="Running"}

# Was there a recent restart? (possible crash)
increase(kube_pod_container_status_restarts_total{
  namespace="clinic", container="clinic-api"}[1h])
```

### Alert: Pod restart storm

```yaml
- alert: PodRestartingTooFrequently
  expr: |
    increase(kube_pod_container_status_restarts_total{
      namespace="clinic"}[30m]) > 3
  for: 0m
  labels:
    severity: critical
  annotations:
    summary: "Pod {{ $labels.pod }} restarting frequently"
    description: "{{ $value }} restarts in the last 30 minutes"
```

---

## 4.7 Deploying kube-prometheus-stack via Helm

The `kube-prometheus-stack` Helm chart installs everything at once: Prometheus, Alertmanager, Grafana, Node Exporter, kube-state-metrics, and pre-built dashboards.

```bash
# 1. Start Minikube
minikube start --memory=4096 --cpus=2

# 2. Add the Helm repo
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# 3. Install the full stack into the "monitoring" namespace
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=admin123

# 4. Verify all pods are running
kubectl get pods -n monitoring

# 5. Access Grafana
kubectl port-forward -n monitoring svc/kube-prometheus-stack-grafana 3000:80
# Open http://localhost:3000 — admin / admin123

# 6. Access Prometheus
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
```

### Deploy the Clinic API to Kubernetes

```yaml
# clinic-api-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: clinic-api
  namespace: clinic
  labels:
    app: clinic-api
spec:
  replicas: 2
  selector:
    matchLabels:
      app: clinic-api
  template:
    metadata:
      labels:
        app: clinic-api
      annotations:
        # These annotations tell Prometheus to scrape this pod
        prometheus.io/scrape: "true"
        prometheus.io/port: "9999"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
        - name: clinic-api
          image: clinic-api:2.0.0
          ports:
            - containerPort: 9999
          env:
            - name: SPRING_DATASOURCE_URL
              value: jdbc:mysql://mysql-service:3306/clinic_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 9999
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 9999
            initialDelaySeconds: 60
            periodSeconds: 30
```

---

## Lab: Day 4

> 🔬 **Lab: Build Dashboards and Deploy to Kubernetes**

### Part A — Grafana Dashboards (local Docker setup)

**1. Import the Node Exporter Full dashboard**

- In Grafana: **Dashboards → Import → ID `1860`**
- Select your Prometheus data source and click Import
- Explore the pre-built panels — CPU, memory, disk, network

**2. Build the Application Dashboard**

- Create a new dashboard
- Add all 7 panels from section 4.3 (request rate, p95 latency, error rate, etc.)
- Set thresholds on the error rate panel: green < 1%, yellow 1–5%, red > 5%

**3. Create a `$application` variable**

- Dashboard Settings → Variables → New Variable
- Use it in all your panel queries

**4. Generate load and watch the dashboard react**

```bash
# Run 50 appointment creation requests
for i in {1..50}; do
  curl -s -X POST http://localhost:9999/api/appointments \
    -H "Content-Type: application/json" \
    -d '{
      "doctorId": 1,
      "patientId": 1,
      "appointmentDate": "2026-04-15",
      "slot": "09:00",
      "reason": "Load test visit '"$i"'"
    }' > /dev/null
  sleep 0.2
done
```

Watch the request rate and appointment counters update in real time.

### Part B — Kubernetes

**5. Install kube-prometheus-stack**

```bash
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace \
  --set grafana.adminPassword=admin123
kubectl get pods -n monitoring --watch
```

**6. Deploy clinic-api with Prometheus annotations**

```bash
kubectl create namespace clinic
kubectl apply -f clinic-api-deployment.yaml
kubectl get pods -n clinic
```

**7. Explore the pre-built Kubernetes dashboards in Grafana**

Port-forward Grafana and explore:
- **Kubernetes / Cluster** — overall cluster health
- **Kubernetes / Pods** — find your `clinic-api` pods
- **Kubernetes / Workloads** — deployment replica counts

---

*Previous: [Day 3 — Alerting & Alertmanager](./03-day3-alerting-alertmanager.md)*
*Next: [Day 5 — Production Architecture & Capstone](./05-day5-production-capstone.md)*
