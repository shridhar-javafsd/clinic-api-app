# Clinic API

A Spring Boot REST API for managing doctors, patients, and appointments — built as a **learning target for Prometheus, Grafana, and Kubernetes observability training**.

The app ships with a Python load simulator that drives realistic traffic so Prometheus metrics come alive and Grafana dashboards animate during workshops.

**Everything runs in Docker.**

---

## Requirements

- Docker & Docker Compose
- Maven 3.8+ (only to build the jar once before the Docker image is built)

---

## Quick Start

> **Before running Docker Compose, fix the Dockerfile jar name** — see [Known Issue](#known-issue--dockerfile-jar-version-mismatch).

```bash
# 1. Build the jar
mvn clean package -DskipTests

# 2. Start MySQL + Clinic API + Prometheus
docker-compose up --build

# 3. In a separate terminal — build and run the load simulator
cd clinic-load-simulator-docker
docker build -t clinic-load-simulator .
docker run --network host clinic-load-simulator
```

Open `http://localhost:9090` in Prometheus and watch the metrics populate.

---

## Services

| Container | Port | Description |
|---|---|---|
| `clinic-mysql` | 3306 | MySQL 8.0 database |
| `clinic-api` | 9999 | Spring Boot REST API |
| `prometheus` | 9090 | Prometheus metrics server |

`clinic-api` waits for `clinic-mysql` to pass its healthcheck before starting. Prometheus starts after `clinic-api`.

---

## Demo Data

On first startup, the app automatically seeds the database (skipped when `prod` profile is active):

- 4 doctors — Cardiology, Pediatrics, Orthopedics, Neurology (one set as unavailable for testing)
- 3 patients
- 4 appointments in SCHEDULED / CONFIRMED status

---

## API Endpoints

### Doctors — `/api/doctors`

| Method | Path | Description |
|---|---|---|
| GET | `/api/doctors` | List all doctors |
| GET | `/api/doctors/{id}` | Get by ID |
| GET | `/api/doctors/available` | List available doctors |
| GET | `/api/doctors/specialization/{spec}` | Filter by specialization |
| GET | `/api/doctors/search/name?name=` | Search by name |
| GET | `/api/doctors/search?email=` | Find by email |
| POST | `/api/doctors` | Create doctor |
| PUT | `/api/doctors/{id}` | Update doctor |
| DELETE | `/api/doctors/{id}` | Delete doctor |

### Patients — `/api/patients`

| Method | Path | Description |
|---|---|---|
| GET | `/api/patients` | List all patients |
| GET | `/api/patients/{id}` | Get by ID |
| GET | `/api/patients/search?email=` | Find by email |
| POST | `/api/patients` | Create patient |
| PUT | `/api/patients/{id}` | Update patient |
| DELETE | `/api/patients/{id}` | Delete patient |

### Appointments — `/api/appointments`

| Method | Path | Description |
|---|---|---|
| GET | `/api/appointments` | List all appointments |
| GET | `/api/appointments/{id}` | Get by ID |
| GET | `/api/appointments/patient/{patientId}` | Get by patient |
| GET | `/api/appointments/doctor/{doctorId}` | Get by doctor |
| GET | `/api/appointments/doctor/{doctorId}/schedule?date=` | Doctor's schedule for a date |
| GET | `/api/appointments/date/{date}` | All appointments on a date |
| GET | `/api/appointments/status/{status}` | Filter by status |
| POST | `/api/appointments` | Book appointment |
| PUT | `/api/appointments/{id}` | Update appointment |
| PATCH | `/api/appointments/{id}/status?status=` | Update status only |
| DELETE | `/api/appointments/{id}` | Delete appointment |

**Valid appointment statuses:** `SCHEDULED`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`

**Valid specializations:** `GENERAL_MEDICINE`, `CARDIOLOGY`, `DERMATOLOGY`, `NEUROLOGY`, `ORTHOPEDICS`, `PEDIATRICS`, `GYNECOLOGY`, `ONCOLOGY`, `PSYCHIATRY`, `RADIOLOGY`

---

## Swagger UI

```
http://localhost:9999/swagger-ui.html
```

---

## Sample Request Bodies

**Create Doctor:**
```json
{
  "name": "Dr. Jane Smith",
  "gender": "Female",
  "specialization": "CARDIOLOGY",
  "contact": "9876543210",
  "email": "jane.smith@clinic.com",
  "password": "Pass@1234"
}
```

**Create Patient:**
```json
{
  "name": "John Doe",
  "dateOfBirth": "1990-05-15",
  "gender": "Male",
  "contact": "9123456789",
  "email": "john.doe@email.com",
  "password": "Pass@1234",
  "bloodGroup": "O+"
}
```

**Book Appointment:**
```json
{
  "doctorId": 1,
  "patientId": 1,
  "appointmentDate": "2026-04-10",
  "slot": "10:00",
  "reason": "Routine checkup",
  "notes": "First visit"
}
```

> Slot format is `HH:mm` (24-hour). Available slots run from `09:00` to `16:30` every 30 minutes.  
> `appointmentDate` must be today or a future date.

**Update appointment status:**
```
PATCH /api/appointments/1/status?status=CONFIRMED
```

---

## Observability

### Prometheus Metrics Endpoint

```
http://localhost:9999/actuator/prometheus
```

### Custom Metrics

| Metric | Type | Description |
|---|---|---|
| `clinic_appointments_created_total` | Counter | Total appointments booked |
| `clinic_appointments_cancelled_total` | Counter | Total appointments cancelled |
| `clinic_appointments_scheduled_total` | Gauge | Currently scheduled appointments |
| `clinic_appointments_today_total` | Gauge | Today's appointment count |
| `http_server_requests_seconds_*` | Histogram | Latency per endpoint and status code |

### Actuator Endpoints

```
http://localhost:9999/actuator/health
http://localhost:9999/actuator/metrics
http://localhost:9999/actuator/prometheus
http://localhost:9999/actuator/env
http://localhost:9999/actuator/loggers
http://localhost:9999/actuator/beans
```

---

## Load Simulator

Located in `clinic-load-simulator-docker/`. Runs 6 concurrent traffic scenarios against the API.

```bash
cd clinic-load-simulator-docker
docker build -t clinic-load-simulator .

# Default run
docker run --network host clinic-load-simulator

# More workers
docker run --network host clinic-load-simulator \
  python clinic_load_simulator.py --workers 10

# Specific scenarios
docker run --network host clinic-load-simulator \
  python clinic_load_simulator.py --scenarios happy_path,cancel_flood

# Limited iterations (useful for demos)
docker run --network host clinic-load-simulator \
  python clinic_load_simulator.py --iterations 50
```

**Available scenarios:**

| Scenario | What it generates |
|---|---|
| `happy_path` | Full lifecycle: create doctor + patient, book, confirm, complete |
| `slot_conflict` | Double-booking same slot → 409 errors |
| `bad_doctor` | Booking with non-existent doctorId → 404 errors |
| `bad_validation` | Malformed payloads → 400 errors |
| `read_traffic` | GET flood across all list/search/filter endpoints |
| `cancel_flood` | Create then immediately cancel → drives cancellation counter |

---

## Stopping Everything

```bash
# Stop the compose stack
docker-compose down

# Stop and wipe the database (clean slate)
docker-compose down -v
```

---

## Known Issue — Dockerfile Jar Version Mismatch

The `Dockerfile` references `clinic-api-1.0.0.jar` but `pom.xml` declares version `2.0.0`. The Docker build will fail without this fix.

**Edit `Dockerfile`:**
```dockerfile
# Change this line:
COPY target/clinic-api-1.0.0.jar app.jar

# To:
COPY target/clinic-api-2.0.0.jar app.jar
```

---

## Project Structure

```
clinic-api-app-main/
├── src/main/java/com/demo/clinic/
│   ├── config/
│   │   ├── DataInitializer.java     # Seeds demo data (skipped on prod profile)
│   │   ├── MetricsConfig.java       # Enables @Timed on service methods
│   │   └── OpenApiConfig.java       # Swagger/OpenAPI setup
│   ├── controller/                  # REST controllers
│   ├── service/                     # Business logic + custom metrics
│   ├── repository/                  # Spring Data JPA repositories
│   ├── model/                       # JPA entities + enums
│   ├── dto/                         # Request/Response DTOs
│   └── exception/                   # GlobalExceptionHandler + custom exceptions
├── src/main/resources/
│   └── application.yml              # App configuration
├── clinic-load-simulator-docker/    # Python load simulator
│   ├── clinic_load_simulator.py
│   ├── requirements.txt
│   └── Dockerfile
├── docs/                            # 5-day observability training curriculum
├── Dockerfile                       # Spring Boot app Docker image
├── docker-compose.yml               # Full stack: MySQL + API + Prometheus
└── prometheus.yml                   # Prometheus scrape configuration
```

---

## Training Curriculum

The `docs/` folder contains a 5-day observability curriculum:

| Day | Topic |
|---|---|
| Day 1 | Observability fundamentals — Prometheus basics |
| Day 2 | Advanced PromQL and custom instrumentation |
| Day 3 | Alerting with Alertmanager |
| Day 4 | Grafana dashboards and Kubernetes |
| Day 5 | Production patterns and capstone |
