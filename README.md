# Clinic API

A Spring Boot REST API for managing doctors, patients, and appointments — built as a **learning target for Prometheus, Grafana, and Kubernetes observability training**.

The app ships with a Python load simulator that drives realistic traffic so that Prometheus metrics come alive and Grafana dashboards animate during workshops.

---

## Requirements

- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Docker & Docker Compose (for the full observability stack)

---

## Quick Start — Docker Compose

The easiest way to run everything (MySQL + API + Prometheus) together:

```bash
# 1. Build the jar first
mvn clean package -DskipTests

# 2. Start the stack
docker-compose up --build
```

Services started:

| Container | Port | Description |
|---|---|---|
| `clinic-mysql` | 3306 | MySQL 8.0 database |
| `clinic-api` | 9999 | Spring Boot REST API |
| `prometheus` | 9090 | Prometheus metrics server |

Then run the load simulator to generate traffic:

```bash
cd clinic-load-simulator-docker
docker build -t clinic-load-simulator .
docker run --network host clinic-load-simulator
```

---

## Running Locally (Without Docker)

**Database setup:**
```sql
CREATE DATABASE clinic_db;
CREATE USER 'clinic_user'@'%' IDENTIFIED BY 'clinic_pass';
GRANT ALL PRIVILEGES ON clinic_db.* TO 'clinic_user'@'%';
FLUSH PRIVILEGES;
```

Tables are auto-created by Hibernate on first run (`ddl-auto: update`).

**Run the app:**
```bash
mvn clean package -DskipTests
java -jar target/clinic-api-2.0.0.jar
```

App runs on `http://localhost:9999`

**Demo data** — On first startup (when not running with `prod` profile), the app auto-seeds:
- 4 doctors (Cardiology, Pediatrics, Orthopedics, Neurology)
- 3 patients
- 4 appointments

To skip seeding, activate the prod profile:
```bash
java -jar target/clinic-api-2.0.0.jar --spring.profiles.active=prod
```

---

## API Endpoints

### Doctors — `/api/doctors`

| Method | Path | Description |
|--------|------|-------------|
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
|--------|------|-------------|
| GET | `/api/patients` | List all patients |
| GET | `/api/patients/{id}` | Get by ID |
| GET | `/api/patients/search?email=` | Find by email |
| POST | `/api/patients` | Create patient |
| PUT | `/api/patients/{id}` | Update patient |
| DELETE | `/api/patients/{id}` | Delete patient |

### Appointments — `/api/appointments`

| Method | Path | Description |
|--------|------|-------------|
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

> Slot format is `HH:mm` (24-hour). Available slots: `09:00` to `16:30` every 30 minutes.  
> `appointmentDate` must be today or a future date.

**Update Appointment Status:**
```
PATCH /api/appointments/1/status?status=CONFIRMED
```

---

## Observability

### Prometheus Metrics

The app exposes metrics at:
```
http://localhost:9999/actuator/prometheus
```

Key custom metrics:

| Metric | Type | Description |
|---|---|---|
| `clinic_appointments_created_total` | Counter | Total appointments booked |
| `clinic_appointments_cancelled_total` | Counter | Total appointments cancelled |
| `clinic_appointments_scheduled_total` | Gauge | Currently scheduled appointments |
| `clinic_appointments_today_total` | Gauge | Today's appointments |
| `http_server_requests_seconds_*` | Histogram | Latency per endpoint and status code |

### Useful PromQL Queries

```promql
# Request rate per second
rate(http_server_requests_seconds_count[1m])

# p95 latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Error rate %
rate(http_server_requests_seconds_count{status=~"4..|5.."}[1m])
/ rate(http_server_requests_seconds_count[1m]) * 100

# Appointments created
clinic_appointments_created_total

# Cancellation rate
rate(clinic_appointments_cancelled_total[5m])
```

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

The `clinic-load-simulator-docker/` folder contains a Python script that drives 6 concurrent traffic scenarios against the API.

**Run with Docker:**
```bash
cd clinic-load-simulator-docker
docker build -t clinic-load-simulator .

# Basic run (targets localhost:9999)
docker run --network host clinic-load-simulator

# Custom URL and more workers
docker run --network host clinic-load-simulator \
  python clinic_load_simulator.py --url http://localhost:9999 --workers 10

# Specific scenarios only
docker run --network host clinic-load-simulator \
  python clinic_load_simulator.py --scenarios happy_path,cancel_flood
```

**Run directly with Python:**
```bash
cd clinic-load-simulator-docker
pip install requests colorama
python clinic_load_simulator.py --url http://localhost:9999
```

**Available scenarios:**

| Scenario | What it generates |
|---|---|
| `happy_path` | Full lifecycle — create doctor, patient, book, confirm, complete |
| `slot_conflict` | Double-booking same slot → 409 errors |
| `bad_doctor` | Booking with non-existent doctorId → 404 errors |
| `bad_validation` | Malformed payloads → 400 errors |
| `read_traffic` | GET flood across all list/search/filter endpoints |
| `cancel_flood` | Create then immediately cancel → drives cancellation counter |

See [`clinic-load-simulator-docker/README.md`](clinic-load-simulator-docker/README.md) for full usage.

---

## Project Structure

```
clinic-api-app-main/
├── src/main/java/com/demo/clinic/
│   ├── config/
│   │   ├── DataInitializer.java     # Seeds demo data (dev profile only)
│   │   ├── MetricsConfig.java       # Enables @Timed on service methods
│   │   └── OpenApiConfig.java       # Swagger/OpenAPI setup
│   ├── controller/                  # REST controllers
│   ├── service/                     # Business logic
│   ├── repository/                  # Spring Data JPA repositories
│   ├── model/                       # JPA entities + enums
│   ├── dto/                         # Request/Response DTOs
│   └── exception/                   # GlobalExceptionHandler + custom exceptions
├── src/main/resources/
│   └── application.yml              # App config
├── clinic-load-simulator-docker/    # Python load simulator
├── docs/                            # 5-day observability training curriculum
├── Dockerfile                       # Spring Boot app image
├── docker-compose.yml               # MySQL + API + Prometheus stack
└── prometheus.yml                   # Prometheus scrape config
```

---

## Training Curriculum

The `docs/` folder contains a 5-day observability training curriculum:

| Day | Topic |
|---|---|
| Day 1 | Observability fundamentals — Prometheus basics |
| Day 2 | Advanced PromQL and custom instrumentation |
| Day 3 | Alerting with Alertmanager |
| Day 4 | Grafana dashboards and Kubernetes basics |
| Day 5 | Production patterns and capstone |
