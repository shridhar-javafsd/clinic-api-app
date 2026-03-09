# Day 3 — Alerting & Alertmanager

> **Bootcamp:** Cloud-Native Monitoring & Alerting | **Trainer:** Vaman Rao Deshmukh

## Table of Contents

- [3.1 Alerting Philosophy & Best Practices](#31-alerting-philosophy--best-practices)
- [3.2 SLI, SLO, SLA Basics](#32-sli-slo-sla-basics)
- [3.3 Writing Alert Rules](#33-writing-alert-rules)
- [3.4 Alert States: Pending, Firing, Resolved](#34-alert-states-pending-firing-resolved)
- [3.5 Alertmanager Architecture](#35-alertmanager-architecture)
- [3.6 Routing, Grouping, Inhibition, Silencing](#36-routing-grouping-inhibition-silencing)
- [3.7 Running Alertmanager via Docker](#37-running-alertmanager-via-docker)
- [Lab: Day 3](#lab-day-3)

---

## 3.1 Alerting Philosophy & Best Practices

Good alerting is about **noise reduction**. A good alert fires only when a human needs to take action *right now*. Too many alerts cause alert fatigue — engineers start ignoring them.

| Principle | Applied to Clinic API |
|---|---|
| Alert on symptoms, not causes | Alert on "high 5xx error rate" — not "DB CPU high". Users feel errors, not CPU. |
| Alert must be actionable | Every alert should have a runbook: what does the on-call person do? |
| Use appropriate thresholds | Use `for: [duration]` to avoid flapping on temporary spikes |
| Set severity levels | `critical` = page someone now; `warning` = investigate in business hours |
| Avoid alert overload | Start with 3–5 critical alerts. Add more as you learn your system. |

---

## 3.2 SLI, SLO, SLA Basics

Service Level terminology is essential for designing meaningful alerts.

| Term | Definition | Clinic API example |
|---|---|---|
| **SLI** (Indicator) | A measurable metric that indicates service quality | 99th percentile latency for `POST /api/appointments` |
| **SLO** (Objective) | A target value for an SLI, agreed within the team | p99 latency < 500ms, measured over 30 days |
| **SLA** (Agreement) | A formal contract based on SLOs | Clinic API will process 99% of bookings within 1 second |
| **Error Budget** | How much you can "spend" on failures before breaching the SLO | If SLO = 99.9% uptime → 43.8 min/month of allowed downtime |

### Translating SLOs into alert rules

```yaml
# SLO: 99th percentile latency for appointment creation < 500ms
# Alert when we are at risk of breaching this SLO:

- alert: AppointmentCreationLatencyHigh
  expr: |
    histogram_quantile(0.99,
      rate(http_server_requests_seconds_bucket{
        uri="/api/appointments",method="POST"
      }[5m])
    ) > 0.5
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "Appointment creation p99 latency breaching SLO"
    description: "p99 latency is {{ $value | humanizeDuration }}, SLO is 500ms"
```

---

## 3.3 Writing Alert Rules

Alert rules live in YAML files referenced by `prometheus.yml`. An alert fires when its **expression evaluates to a non-empty result for longer than the `for` duration**.

### Alert rule structure

```yaml
# alert-rules.yml
groups:
  - name: clinic_api_alerts
    rules:

      - alert: AlertName           # Unique name (shown in Alertmanager)
        expr: <promql_expression>  # Fires when this returns non-empty result
        for: 5m                    # Must be true for 5m before firing
        labels:
          severity: critical       # Used by Alertmanager for routing
          team: backend
        annotations:
          summary: "One-line description"
          description: "Detailed message with {{ $value }} template"
          runbook: "https://wiki.example.com/runbook/AlertName"
```

### Production-ready alert rules for the Clinic API

```yaml
groups:
  - name: clinic_api_availability
    rules:

      # ── SERVICE DOWN ──────────────────────────────────────────────────────────
      - alert: ClinicApiDown
        expr: up{job="clinic-api"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Clinic API is unreachable"
          description: "Prometheus cannot scrape {{ $labels.instance }}. Service may be down."

      # ── HIGH ERROR RATE ───────────────────────────────────────────────────────
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
          description: "Error rate is {{ $value | humanize }}% over the last 5 minutes"

      # ── HIGH LATENCY ──────────────────────────────────────────────────────────
      - alert: ClinicApiSlowAppointmentCreation
        expr: |
          histogram_quantile(0.95,
            rate(http_server_requests_seconds_bucket{
              application="clinic-api",
              uri="/api/appointments",method="POST"
            }[5m])
          ) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Appointment booking is slow"
          description: "p95 latency for appointment creation is {{ $value | humanizeDuration }}"

  - name: clinic_api_business
    rules:

      # ── HIGH CANCELLATION RATE ────────────────────────────────────────────────
      - alert: HighAppointmentCancellationRate
        expr: |
          (
            rate(clinic_appointments_cancelled_total[15m])
            /
            rate(clinic_appointments_created_total[15m])
          ) * 100 > 30
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High appointment cancellation rate"
          description: "{{ $value | humanize }}% of appointments are being cancelled"

      # ── APPOINTMENT BACKLOG ───────────────────────────────────────────────────
      - alert: AppointmentBacklogTooHigh
        expr: clinic_appointments_scheduled_total > 100
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "Too many unconfirmed appointments"
          description: "{{ $value }} appointments are in SCHEDULED state"

  - name: clinic_infrastructure
    rules:

      # ── HIGH CPU ──────────────────────────────────────────────────────────────
      - alert: HighCpuUsage
        expr: |
          100 - (avg by (instance)
            (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100) > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage on {{ $labels.instance }}"
          description: "CPU usage is {{ $value | humanize }}%"

      # ── JVM HEAP PRESSURE ─────────────────────────────────────────────────────
      - alert: JvmHeapUsageHigh
        expr: |
          (
            jvm_memory_used_bytes{area="heap",application="clinic-api"}
            /
            jvm_memory_max_bytes{area="heap",application="clinic-api"}
          ) * 100 > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Clinic API JVM heap above 80%"
          description: "Heap usage is {{ $value | humanize }}%"

      # ── DB CONNECTION POOL ────────────────────────────────────────────────────
      - alert: DatabaseConnectionPoolExhausted
        expr: |
          hikaricp_connections_pending{application="clinic-api"} > 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool exhausted"
          description: "{{ $value }} threads waiting for a DB connection"
```

---

## 3.4 Alert States: Pending, Firing, Resolved

| State | Meaning | Example |
|---|---|---|
| **Inactive** | Expression is false — no problem detected | Error rate = 1% (threshold is 5%) |
| **Pending** | Expression is true but `for` duration not yet met | Error rate = 8% — waiting 3 minutes before firing |
| **Firing** | Expression has been true for the full `for` duration | Error rate = 8% for 3+ minutes → alert fires |
| **Resolved** | Alert was Firing but expression is now false again | Error rate drops back to 1% |

> 📝 **Note:** The `for` duration **prevents false positives** from transient spikes.
> Without `for: 3m`, a single bad scrape would immediately page someone.
> With `for: 3m`, the condition must persist for 3 full minutes — genuine problems, not noise.

---

## 3.5 Alertmanager Architecture

Alertmanager receives firing alerts from Prometheus and decides **what to do** with them: who to notify, how to group them, and when to suppress them.

```
┌────────────────┐   fires alert   ┌──────────────────────────────────┐
│  Prometheus    │ ─────────────►  │          ALERTMANAGER            │
│  alert rules   │                 │                                  │
└────────────────┘                 │  1. Routing    (who gets it?)    │
                                   │  2. Grouping   (batch related)   │
                                   │  3. Inhibition (suppress lower)  │
                                   │  4. Silencing  (scheduled maint) │
                                   └──────────────────────────────────┘
                                         │            │           │
                                      Email         Slack      PagerDuty
```

---

## 3.6 Routing, Grouping, Inhibition, Silencing

### Complete Alertmanager configuration for the Clinic API

```yaml
# alertmanager.yml

global:
  smtp_smarthost: "smtp.gmail.com:587"
  smtp_from: "alerts@clinic.com"
  smtp_auth_username: "alerts@clinic.com"
  smtp_auth_password: "your-app-password"

# ── ROUTING ────────────────────────────────────────────────────────────────────
# Routes define WHERE alerts go based on their labels.
route:
  receiver: "default-email"            # fallback — catch all
  group_by: ["alertname", "severity"]  # group alerts with same name+severity together
  group_wait: 30s                      # wait 30s before sending first notification
  group_interval: 5m                   # wait before sending re-notifications for same group
  repeat_interval: 4h                  # repeat if alert is still firing after 4h

  routes:
    # Critical alerts go to Slack immediately
    - match:
        severity: critical
      receiver: "slack-critical"
      group_wait: 10s

    # Business alerts go to a different Slack channel
    - match_re:
        alertname: "(HighAppointmentCancellationRate|AppointmentBacklogTooHigh)"
      receiver: "slack-business"

# ── RECEIVERS ──────────────────────────────────────────────────────────────────
receivers:
  - name: "default-email"
    email_configs:
      - to: "team@clinic.com"
        subject: "[{{ .Status | toUpper }}] {{ .GroupLabels.alertname }}"
        body: |
          {{ range .Alerts }}
          Alert: {{ .Annotations.summary }}
          Description: {{ .Annotations.description }}
          Labels: {{ range .Labels.SortedPairs }} {{ .Name }}={{ .Value }} {{ end }}
          {{ end }}

  - name: "slack-critical"
    slack_configs:
      - api_url: "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
        channel: "#clinic-alerts-critical"
        title: "{{ .GroupLabels.alertname }}"
        text: "{{ range .Alerts }}{{ .Annotations.description }}\n{{ end }}"
        color: '{{ if eq .Status "firing" }}danger{{ else }}good{{ end }}'

  - name: "slack-business"
    slack_configs:
      - api_url: "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
        channel: "#clinic-ops"

# ── INHIBITION ─────────────────────────────────────────────────────────────────
# If the service is DOWN, suppress all other alerts about that service.
# (No point alerting on high latency if the service is completely unavailable)
inhibit_rules:
  - source_match:
      alertname: "ClinicApiDown"
      severity: "critical"
    target_match:
      severity: "warning"
    equal: ["instance"]    # Only inhibit alerts from the same instance
```

### Silencing — scheduled maintenance

Silences suppress alerts for a defined time window — useful during planned maintenance like deploying a new version of the Clinic API.

```bash
# Create a silence via the Alertmanager UI at http://localhost:9093
# OR via API:

curl -X POST http://localhost:9093/api/v2/silences \
  -H "Content-Type: application/json" \
  -d '{
    "matchers": [{"name": "application", "value": "clinic-api", "isRegex": false}],
    "startsAt": "2026-03-13T09:00:00Z",
    "endsAt":   "2026-03-13T10:00:00Z",
    "createdBy": "vaman.rao",
    "comment":   "Planned maintenance: clinic-api v2.1 deployment"
  }'
```

---

## 3.7 Running Alertmanager via Docker

```bash
docker run -d \
  --name alertmanager \
  -p 9093:9093 \
  -v $(pwd)/alertmanager.yml:/etc/alertmanager/alertmanager.yml \
  prom/alertmanager

# Verify: http://localhost:9093
# You should see the Alertmanager UI with no active alerts
```

---

## Lab: Day 3

> 🔬 **Lab: Create, Fire, and Route Alerts**

**Step 1 — Add alert rules to Prometheus**

```bash
# Create alert-rules.yml with the rules from section 3.3
# Add to prometheus.yml:
rule_files:
  - "recording-rules.yml"
  - "alert-rules.yml"

# Reload Prometheus
curl -X POST http://localhost:9090/-/reload

# Verify: http://localhost:9090/rules — all should show green
```

**Step 2 — Trigger `ClinicApiDown`**

```bash
# Stop the Clinic API
# (if running in Docker):
docker stop clinic-api

# Watch http://localhost:9090/alerts
# ClinicApiDown should transition: Inactive → Pending → Firing (within ~2 minutes)
```

**Step 3 — Start Alertmanager**

```bash
# Configure alertmanager.yml with a webhook.site URL for testing:
# 1. Go to https://webhook.site and copy your unique URL
# 2. Replace the Slack api_url with your webhook.site URL
# 3. Start Alertmanager

docker run -d \
  --name alertmanager \
  -p 9093:9093 \
  -v $(pwd)/alertmanager.yml:/etc/alertmanager/alertmanager.yml \
  prom/alertmanager
```

**Step 4 — Confirm the fired alert**

- Open `http://localhost:9093` — the `ClinicApiDown` alert should appear
- Open your `webhook.site` URL — you should see the POST payload from Alertmanager

**Step 5 — Restore the service and observe resolution**

```bash
# Start the Clinic API again
./mvnw spring-boot:run

# ClinicApiDown should resolve within 1-2 Prometheus scrape cycles (~30 seconds)
# Alertmanager will send a "resolved" notification to your webhook
```

**Step 6 — Create a silence**

- Open `http://localhost:9093` → Silences → New Silence
- Match `application="clinic-api"`, set duration to 5 minutes
- Stop the Clinic API again — verify the alert does NOT reach your webhook

---

*Previous: [Day 2 — Advanced PromQL & Microservice Instrumentation](./02-day2-advanced-promql-instrumentation.md)*
*Next: [Day 4 — Grafana & Kubernetes Monitoring](./04-day4-grafana-kubernetes.md)*
