# Cloud-Native Monitoring & Alerting Bootcamp

**Prometheus + Grafana for Kubernetes & Microservices**

| | |
|---|---|
| **Client** | Oracle |
| **Vendor** | The Skill Enhancers |
| **Trainer** | Vaman Rao Deshmukh |
| **Mode** | VILT |
| **Dates** | 09th March 2026 to 13th March 2026 |
| **Duration** | Five days, 9 AM – 5 PM |
| **Audience** | Freshers |
| **Approach** | Hands-on, industry-standard, enterprise-focused |

---

## Introduction

Welcome to the Cloud-Native Monitoring and Alerting Bootcamp. Over five intensive days, you will learn to observe, instrument, alert on, and visualize a real-world Spring Boot microservice — the **Clinic Management API** — running inside a Kubernetes cluster.

This documentation is your complete reference for all five days. Every concept explained here is directly tied to code you will run, not hypothetical examples.

---

## The Clinic API — Your Training Application

Throughout this bootcamp we use a deliberately realistic Spring Boot REST API that manages a clinic. It has three core entities and production-grade concerns already built in:

| Entity / Component | Purpose |
|---|---|
| `Doctor` | Registered medical practitioners with specialization, availability flag, and contact details |
| `Patient` | Registered patients with date of birth, blood group, and contact details |
| `Appointment` | Links a Doctor and Patient to a date/time slot; has status lifecycle (`SCHEDULED → CONFIRMED → COMPLETED`) |
| `GlobalExceptionHandler` | Catches all exceptions and returns structured JSON error responses with HTTP status codes |
| `Micrometer Metrics` | Instruments the application so Prometheus can scrape real business metrics |
| `Spring Boot Actuator` | Exposes `/actuator/prometheus`, `/actuator/health`, `/actuator/metrics` endpoints |

---

## Why This Application?

Real observability challenges appear when:

- Entities have **relationships** — an appointment cannot exist without a valid Doctor and Patient
- **Business rules** are enforced — a doctor's slot cannot be double-booked
- **Errors are structured** — invalid requests return JSON with field-level validation details
- **Metrics are meaningful** — counters track appointments created, gauges show live scheduled count

---

## Project Structure

```
src/main/java/com/demo/clinic/
├── ClinicApiApplication.java           ← Spring Boot entry point
├── config/
│   ├── DataInitializer.java            ← Seeds demo data on startup
│   ├── MetricsConfig.java              ← Registers TimedAspect for @Timed
│   └── OpenApiConfig.java              ← Swagger/OpenAPI metadata
├── controller/
│   ├── AppointmentController.java      ← REST endpoints for appointments
│   ├── DoctorController.java           ← REST endpoints for doctors
│   └── PatientController.java          ← REST endpoints for patients
├── dto/
│   ├── request/                        ← Input DTOs (DoctorRequest, etc.)
│   └── response/                       ← Output DTOs (DoctorResponse, etc.)
├── exception/
│   ├── BusinessRuleException.java      ← 422 Unprocessable Entity
│   ├── DuplicateResourceException.java ← 409 Conflict
│   ├── ErrorResponse.java              ← Structured error body
│   ├── GlobalExceptionHandler.java     ← @RestControllerAdvice
│   └── ResourceNotFoundException.java  ← 404 Not Found
├── model/
│   ├── Appointment.java                ← Entity with @ManyToOne to Doctor & Patient
│   ├── Doctor.java                     ← Entity with @OneToMany to Appointments
│   ├── Patient.java                    ← Entity with @OneToMany to Appointments
│   └── enums/
│       ├── AppointmentStatus.java      ← SCHEDULED, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW
│       └── Specialization.java         ← CARDIOLOGY, PEDIATRICS, ORTHOPEDICS, etc.
├── repository/
│   ├── AppointmentRepository.java      ← JPA queries including slot conflict check
│   ├── DoctorRepository.java           ← JPA queries by email, specialization
│   └── PatientRepository.java          ← JPA queries by email, name
└── service/
    ├── AppointmentService.java         ← All business rules + Micrometer counters/gauges
    ├── DoctorService.java              ← CRUD + duplicate email check + metrics
    └── PatientService.java             ← CRUD + duplicate email check + metrics
```

---

## API Endpoints Summary

| Endpoint | Description |
|---|---|
| `GET  /api/doctors` | List all doctors |
| `POST /api/doctors` | Register a new doctor (validated) |
| `GET  /api/doctors/available` | List available doctors only |
| `GET  /api/doctors/specialization/{spec}` | Filter doctors by specialization |
| `GET  /api/patients` | List all patients |
| `POST /api/patients` | Register a new patient (validated) |
| `GET  /api/appointments` | List all appointments |
| `POST /api/appointments` | Book appointment (validates doctor, patient, slot) |
| `PATCH /api/appointments/{id}/status` | Change appointment status |
| `GET  /api/appointments/doctor/{id}/schedule?date=` | Doctor's schedule for a date |
| `GET  /api/appointments/status/{status}` | Filter by status (`SCHEDULED`, `CONFIRMED`, etc.) |
| `GET  /actuator/prometheus` | Prometheus scrape endpoint |
| `GET  /actuator/health` | Health check with details |
| `GET  /swagger-ui.html` | Interactive API documentation |

---

## Prerequisites Checklist

Before Day 1 begins, confirm all of the following work on your laptop. Run each command and verify no errors.

> ⚠️ **Important:** If anything fails, contact the trainer immediately — lab time cannot be used for environment setup.

```bash
# 1. Docker
docker run hello-world

# 2. Kubernetes (Minikube or Kind)
minikube start          # OR: kind create cluster
kubectl get nodes

# 3. Helm
helm repo add stable https://charts.helm.sh/stable
helm version

# 4. Java 17
java -version           # should show 17.x

# 5. Deploy a test pod
kubectl run nginx-test --image=nginx
kubectl get pod nginx-test
```

### Minimum Hardware

| Requirement | Minimum |
|---|---|
| Architecture | 64-bit (Intel i5 or equivalent) |
| RAM | 16 GB |
| Free Disk | 100 GB |
| OS | Windows 11 / macOS |
| Internet | ≥ 50 Mbps |

### Required Software

| Tool | Notes |
|---|---|
| Docker | Latest stable |
| Minikube or Kind | Either one |
| `kubectl` CLI | Matches your cluster version |
| Helm v3+ | |
| Git | |
| Java 17+ | |
| VS Code | With YAML & Kubernetes extensions |

> 📝 **Note:** Prometheus, Grafana, and Alertmanager will be **installed during labs** using Docker and Helm. Do **not** pre-install them manually.

---

## Document Navigation

| File | Contents |
|---|---|
| `00-introduction.md` | This file — project overview, structure, prerequisites |
| `01-day1-observability-prometheus-fundamentals.md` | Monitoring vs Observability, Prometheus architecture, metric types, PromQL basics |
| `02-day2-advanced-promql-instrumentation.md` | Advanced PromQL, recording rules, Micrometer, custom metrics |
| `03-day3-alerting-alertmanager.md` | SLIs/SLOs, alert rules, Alertmanager routing/grouping/inhibition |
| `04-day4-grafana-kubernetes.md` | Grafana dashboards, variables, kube-prometheus-stack, Kubernetes monitoring |
| `05-day5-production-capstone.md` | Production architecture, Thanos, security, capstone project |
