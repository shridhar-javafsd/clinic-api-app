# Clinic API + Load Simulator — Docker Flow

Everything runs in Docker. No local Java, Python, or database setup required beyond one Maven build step.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     docker-compose network                          │
│                                                                     │
│  ┌──────────────┐    SQL     ┌──────────────┐                       │
│  │  clinic-mysql│◄───────── │  clinic-api  │◄── REST :9999         │
│  │  MySQL 8.0   │            │  Java 17     │                       │
│  │  :3306       │            │  Spring Boot │──► /actuator/prometheus│
│  └──────────────┘            └──────────────┘         │            │
│                                                        │ scrape/15s │
│                                               ┌────────▼──────┐    │
│                                               │  prometheus   │    │
│                                               │  :9090        │    │
│                                               └───────────────┘    │
└─────────────────────────────────────────────────────────────────────┘

┌───────────────────────┐
│  clinic-load-simulator│  ──── HTTP calls ──►  localhost:9999
│  Python 3.11 (Docker) │       (--network host)
│  6 concurrent threads │
└───────────────────────┘
```

The simulator runs with `--network host` so it can reach `localhost:9999`, which maps into the compose network.

---

## Step-by-Step: Running the Full Stack

### Step 1 — Build the Spring Boot jar

Maven needs to run once to produce the jar that the Dockerfile copies into the image.

```bash
cd clinic-api-app-main
mvn clean package -DskipTests
```

This produces `target/clinic-api-2.0.0.jar`.

> **Note:** The `Dockerfile` currently references `clinic-api-1.0.0.jar` but `pom.xml` declares version `2.0.0`. Fix the Dockerfile before running — see [Known Issue](#known-issue--dockerfile-jar-version-mismatch) at the bottom.

---

### Step 2 — Start the core stack

```bash
docker-compose up --build
```

Docker Compose starts three containers in dependency order:

| Container | Image | Port | Starts when |
|---|---|---|---|
| `clinic-mysql` | `mysql:8.0` | 3306 | Immediately |
| `clinic-api` | Built from `Dockerfile` | 9999 | After MySQL healthcheck passes |
| `prometheus` | `prom/prometheus:latest` | 9090 | After `clinic-api` starts |

Prometheus is configured via `prometheus.yml` to scrape `clinic-api:9999/actuator/prometheus` every 15 seconds.

**Verify everything is up:**
```bash
# API health
curl http://localhost:9999/actuator/health

# Prometheus targets — clinic-api should show state: UP
open http://localhost:9090/targets
```

On first startup, `DataInitializer` auto-seeds the database (runs when `prod` profile is NOT active):
- 4 doctors (Cardiology, Pediatrics, Orthopedics, Neurology)
- 3 patients
- 4 appointments

---

### Step 3 — Build the Load Simulator image

In a separate terminal:

```bash
cd clinic-api-app-main/clinic-load-simulator-docker
docker build -t clinic-load-simulator .
```

---

### Step 4 — Run the Load Simulator

```bash
docker run --network host clinic-load-simulator
```

The simulator:
1. Checks `GET /actuator/health` — retries up to 10 times with 3s delay
2. Launches 6 worker threads, one per scenario, in an endless loop
3. Prints colour-coded request logs to the terminal
4. Prints a stats summary every 15 seconds (total requests, RPS, per-status breakdown)

**With more load:**
```bash
docker run --network host clinic-load-simulator \
  python clinic_load_simulator.py --url http://localhost:9999 --workers 10
```

**Specific scenarios only:**
```bash
docker run --network host clinic-load-simulator \
  python clinic_load_simulator.py --scenarios happy_path,cancel_flood
```

**Stop:**
```
Ctrl + C  — shuts down gracefully and prints final stats
```

---

## The 6 Simulator Scenarios

| Scenario | What it does | HTTP responses | Metric driven |
|---|---|---|---|
| `happy_path` | Register doctor → register patient → book → confirm → complete | 201, 200 | `clinic_appointments_created_total` |
| `slot_conflict` | Books same doctor + date + slot twice | 201, then **409** | 4xx error rate |
| `bad_doctor` | Books with a non-existent doctorId | **404** | 404 spike |
| `bad_validation` | Sends malformed payloads (missing fields, bad email, short password, past date) | **400** | 4xx spike |
| `read_traffic` | Floods GET endpoints — lists, filters by status/specialization, name search | 200 | Low-latency histogram buckets |
| `cancel_flood` | Creates appointments then immediately cancels them | 201, 200 | `clinic_appointments_cancelled_total` |

---

## What to Watch in Prometheus

Open `http://localhost:9090` and try these queries once the simulator is running:

```promql
# All incoming request rate
rate(http_server_requests_seconds_count[1m])

# p95 response latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Error rate %
rate(http_server_requests_seconds_count{status=~"4..|5.."}[1m])
  / rate(http_server_requests_seconds_count[1m]) * 100

# Appointments created (rises with happy_path)
clinic_appointments_created_total

# Cancellation rate (rises with cancel_flood)
rate(clinic_appointments_cancelled_total[5m])

# Currently scheduled appointments
clinic_appointments_scheduled_total
```

---

## Startup Sequence

```
docker-compose up --build
  │
  ├─► clinic-mysql starts
  │       └─► healthcheck: mysqladmin ping (every 10s, up to 10 retries)
  │
  ├─► clinic-api starts  (only after MySQL is healthy)
  │       ├─► Hibernate creates/updates schema
  │       ├─► DataInitializer seeds demo data
  │       └─► /actuator/prometheus becomes available
  │
  └─► prometheus starts
          └─► begins scraping clinic-api:9999 every 15s

(separate terminal)
docker run --network host clinic-load-simulator
  └─► health-checks the API
  └─► launches 6 scenario threads
  └─► metrics start flowing into Prometheus
```

---

## Stopping Everything

```bash
# Stop the simulator
Ctrl + C in the simulator terminal

# Stop the compose stack
Ctrl + C in the compose terminal
# or from any terminal:
docker-compose down

# Stop and wipe the MySQL data volume (clean slate)
docker-compose down -v
```

---

## Known Issue — Dockerfile Jar Version Mismatch

The `Dockerfile` has:
```dockerfile
COPY target/clinic-api-1.0.0.jar app.jar
```

But `pom.xml` declares `<version>2.0.0</version>`, so `mvn package` produces `clinic-api-2.0.0.jar`. The Docker build will fail until this is fixed.

**Fix — update the Dockerfile:**
```dockerfile
COPY target/clinic-api-2.0.0.jar app.jar
```

---

## Key URLs

| URL | What it is |
|---|---|
| `http://localhost:9999/swagger-ui.html` | Interactive API docs |
| `http://localhost:9999/actuator/health` | App health check |
| `http://localhost:9999/actuator/prometheus` | Raw Prometheus metrics |
| `http://localhost:9090` | Prometheus UI |
| `http://localhost:9090/targets` | Confirm scrape targets are UP |
