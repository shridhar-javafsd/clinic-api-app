# Day 1 — Observability & Prometheus Fundamentals

> **Bootcamp:** Cloud-Native Monitoring & Alerting | **Trainer:** Vaman Rao Deshmukh

## Table of Contents

- [1.1 Monitoring vs Observability](#11-monitoring-vs-observability)
- [1.2 The Observability Triad: Metrics, Logs, Traces](#12-the-observability-triad-metrics-logs-traces)
- [1.3 Prometheus Architecture & Data Model](#13-prometheus-architecture--data-model)
- [1.4 Metric Types: Counter, Gauge, Histogram, Summary](#14-metric-types-counter-gauge-histogram-summary)
- [1.5 Installing Prometheus (Docker-based)](#15-installing-prometheus-docker-based)
- [1.6 prometheus.yml Deep Dive](#16-prometheusyml-deep-dive)
- [1.7 Node Exporter Setup & Key Metrics](#17-node-exporter-setup--key-metrics)
- [1.8 Introduction to PromQL](#18-introduction-to-promql)
- [Lab: Day 1](#lab-day-1)

---

## 1.1 Monitoring vs Observability

These two terms are often used interchangeably but they represent fundamentally different mindsets.

| Monitoring | Observability |
|---|---|
| Answers **known** questions: *"Is the service up?"* | Answers **unknown** questions: *"Why did response time spike?"* |
| Pre-defined dashboards and alerts | Exploratory analysis from raw telemetry |
| Detects known failure modes | Helps diagnose novel, unexpected failures |
| Example: CPU > 80% → alert | Example: Correlate slow DB queries with high appointment creation rate |

### In the context of the Clinic API

**Monitoring alone** would tell you: *"The `/api/appointments` endpoint returned 422 errors."*

**Observability** helps you understand **why**: A spike in 422s correlates with a deploy that changed appointment slot validation — discoverable by combining metrics, logs, and traces.

---

## 1.2 The Observability Triad: Metrics, Logs, Traces

Modern observability is built on three pillars. Prometheus and Grafana primarily handle **Metrics**, but understanding all three is essential.

| Pillar | What it captures | Tool | Clinic API example |
|---|---|---|---|
| **Metrics** | Numeric measurements over time | Prometheus + Grafana | `clinic_appointments_created_total = 142` |
| **Logs** | Discrete events with context | Loki / ELK / stdout | `"Appointment 55 created for patient Amit Verma"` |
| **Traces** | Request flow across services | Jaeger / Zipkin / Tempo | `POST /api/appointments → AppointmentService → AppointmentRepository.save` |

> 📝 **Note:** This bootcamp focuses on **Metrics**. However in the capstone project you will see how Grafana can correlate metrics with log data to build a complete observability picture.

---

## 1.3 Prometheus Architecture & Data Model

### Architecture

Prometheus follows a **pull-based model** — it scrapes targets on a schedule, targets do not push data.

```
┌─────────────────────────────────────────────────────────────────┐
│                      PROMETHEUS SERVER                          │
│                                                                 │
│  Retrieval (scrape)  →  TSDB (Storage)  →  HTTP API           │
│         ↑                                      ↓               │
│   Target Discovery                          PromQL             │
│  (static / k8s SD)                   (Grafana / alerts)       │
└─────────────────────────────────────────────────────────────────┘
         ↑                     ↑                     ↑
  Clinic API             Node Exporter         Alertmanager
  :9999/actuator         :9100/metrics           ← fires alerts
  /prometheus
```

| Component | Role in the Clinic API setup |
|---|---|
| Prometheus Server | Scrapes the Clinic API and Node Exporter every 15s |
| Clinic API target | Exposes metrics at `http://localhost:9999/actuator/prometheus` |
| Node Exporter | Exposes OS-level metrics (CPU, memory, disk) from the host |
| TSDB | Time-series database that stores all scraped samples on disk |
| PromQL engine | Query language used by Grafana dashboards and alert rules |
| Alertmanager | Receives alerts fired by Prometheus, routes to email/Slack |

### Data Model: Time-Series

Every metric in Prometheus is a **time-series**: a stream of `(timestamp, value)` pairs identified by a **metric name** and a set of **key=value labels**.

```
# Metric name + labels = unique time-series
clinic_appointments_created_total{application="clinic-api", instance="localhost:9999", job="clinic-api"}

# Each scrape appends a new sample:
# timestamp=1709980800  value=5
# timestamp=1709980815  value=5   (no new appointments)
# timestamp=1709980830  value=7   (two more booked)
```

### Labels — The Power of Prometheus

Labels make Prometheus flexible. Instead of creating separate metrics for each case, you use **one metric with labels**:

```
# WITHOUT labels: three separate metrics
http_requests_get_total
http_requests_post_total
http_requests_delete_total

# WITH labels: one metric, queryable many ways
http_server_requests_seconds_count{method="GET",  uri="/api/appointments", status="200"}
http_server_requests_seconds_count{method="POST", uri="/api/appointments", status="201"}
http_server_requests_seconds_count{method="POST", uri="/api/appointments", status="422"}

# Spring Boot auto-generates the above from Micrometer — no extra code needed
```

---

## 1.4 Metric Types: Counter, Gauge, Histogram, Summary

Prometheus has four metric types. Understanding which to use is critical for writing correct PromQL queries.

### Counter

A Counter **only goes up** (or resets to zero on restart). Use it to count events.

```java
// In AppointmentService.java
this.appointmentCreatedCounter = Counter.builder("clinic.appointments.created")
        .description("Total number of appointments created")
        .register(meterRegistry);

// Called inside createAppointment():
appointmentCreatedCounter.increment();

// Resulting metric in Prometheus:
// clinic_appointments_created_total{application="clinic-api"} 14.0
```

> 💡 **Tip:** Always query counters with `rate()` or `increase()` in PromQL — never the raw value.
> The raw counter value is cumulative and not meaningful on its own.
> Example: `rate(clinic_appointments_created_total[5m])` = appointments booked per second over last 5 minutes.

### Gauge

A Gauge **can go up or down**. Use it for values that represent a current state.

```java
// In AppointmentService.java
Gauge.builder("clinic.appointments.scheduled.total", appointmentRepository,
        repo -> repo.countByStatus(AppointmentStatus.SCHEDULED))
        .description("Current number of scheduled appointments")
        .register(meterRegistry);

// This re-queries the DB on every Prometheus scrape!
// The value fluctuates: goes up when appointments are booked,
// goes down when they are confirmed, cancelled, or completed.

// In Prometheus:
// clinic_appointments_scheduled_total{application="clinic-api"} 8.0
```

> 💡 **Tip:** Query gauges directly (no `rate()` needed):
> - `clinic_appointments_scheduled_total` — current value
> - `clinic_appointments_today_total` — how many appointments are scheduled today

### Histogram

A Histogram **samples observations** (usually durations or sizes) and counts them in configurable buckets. Spring Boot auto-creates histograms for HTTP request durations.

```
# Auto-generated by Spring Boot Actuator + Micrometer for every HTTP endpoint:

http_server_requests_seconds_bucket{uri="/api/appointments",method="POST",le="0.1"}  45
http_server_requests_seconds_bucket{uri="/api/appointments",method="POST",le="0.25"} 61
http_server_requests_seconds_bucket{uri="/api/appointments",method="POST",le="0.5"}  63
http_server_requests_seconds_bucket{uri="/api/appointments",method="POST",le="+Inf"} 63

# _count = total number of requests
http_server_requests_seconds_count{uri="/api/appointments",method="POST"} 63

# _sum = total time spent serving all requests
http_server_requests_seconds_sum{uri="/api/appointments",method="POST"} 4.271
```

The SLO buckets in `application.yml` make these even more useful:

```yaml
# application.yml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms, 250ms, 500ms, 1s, 2s

# Now Prometheus has a bucket at each SLO boundary.
# PromQL: what % of POST /api/appointments completed within 250ms?
# rate(http_server_requests_seconds_bucket{uri="/api/appointments",le="0.25"}[5m])
# / rate(http_server_requests_seconds_count{uri="/api/appointments"}[5m])
```

### Summary

A Summary calculates **quantiles on the client side**. Prometheus cannot aggregate summaries across instances — use Histograms for multi-instance deployments.

```
# When you see metrics like:
jvm_gc_pause_seconds{quantile="0.5"}  0.0041
jvm_gc_pause_seconds{quantile="0.95"} 0.0089

# Those are Summaries — pre-computed on the application side.
# The JVM metrics from Spring Boot use Summaries for GC pauses.
```

### Metric Type Quick Reference

| Metric Type | Clinic API Example | PromQL function |
|---|---|---|
| Counter | `clinic_appointments_created_total` | `rate()`, `increase()` |
| Counter | `clinic_doctors_created_total` | `rate()`, `increase()` |
| Counter | `clinic_appointments_cancelled_total` | `rate()`, `increase()` |
| Gauge | `clinic_appointments_scheduled_total` | Direct query |
| Gauge | `clinic_appointments_today_total` | Direct query |
| Histogram | `http_server_requests_seconds` | `histogram_quantile()` |
| Gauge | `jvm_memory_used_bytes` | Direct query |
| Gauge | `hikaricp_connections_active` | Direct query |

---

## 1.5 Installing Prometheus (Docker-based)

### Step 1: Create `prometheus.yml`

```yaml
global:
  scrape_interval: 15s       # How often to scrape targets
  evaluation_interval: 15s   # How often to evaluate alert rules

scrape_configs:
  # Scrape Prometheus itself
  - job_name: "prometheus"
    static_configs:
      - targets: ["localhost:9090"]

  # Scrape the Clinic API
  - job_name: "clinic-api"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["host.docker.internal:9999"]
      # host.docker.internal = your laptop from inside Docker

  # Scrape Node Exporter (OS metrics)
  - job_name: "node-exporter"
    static_configs:
      - targets: ["host.docker.internal:9100"]
```

### Step 2: Run Prometheus via Docker

```bash
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus

# Verify: open http://localhost:9090
# Check targets: http://localhost:9090/targets
# Both clinic-api and node-exporter should show UP in green
```

### Step 3: Run Node Exporter

```bash
docker run -d \
  --name node-exporter \
  -p 9100:9100 \
  prom/node-exporter

# Verify metrics:
curl http://localhost:9100/metrics | head -20
```

---

## 1.6 prometheus.yml Deep Dive

Every field in `prometheus.yml` serves a specific purpose. Here is a production-representative configuration for the Clinic API setup:

```yaml
global:
  scrape_interval: 15s        # Default: every 15 seconds
  scrape_timeout: 10s         # A scrape must complete within 10s
  evaluation_interval: 15s    # Alert rules evaluated every 15s

  # These labels are attached to ALL metrics from this Prometheus instance.
  # Useful when federating or when remote-writing to Thanos.
  external_labels:
    environment: "dev"
    region: "local"

rule_files:                    # Alert rule files (Day 3)
  - "alert-rules.yml"

alerting:                      # Where to send fired alerts (Day 3)
  alertmanagers:
    - static_configs:
        - targets: ["localhost:9093"]

scrape_configs:
  - job_name: "clinic-api"
    scrape_interval: 10s       # Override global: scrape this target every 10s
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["host.docker.internal:9999"]
        labels:
          service: "clinic-api"
          team: "backend"
```

---

## 1.7 Node Exporter Setup & Key Metrics

Node Exporter exposes Linux kernel-level metrics. These are the OS metrics you'll build your infrastructure dashboard around on Day 4.

| Metric | Description | Alert use case |
|---|---|---|
| `node_cpu_seconds_total` | CPU time by mode (user, system, idle) | CPU usage > 80% for 5 min |
| `node_memory_MemAvailable_bytes` | Available memory | Available memory < 10% |
| `node_filesystem_avail_bytes` | Free disk space | Disk < 10% free |
| `node_network_receive_bytes_total` | Network bytes received | Unusual traffic spikes |
| `node_load1` | 1-minute load average | Load > number of CPUs |

### Useful PromQL for Node Exporter

```promql
# CPU Usage % (across all cores)
100 - (avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# Memory usage %
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100

# Disk usage % on root partition
(1 - (node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"})) * 100
```

---

## 1.8 Introduction to PromQL

### The four basic instant vector selectors

```promql
# 1. All time-series for a metric
http_server_requests_seconds_count

# 2. Filter by label — appointments endpoint only
http_server_requests_seconds_count{uri="/api/appointments"}

# 3. Filter by multiple labels
http_server_requests_seconds_count{uri="/api/appointments", method="POST", status="201"}

# 4. Regex match — all /api/* endpoints
http_server_requests_seconds_count{uri=~"/api/.*"}
```

---

## Lab: Day 1

> 🔬 **Lab: Your First PromQL Queries**
>
> Start the Clinic API and Prometheus. Open `http://localhost:9090` and run the following queries one by one.

**1. Raw counter value**
```promql
clinic_appointments_created_total
```

**2. Live gauge — current scheduled appointments**
```promql
clinic_appointments_scheduled_total
```

**3. How many times was the doctors endpoint called?**
```promql
http_server_requests_seconds_count{uri="/api/doctors"}
```

**4. Filter for non-200 responses (errors and redirects)**
```promql
http_server_requests_seconds_count{status!="200"}
```

**5. Cause a 404 and observe**
```bash
curl http://localhost:9999/api/doctors/999
```
Re-run query 4 — the counter should have incremented.

---

*Next: [Day 2 — Advanced PromQL & Microservice Instrumentation](./02-day2-advanced-promql-instrumentation.md)*
