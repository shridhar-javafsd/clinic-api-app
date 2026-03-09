# Clinic API + Load Simulator — How They Work Together

This document explains the full flow of running the **Spring Boot Clinic API** alongside the **Python Load Simulator** so that Prometheus receives live metrics and Grafana dashboards animate in real time.

---

## Big Picture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Docker Network                               │
│                                                                     │
│  ┌──────────────┐    SQL    ┌──────────────┐                        │
│  │  MySQL 8.0   │◄─────────│  Clinic API  │◄── HTTP :9999          │
│  │  :3306       │          │  Spring Boot │                        │
│  └──────────────┘          │  Java 17     │──► /actuator/prometheus │
│                             └──────┬───────┘        │              │
│                                    │                 │ scrape       │
│                                    │          ┌──────▼───────┐     │
│  ┌────────────────────┐            │          │  Prometheus  │     │
│  │  Load Simulator    │            │          │  :9090       │     │
│  │  Python 3.11       │────────────┘          └──────┬───────┘     │
│  │  (6 concurrent     │  REST calls                  │ query       │
│  │   scenarios)       │                       ┌──────▼───────┐     │
│  └────────────────────┘                       │   Grafana    │     │
│                                               │   :3000      │     │
│                                               └──────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Component Roles

### 1. Clinic API (Spring Boot)
- A REST API managing **Doctors**, **Patients**, and **Appointments**
- Runs on port **9999**
- Exposes Prometheus metrics at `/actuator/prometheus` via Micrometer
- Auto-seeds demo data (4 doctors, 3 patients, 4 appointments) on first start when `prod` profile is NOT active
- Custom metrics tracked:
  - `clinic_appointments_created_total` — counter incremented on every new booking
  - `clinic_appointments_cancelled_total` — counter incremented on cancellation
  - `clinic_appointments_scheduled_total` — gauge of currently scheduled appointments
  - `clinic_appointments_today_total` — gauge of today's appointments
  - All standard Spring Boot HTTP metrics via `http_server_requests_seconds_*`

### 2. Load Simulator (Python)
- Drives the Clinic API with realistic traffic in **6 concurrent scenarios**
- Each scenario runs in its own thread in an endless loop
- Produces a mix of 2xx, 4xx responses to simulate real-world conditions
- Prints colour-coded request logs to the terminal
- Prints a stats summary every 15 seconds (RPS, status code breakdown)

### 3. Prometheus
- Scrapes `/actuator/prometheus` on the Clinic API every **15 seconds**
- Stores time-series metric data
- Accessible at `http://localhost:9090`

### 4. Grafana *(optional — not in docker-compose yet)*
- Queries Prometheus and renders dashboards
- Accessible at `http://localhost:3000` when added

---

## The 6 Simulator Scenarios

| Scenario | What it does | Expected HTTP responses | Metric it drives |
|---|---|---|---|
| `happy_path` | Register doctor → register patient → book appointment → confirm → complete | 201, 200 | `clinic_appointments_created_total` |
| `slot_conflict` | Books same doctor + date + slot twice | 201 then **409** | 4xx error rate panel |
| `bad_doctor` | Books appointment with a non-existent doctorId | **404** | 404 spike |
| `bad_validation` | Sends malformed payloads (missing fields, bad email, short password) | **400** | 4xx error spike |
| `read_traffic` | Floods GET endpoints — list doctors, patients, appointments by status/specialization | 200 | Low-latency histogram buckets |
| `cancel_flood` | Creates appointments then immediately cancels them | 201 then 200 | `clinic_appointments_cancelled_total` |

---

## Running the Stack

### Option A — Full Docker Compose (Recommended)

This starts MySQL + Clinic API + Prometheus together. The simulator runs separately.

**Step 1 — Build the Spring Boot jar**
```bash
cd clinic-api-app-main
mvn clean package -DskipTests
```

**Step 2 — Start the core stack**
```bash
docker-compose up --build
```

This starts:
- `clinic-mysql` on port 3306
- `clinic-api` on port 9999 (waits for MySQL to be healthy)
- `prometheus` on port 9090

**Step 3 — Verify the API is up**
```bash
curl http://localhost:9999/actuator/health
# Expected: {"status":"UP",...}
```

Check that Prometheus is scraping:
- Open `http://localhost:9090/targets` — `clinic-api` should show as **UP**

**Step 4 — Build and run the Load Simulator**

In a separate terminal:
```bash
cd clinic-api-app-main/clinic-load-simulator-docker
docker build -t clinic-load-simulator .
docker run --network host clinic-load-simulator
```

