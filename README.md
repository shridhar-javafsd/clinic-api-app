# Clinic API — Unified REST API

A single Spring Boot application for managing doctors, patients, and appointments.

---

## Requirements

- Java 17+
- Maven 3.8+
- MySQL 8.0+

---

## Database Setup

```sql
CREATE DATABASE clinic_db;
CREATE USER 'clinic_user'@'%' IDENTIFIED BY 'clinic_pass';
GRANT ALL PRIVILEGES ON clinic_db.* TO 'clinic_user'@'%';
FLUSH PRIVILEGES;
```

Tables are auto-created by Hibernate on first run.

---

## Running the App

```bash
mvn clean package -DskipTests
java -jar target/clinic-api-1.0.0.jar
```

App runs on `http://localhost:8080`

---

## API Endpoints

### Doctors — `/api/doctors`
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/doctors` | List all doctors |
| GET    | `/api/doctors/{id}` | Get by ID |
| GET    | `/api/doctors/search?email=` | Find by email |
| POST   | `/api/doctors` | Create |
| PUT    | `/api/doctors/{id}` | Update |
| DELETE | `/api/doctors/{id}` | Delete |

### Patients — `/api/patients`
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/patients` | List all patients |
| GET    | `/api/patients/{id}` | Get by ID |
| GET    | `/api/patients/search?email=` | Find by email |
| POST   | `/api/patients` | Create |
| PUT    | `/api/patients/{id}` | Update |
| DELETE | `/api/patients/{id}` | Delete |

### Appointments — `/api/appointments`
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/appointments` | List all appointments |
| GET    | `/api/appointments/{id}` | Get by ID |
| GET    | `/api/appointments/patient/{patientId}` | Get by patient |
| GET    | `/api/appointments/doctor/{doctorId}` | Get by doctor |
| POST   | `/api/appointments` | Create |
| PUT    | `/api/appointments/{id}` | Update |
| DELETE | `/api/appointments/{id}` | Delete |

---

## Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## Sample Request Bodies

**Create Doctor:**
```json
{
  "name": "Dr. Jane Smith",
  "gender": "Female",
  "specialization": "Cardiology",
  "contact": "9876543210",
  "email": "jane.smith@clinic.com",
  "password": "secret123"
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
  "password": "pass123"
}
```

**Create Appointment:**
```json
{
  "doctorId": 1,
  "patientId": 1,
  "date": "2026-04-10",
  "slot": "10:00 AM",
  "status": "SCHEDULED"
}
```
