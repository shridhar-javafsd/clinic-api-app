# Clinic API Load Simulator (Docker)

This package contains a Python load simulator used to generate realistic traffic for the **Clinic API** so that **Prometheus and Grafana dashboards show live metrics**.

## Files

- `clinic_load_simulator.py` – Load simulation script
- `requirements.txt` – Python dependencies
- `Dockerfile` – Docker image build instructions
- `README.md` – Instructions to run the simulator

---

# Prerequisites

You must have installed:

- Docker Desktop
- Docker CLI working (`docker --version`)

Also ensure your **Clinic API is running**, for example:

http://localhost:9999

---

# Step 1 — Build Docker Image

Open a terminal inside this folder and run:

```
docker build -t clinic-load-simulator .
```

Docker will:

1. Pull the Python image
2. Install dependencies
3. Copy the simulator script

---

# Step 2 — Run the Simulator

Basic run:

```
docker run --network host clinic-load-simulator
```

---

# Step 3 — Run with Custom API URL

If your API runs on another port:

```
docker run --network host clinic-load-simulator python clinic_load_simulator.py --url http://localhost:9999
```

---

# Step 4 — Run with More Load

Increase worker threads:

```
docker run --network host clinic-load-simulator python clinic_load_simulator.py --url http://localhost:9999 --workers 10
```

---

# Step 5 — Run Specific Scenarios

Example:

```
docker run --network host clinic-load-simulator python clinic_load_simulator.py --scenarios happy_path,cancel_flood
```

Available scenarios:

- happy_path
- slot_conflict
- bad_doctor
- bad_validation
- read_traffic
- cancel_flood

---

# Step 6 — Stop the Simulator

Press:

```
CTRL + C
```

The simulator will stop gracefully.

---

# What You Will See

Prometheus metrics such as:

- clinic_appointments_created_total
- clinic_appointments_cancelled_total
- http_server_requests_seconds_count

Grafana dashboards will start animating with live traffic.

---

# Example Training Demo

For a monitoring workshop run:

```
docker run --network host clinic-load-simulator python clinic_load_simulator.py --url http://localhost:9999 --workers 6
```

Then open:

Prometheus  
http://localhost:9090

Grafana  
http://localhost:3000

---

Author: Training Environment Setup