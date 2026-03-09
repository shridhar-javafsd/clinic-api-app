# Day 2 — Advanced PromQL & Microservice Instrumentation

> **Bootcamp:** Cloud-Native Monitoring & Alerting | **Trainer:** Vaman Rao Deshmukh

## Table of Contents

- [2.1 Advanced PromQL Functions](#21-advanced-promql-functions)
- [2.2 Aggregations & Vector Matching](#22-aggregations--vector-matching)
- [2.3 Recording Rules](#23-recording-rules)
- [2.4 Service Discovery Concepts](#24-service-discovery-concepts)
- [2.5 Exporters Ecosystem Overview](#25-exporters-ecosystem-overview)
- [2.6 Monitoring Java with Spring Boot Actuator](#26-monitoring-java-with-spring-boot-actuator)
- [2.7 Micrometer Integration & Custom Metrics](#27-micrometer-integration--custom-metrics)
- [2.8 Exposing Custom Metrics — Full Verification](#28-exposing-custom-metrics--full-verification)
- [Lab: Day 2](#lab-day-2)

---

## 2.1 Advanced PromQL Functions

### `rate()` and `irate()`

`rate()` calculates the **per-second average rate of increase** of a counter over a time window. This is the most important PromQL function.

```promql
# Appointments booked per second (averaged over last 5 minutes)
rate(clinic_appointments_created_total[5m])

# Multiply by 60 for per-minute rate (easier to read)
rate(clinic_appointments_created_total[5m]) * 60

# Appointments cancelled per minute
rate(clinic_appointments_cancelled_total[5m]) * 60

# HTTP errors per second on the appointments endpoint
rate(http_server_requests_seconds_count{uri="/api/appointments",status=~"4.."}[5m])

# irate() = instantaneous rate (last 2 samples only) — more reactive but noisier
irate(clinic_appointments_created_total[5m])
```

> 📝 **Note:** `rate()` is extrapolation-safe and handles counter resets (e.g. pod restarts). Always prefer `rate()` for dashboards. Use `irate()` only for debugging real-time spikes.

### `increase()`

`increase()` calculates the **total increase** in a counter over a time window. More intuitive than `rate()` for totals.

```promql
# Total appointments created in the last hour
increase(clinic_appointments_created_total[1h])

# Total HTTP 500 errors in last 30 minutes
increase(http_server_requests_seconds_count{status=~"5.."}[30m])

# Total appointments cancelled today (approximate)
increase(clinic_appointments_cancelled_total[24h])
```

### `histogram_quantile()`

This is how you calculate **latency percentiles** from Histogram data — essential for SLO dashboards.

```promql
# 95th percentile response time for POST /api/appointments over last 5 minutes
histogram_quantile(0.95,
  rate(http_server_requests_seconds_bucket{uri="/api/appointments",method="POST"}[5m])
)

# 99th percentile across ALL Clinic API endpoints
histogram_quantile(0.99,
  sum by (le, uri) (
    rate(http_server_requests_seconds_bucket[5m])
  )
)

# Average response time (not a percentile, but useful)
rate(http_server_requests_seconds_sum{uri="/api/appointments"}[5m])
/
rate(http_server_requests_seconds_count{uri="/api/appointments"}[5m])
```

---

## 2.2 Aggregations & Vector Matching

### Aggregation operators

```promql
# sum() — total requests across all endpoints and instances
sum(rate(http_server_requests_seconds_count[5m]))

# sum by label — requests per endpoint
sum by (uri) (rate(http_server_requests_seconds_count[5m]))

# sum by multiple labels
sum by (uri, method) (rate(http_server_requests_seconds_count[5m]))

# count — how many time-series match
count(clinic_appointments_scheduled_total)

# max — highest response time across all endpoints
max by (uri) (http_server_requests_seconds_max)

# avg — average JVM heap used
avg(jvm_memory_used_bytes{area="heap"})

# topk — top 3 slowest endpoints by 95th percentile
topk(3,
  histogram_quantile(0.95,
    sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m]))
  )
)
```

### Binary operations and vector matching

```promql
# Error rate % = (error requests / total requests) * 100
(
  rate(http_server_requests_seconds_count{status=~"5.."}[5m])
  /
  rate(http_server_requests_seconds_count[5m])
) * 100

# Appointment completion rate % (completed / total created)
clinic_appointments_scheduled_total
/
clinic_appointments_created_total
```

---

## 2.3 Recording Rules

Recording rules **pre-compute expensive PromQL expressions** and store the result as a new metric. This dramatically improves Grafana dashboard performance.

### Why use Recording Rules?

- Grafana dashboards query Prometheus on every refresh
- Complex `histogram_quantile()` queries over large time ranges are CPU-intensive
- Recording rules run once per `evaluation_interval` and cache the result

### Recording rule syntax and Clinic API example

```yaml
# recording-rules.yml
groups:
  - name: clinic_api_performance
    interval: 30s                  # Override global evaluation_interval
    rules:

      # Pre-compute p95 latency per endpoint
      - record: job:http_request_duration_p95:rate5m
        expr: |
          histogram_quantile(0.95,
            sum by (le, uri) (
              rate(http_server_requests_seconds_bucket{application="clinic-api"}[5m])
            )
          )

      # Pre-compute request rate per endpoint
      - record: job:http_request_rate:rate5m
        expr: |
          sum by (uri, method) (
            rate(http_server_requests_seconds_count{application="clinic-api"}[5m])
          )

      # Pre-compute error rate
      - record: job:http_error_rate:rate5m
        expr: |
          sum by (uri) (
            rate(http_server_requests_seconds_count{
              application="clinic-api",status=~"5.."
            }[5m])
          )
          /
          sum by (uri) (
            rate(http_server_requests_seconds_count{application="clinic-api"}[5m])
          )

      # Business metric: appointment cancellation rate
      - record: clinic:appointment_cancellation_rate:rate5m
        expr: |
          rate(clinic_appointments_cancelled_total[5m])
          /
          rate(clinic_appointments_created_total[5m])
```

### Add recording rules to prometheus.yml

```yaml
# prometheus.yml — add rule_files section
rule_files:
  - "recording-rules.yml"
  - "alert-rules.yml"          # (Day 3)
```

```bash
# After adding, reload Prometheus:
curl -X POST http://localhost:9090/-/reload

# Verify: go to http://localhost:9090/rules
# Your recording rules should appear with green status
```

---

## 2.4 Service Discovery Concepts

Static configuration (targets hardcoded in `prometheus.yml`) works for lab environments but breaks in Kubernetes where pod IPs change constantly.

| Discovery Type | Use Case |
|---|---|
| `static_configs` | Lab/dev — targets are fixed (what we use today) |
| `kubernetes_sd_configs` | Auto-discover pods/services/nodes in k8s (Day 4) |
| `file_sd_configs` | Targets in a JSON/YAML file updated externally |
| `ec2_sd_configs` | Auto-discover AWS EC2 instances |
| `consul_sd_configs` | Discover services registered in HashiCorp Consul |

### Kubernetes Service Discovery (preview for Day 4)

```yaml
# prometheus.yml for Kubernetes
scrape_configs:
  - job_name: "kubernetes-pods"
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      # Only scrape pods with annotation prometheus.io/scrape: "true"
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: "true"
      # Use custom port if specified in annotation
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        target_label: __address__
```

---

## 2.5 Exporters Ecosystem Overview

An **exporter** translates metrics from a system that doesn't natively expose Prometheus format into the `/metrics` text format that Prometheus understands.

| Exporter | What it monitors | Port |
|---|---|---|
| Node Exporter | Linux OS: CPU, memory, disk, network | `9100` |
| MySQL Exporter | MySQL databases (`clinic_db`) | `9104` |
| JMX Exporter | Java JVM metrics (alternative to Micrometer) | `9012` |
| Blackbox Exporter | HTTP/TCP endpoint probes (is the API reachable?) | `9115` |
| kube-state-metrics | Kubernetes object state (pod status, replica counts) | `8080` |
| cAdvisor | Container CPU/memory/network usage | built-in k8s |

> 📝 **Note:** The Clinic API does **not** need a separate exporter because Spring Boot Actuator + Micrometer natively expose Prometheus-format metrics at `/actuator/prometheus`. Exporters are only needed for systems that cannot be instrumented directly.

---

## 2.6 Monitoring Java with Spring Boot Actuator

Spring Boot Actuator auto-configures dozens of metric endpoints. With `micrometer-registry-prometheus` on the classpath, all metrics are automatically exposed in Prometheus format.

### What Actuator exposes automatically

| Metric prefix | What it measures |
|---|---|
| `http_server_requests_*` | Latency, count, and errors for every HTTP endpoint |
| `jvm_memory_*` | JVM heap and non-heap memory pools |
| `jvm_gc_*` | Garbage collection pause times and counts |
| `jvm_threads_*` | Thread states: daemon, peak, live |
| `hikaricp_connections_*` | Database connection pool: active, idle, pending |
| `logback_events_*` | Log event count by level (INFO, WARN, ERROR) |
| `process_cpu_*` | CPU time used by the Java process |
| `system_cpu_*` | Total system CPU usage |

### `pom.xml` — required dependencies

```xml
<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Prometheus metrics — exposes /actuator/prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- AOP — required for @Timed annotation on service methods -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### `application.yml` — expose the Prometheus endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics, env, loggers
  endpoint:
    prometheus:
      enabled: true
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}   # Adds application="clinic-api" to ALL metrics
```

---

## 2.7 Micrometer Integration & Custom Metrics

Micrometer is the **metrics facade for Java applications** — it sits between your code and the monitoring backend (Prometheus). Think of it as SLF4J, but for metrics.

### Custom Counter — tracking appointments created

```java
// AppointmentService.java
@Service
public class AppointmentService {

    private final Counter appointmentCreatedCounter;
    private final Counter appointmentCancelledCounter;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              DoctorService doctorService,
                              PatientService patientService,
                              MeterRegistry meterRegistry) {

        this.appointmentCreatedCounter = Counter.builder("clinic.appointments.created")
                .description("Total number of appointments created")
                .register(meterRegistry);

        this.appointmentCancelledCounter = Counter.builder("clinic.appointments.cancelled")
                .description("Total number of appointments cancelled")
                .register(meterRegistry);
    }

    public AppointmentResponse createAppointment(AppointmentRequest request) {
        // ... business logic and validation ...
        Appointment saved = appointmentRepository.save(appointment);
        appointmentCreatedCounter.increment();     // ← metric recorded here
        return toResponse(saved);
    }
}
```

### Custom Gauge — live count of scheduled appointments

```java
// The lambda is called on EVERY Prometheus scrape.
// The gauge always reflects the current DB state.
Gauge.builder("clinic.appointments.scheduled.total", appointmentRepository,
        repo -> repo.countByStatus(AppointmentStatus.SCHEDULED))
        .description("Current number of scheduled appointments")
        .register(meterRegistry);

// Another gauge: appointments today
Gauge.builder("clinic.appointments.today.total", appointmentRepository,
        repo -> repo.countByDate(LocalDate.now()))
        .description("Total appointments scheduled for today")
        .register(meterRegistry);
```

### The `@Timed` annotation — automatic method timing

```java
// MetricsConfig.java enables @Timed support
@Configuration
@EnableAspectJAutoProxy
public class MetricsConfig {
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}

// Now annotate any service method:
@Timed(value = "clinic.service.create_appointment",
       description = "Time taken to create an appointment",
       percentiles = {0.5, 0.95, 0.99})
public AppointmentResponse createAppointment(AppointmentRequest request) {
    // Prometheus will automatically record how long this method takes
}

// Resulting metric:
// clinic_service_create_appointment_seconds{quantile="0.95"} 0.042
```

---

## 2.8 Exposing Custom Metrics — Full Verification

After starting the Clinic API, verify all custom metrics are appearing:

```bash
curl http://localhost:9999/actuator/prometheus | grep "clinic_"
```

Expected output:

```
# HELP clinic_appointments_cancelled_total Total number of appointments cancelled
# TYPE clinic_appointments_cancelled_total counter
clinic_appointments_cancelled_total{application="clinic-api",...} 2.0

# HELP clinic_appointments_created_total Total number of appointments created
# TYPE clinic_appointments_created_total counter
clinic_appointments_created_total{application="clinic-api",...} 8.0

# HELP clinic_appointments_scheduled_total Current number of scheduled appointments
# TYPE clinic_appointments_scheduled_total gauge
clinic_appointments_scheduled_total{application="clinic-api",...} 6.0

# HELP clinic_appointments_today_total Total appointments scheduled for today
# TYPE clinic_appointments_today_total gauge
clinic_appointments_today_total{application="clinic-api",...} 4.0
```

> 📝 **Note:** Micrometer converts dots (`.`) in metric names to underscores (`_`) for Prometheus compatibility. So `clinic.appointments.created` becomes `clinic_appointments_created_total`.

---

## Lab: Day 2

> 🔬 **Lab: Instrument and Query**

**Setup**

```bash
# Start the Clinic API (seeds 4 doctors, 3 patients, 4 appointments via DataInitializer)
./mvnw spring-boot:run
```

**Step 1 — Create data via Swagger UI**

Open `http://localhost:9999/swagger-ui.html` and:
- Register 3 additional doctors
- Register 3 additional patients
- Book 5 appointments (use valid doctor and patient IDs)

**Step 2 — Cancel some appointments**

```bash
curl -X PATCH "http://localhost:9999/api/appointments/1/status?status=CANCELLED"
curl -X PATCH "http://localhost:9999/api/appointments/2/status?status=CANCELLED"
```

**Step 3 — Verify counters in Prometheus** (`http://localhost:9090`)

```promql
clinic_appointments_created_total    -- should be 9 (4 seeded + 5 new)
clinic_appointments_cancelled_total  -- should be 2
clinic_appointments_scheduled_total  -- should be 7
```

**Step 4 — Write a recording rule**

Add this to `recording-rules.yml`:

```yaml
- record: clinic:appointment_cancellation_rate:rate5m
  expr: |
    rate(clinic_appointments_cancelled_total[5m])
    /
    rate(clinic_appointments_created_total[5m])
```

Reload Prometheus and confirm the rule appears at `http://localhost:9090/rules`.

---

*Previous: [Day 1 — Observability & Prometheus Fundamentals](./01-day1-observability-prometheus-fundamentals.md)*
*Next: [Day 3 — Alerting & Alertmanager](./03-day3-alerting-alertmanager.md)*