> `--network host` is required so the simulator (outside the Compose network) can reach `localhost:9999`.

**Step 5 — Watch the metrics**

Open Prometheus at `http://localhost:9090` and try these queries:
```
# Request rate per second
rate(http_server_requests_seconds_count[1m])

# Total appointments created
clinic_appointments_created_total

# Total appointments cancelled
clinic_appointments_cancelled_total

# p95 response latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Error rate percentage
rate(http_server_requests_seconds_count{status=~"4..|5.."}[1m])
/ rate(http_server_requests_seconds_count[1m]) * 100
```

---

### Option B — Local Development (No Docker for the API)

Use this when running the Spring Boot app directly from your IDE or terminal.

**Step 1 — Start MySQL only via Docker**
```bash
docker run -d \
  --name clinic-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=clinic_db \
  -p 3306:3306 \
  mysql:8.0
```

**Step 2 — Run the Spring Boot app**

From your IDE (Eclipse/IntelliJ), or:
```bash
mvn spring-boot:run
```

The app starts on `http://localhost:9999`. Demo data is seeded automatically on first run (dev profile).

**Step 3 — Start Prometheus pointing to localhost**

Edit `prometheus.yml` to use `host.docker.internal` instead of `clinic-api`:
```yaml
static_configs:
  - targets: ['host.docker.internal:9999']
```

Then run Prometheus:
```bash
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus:latest
```

**Step 4 — Run the Load Simulator locally (no Docker)**
```bash
cd clinic-load-simulator-docker
pip install requests colorama
python clinic_load_simulator.py --url http://localhost:9999
```

---

## Load Simulator Options

```bash
# Default — all 6 scenarios, 6 workers, runs forever
python clinic_load_simulator.py

# Custom API URL
python clinic_load_simulator.py --url http://localhost:9999

# Increase load (more workers = more concurrent traffic)
python clinic_load_simulator.py --workers 10

# Run specific scenarios only
python clinic_load_simulator.py --scenarios happy_path,cancel_flood

# Run limited iterations (useful for demos)
python clinic_load_simulator.py --iterations 50

# Stop
Ctrl + C    # shuts down gracefully, prints final stats
```

---

## Startup Sequence and Dependencies

```
MySQL starts
    └─► clinic-api starts (waits for MySQL healthcheck to pass)
            └─► Hibernate creates/updates schema
            └─► DataInitializer seeds demo data (dev profile only)
            └─► /actuator/prometheus endpoint becomes available
                    └─► Prometheus begins scraping every 15s
                            └─► Load Simulator hits the API
                                    └─► Metrics start flowing into Prometheus
                                            └─► Grafana dashboards animate
```

---

## Key URLs

| Service | URL | Purpose |
|---|---|---|
| Clinic API | `http://localhost:9999` | REST API base |
| Swagger UI | `http://localhost:9999/swagger-ui.html` | Interactive API docs |
| Health check | `http://localhost:9999/actuator/health` | App health |
| Prometheus metrics | `http://localhost:9999/actuator/prometheus` | Raw metrics endpoint |
| Prometheus | `http://localhost:9090` | Query metrics, check targets |
| Grafana | `http://localhost:3000` | Dashboards (when configured) |

---

## Troubleshooting

**Simulator says "CONNECTION ERROR — Is the Clinic API running?"**
The simulator checks `GET /actuator/health` before starting. Make sure the API is fully up before running the simulator. It retries 10 times with 3-second delays.

**Prometheus shows clinic-api target as DOWN**
- In Docker Compose: Prometheus uses the service name `clinic-api:9999`. Check that the `clinic-api` container is running (`docker ps`).
- In local mode: Make sure `prometheus.yml` targets `host.docker.internal:9999` not `localhost:9999`.

**`cancel_flood` scenario skips iterations**
This scenario reuses existing doctors and patients from the database. On a fresh database it skips the first few iterations until `happy_path` has created some data. This is normal.

**`slot_conflict` not generating 409s**
The `slot_conflict` scenario uses a fixed date 3 days from today and slot `10:00`. If the database was wiped and the same doctor exists with ID collisions, the second booking may fail for a different reason. Check the terminal logs — yellow lines indicate expected 4xx responses.

**Hibernate DDL warnings on startup**
If you see `GenerationTarget encountered exception` warnings, your database has existing data in incompatible column types. Either drop and recreate the database, or temporarily set `ddl-auto: create` for one clean run, then switch back to `update`.
