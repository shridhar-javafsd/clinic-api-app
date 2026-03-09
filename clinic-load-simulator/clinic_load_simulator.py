"""
============================================================
  Clinic API — Prometheus & Grafana Load Simulator
  Bootcamp: Cloud-Native Monitoring & Alerting
  The Skill Enhancers | Oracle | Trainer: Vaman Rao Deshmukh
============================================================

PURPOSE
-------
This script drives the Clinic REST API with realistic traffic so that
Prometheus has data to scrape and Grafana dashboards come alive.

It runs six concurrent scenarios in an endless loop:
  1. happy_path       — register doctor + patient + book + confirm + complete
  2. slot_conflict    — try to double-book the same slot  (→ 409)
  3. bad_doctor       — book with non-existent doctorId   (→ 404)
  4. bad_validation   — send malformed payloads           (→ 400 / 422)
  5. read_traffic     — GET floods on list & search endpoints
  6. cancel_flood     — create then immediately cancel appointments

WHAT YOU WILL SEE
-----------------
  Prometheus metrics that light up:
    clinic_appointments_created_total         ← increments every happy-path cycle
    clinic_appointments_cancelled_total       ← increments on cancel_flood
    clinic_appointments_scheduled_total       ← gauge fluctuates in real time
    clinic_appointments_today_total           ← gauge rises during the run
    http_server_requests_seconds_count        ← all endpoints, all status codes
    http_server_requests_seconds_bucket       ← latency histograms

  Grafana panels that animate:
    Request rate per endpoint (Time Series)
    p95 / p99 latency heatmap
    Error rate % — spikes from bad_doctor + bad_validation scenarios
    Appointments created / cancelled counters
    Scheduled appointments gauge

USAGE
-----
  pip install requests colorama
  python clinic_load_simulator.py

  # Custom base URL or concurrency:
  python clinic_load_simulator.py --url http://localhost:9999 --workers 10

  # Run only specific scenarios:
  python clinic_load_simulator.py --scenarios happy_path,cancel_flood

  # Stop after N iterations per scenario (useful for demos):
  python clinic_load_simulator.py --iterations 50
"""

import argparse
import random
import time
import threading
import sys
import json
import signal
import datetime
from collections import defaultdict

try:
    import requests
    from requests.adapters import HTTPAdapter
    from urllib3.util.retry import Retry
except ImportError:
    print("[ERROR] 'requests' is not installed. Run:  pip install requests")
    sys.exit(1)

try:
    from colorama import Fore, Style, init as colorama_init
    colorama_init(autoreset=True)
    HAS_COLOR = True
except ImportError:
    HAS_COLOR = False
    class Fore:
        GREEN = RED = YELLOW = CYAN = MAGENTA = BLUE = WHITE = ""
    class Style:
        BRIGHT = RESET_ALL = DIM = ""


# ─────────────────────────────────────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────────────────────────────────────

DEFAULT_BASE_URL   = "http://localhost:9999"
DEFAULT_WORKERS    = 6        # one thread per scenario
DEFAULT_ITERATIONS = 0        # 0 = run forever
REQUEST_TIMEOUT    = 8        # seconds

# Delay between individual requests within a scenario (seconds)
THINK_TIME_MIN = 0.3
THINK_TIME_MAX = 1.2

# ─────────────────────────────────────────────────────────────────────────────
# DATA POOLS
# ─────────────────────────────────────────────────────────────────────────────

SPECIALIZATIONS = [
    "CARDIOLOGY", "PEDIATRICS", "ORTHOPEDICS", "NEUROLOGY",
    "DERMATOLOGY", "GENERAL_MEDICINE", "ONCOLOGY", "PSYCHIATRY",
]

FIRST_NAMES = [
    "Aarav", "Priya", "Rahul", "Sneha", "Vikram", "Ananya", "Rohan",
    "Kavya", "Arjun", "Divya", "Nikhil", "Pooja", "Karan", "Riya",
    "Siddharth", "Meera", "Aditya", "Swati", "Manish", "Deepa",
]

LAST_NAMES = [
    "Sharma", "Verma", "Patel", "Singh", "Kumar", "Gupta", "Mehta",
    "Joshi", "Rao", "Iyer", "Nair", "Reddy", "Desai", "Chauhan",
]

BLOOD_GROUPS   = ["A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"]
GENDERS        = ["Male", "Female"]
REASONS        = [
    "Routine checkup",
    "Chest pain and shortness of breath",
    "Fever and cold for 3 days",
    "Knee pain — follow-up",
    "Headache and dizziness",
    "Annual physical examination",
    "Back pain",
    "Skin rash",
    "Vaccination",
    "Blood pressure monitoring",
    "Diabetic review",
    "Post-surgery follow-up",
]

# Appointment slots — clinic hours 09:00 to 17:00, every 30 min
SLOTS = [
    f"{h:02d}:{m:02d}"
    for h in range(9, 17)
    for m in (0, 30)
]


# ─────────────────────────────────────────────────────────────────────────────
# STATS TRACKER
# ─────────────────────────────────────────────────────────────────────────────

class Stats:
    def __init__(self):
        self._lock = threading.Lock()
        self.counts = defaultdict(int)   # e.g. {"201": 45, "404": 12}
        self.total  = 0
        self.errors = 0                  # network / timeout errors
        self._start = time.time()

    def record(self, status_code: int):
        with self._lock:
            self.counts[str(status_code)] += 1
            self.total += 1

    def record_error(self):
        with self._lock:
            self.errors += 1
            self.total  += 1

    def summary(self) -> str:
        with self._lock:
            elapsed = max(time.time() - self._start, 1)
            rps = self.total / elapsed
            parts = [f"{k}→{v}" for k, v in sorted(self.counts.items())]
            return (
                f"total={self.total}  rps={rps:.1f}  "
                f"net_errors={self.errors}  "
                + "  ".join(parts)
            )

STATS = Stats()


# ─────────────────────────────────────────────────────────────────────────────
# HTTP CLIENT
# ─────────────────────────────────────────────────────────────────────────────

def make_session() -> requests.Session:
    """Create a session with retry logic and connection pooling."""
    session = requests.Session()
    retry = Retry(
        total=2,
        backoff_factor=0.3,
        status_forcelist=[502, 503, 504],
        allowed_methods=["GET", "POST", "PUT", "PATCH", "DELETE"],
    )
    adapter = HTTPAdapter(max_retries=retry, pool_connections=10, pool_maxsize=20)
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    session.headers.update({"Content-Type": "application/json", "Accept": "application/json"})
    return session


def call(session, method: str, url: str, label: str, **kwargs):
    """
    Make one HTTP call, log the result, and record stats.
    Returns the Response object or None on network error.
    """
    kwargs.setdefault("timeout", REQUEST_TIMEOUT)
    try:
        resp = session.request(method, url, **kwargs)
        STATS.record(resp.status_code)
        _log(resp.status_code, method, label, resp)
        return resp
    except requests.exceptions.ConnectionError:
        STATS.record_error()
        _log_err(f"CONNECTION ERROR → {url}  (Is the Clinic API running?)")
        return None
    except requests.exceptions.Timeout:
        STATS.record_error()
        _log_err(f"TIMEOUT → {url}")
        return None
    except Exception as exc:
        STATS.record_error()
        _log_err(f"UNEXPECTED → {url}  {exc}")
        return None


def _log(status: int, method: str, label: str, resp):
    if status in (200, 201, 204):
        color = Fore.GREEN
    elif status in (400, 404, 409, 422):
        color = Fore.YELLOW
    else:
        color = Fore.RED

    body_preview = ""
    try:
        body = resp.json()
        if isinstance(body, dict) and "message" in body:
            body_preview = f"  ← {body['message'][:80]}"
        elif isinstance(body, dict) and "error" in body:
            body_preview = f"  ← {body['error']}: {body.get('message','')[:60]}"
    except Exception:
        pass

    ts = datetime.datetime.now().strftime("%H:%M:%S")
    print(
        f"{Style.DIM}{ts}{Style.RESET_ALL}  "
        f"{color}{status}{Style.RESET_ALL}  "
        f"{Fore.CYAN}{method:<6}{Style.RESET_ALL}  "
        f"{label:<45}"
        f"{Style.DIM}{body_preview}{Style.RESET_ALL}"
    )


def _log_err(msg: str):
    ts = datetime.datetime.now().strftime("%H:%M:%S")
    print(f"{Style.DIM}{ts}{Style.RESET_ALL}  {Fore.RED}ERR{Style.RESET_ALL}  {msg}")


def _log_section(title: str):
    ts = datetime.datetime.now().strftime("%H:%M:%S")
    print(
        f"\n{Style.DIM}{ts}{Style.RESET_ALL}  "
        f"{Fore.MAGENTA}{Style.BRIGHT}{'─'*10} {title} {'─'*10}{Style.RESET_ALL}"
    )


def think():
    time.sleep(random.uniform(THINK_TIME_MIN, THINK_TIME_MAX))


# ─────────────────────────────────────────────────────────────────────────────
# DATA GENERATORS
# ─────────────────────────────────────────────────────────────────────────────

def random_name() -> str:
    return f"{random.choice(FIRST_NAMES)} {random.choice(LAST_NAMES)}"

def random_email(name: str, suffix: str = "") -> str:
    clean = name.lower().replace(" ", ".") + suffix
    domain = random.choice(["clinic.com", "hospital.org", "health.in", "mail.com"])
    return f"{clean}.{random.randint(100,9999)}@{domain}"

def random_contact() -> str:
    return f"9{random.randint(100000000, 999999999)}"

def random_dob() -> str:
    year  = random.randint(1955, 2004)
    month = random.randint(1, 12)
    day   = random.randint(1, 28)
    return f"{year}-{month:02d}-{day:02d}"

def future_date(days_ahead: int = None) -> str:
    if days_ahead is None:
        days_ahead = random.randint(1, 30)
    d = datetime.date.today() + datetime.timedelta(days=days_ahead)
    return d.isoformat()


def doctor_payload(name: str = None) -> dict:
    name = name or ("Dr. " + random_name())
    return {
        "name":           name,
        "gender":         random.choice(GENDERS),
        "specialization": random.choice(SPECIALIZATIONS),
        "contact":        random_contact(),
        "email":          random_email(name, "-doc"),
        "password":       "Pass@1234",
    }

def patient_payload(name: str = None) -> dict:
    name = name or random_name()
    return {
        "name":        name,
        "dateOfBirth": random_dob(),
        "gender":      random.choice(GENDERS),
        "contact":     random_contact(),
        "email":       random_email(name, "-pat"),
        "password":    "Pass@1234",
        "bloodGroup":  random.choice(BLOOD_GROUPS),
    }

def appointment_payload(doctor_id: int, patient_id: int,
                        days_ahead: int = None, slot: str = None) -> dict:
    return {
        "doctorId":        doctor_id,
        "patientId":       patient_id,
        "appointmentDate": future_date(days_ahead),
        "slot":            slot or random.choice(SLOTS),
        "reason":          random.choice(REASONS),
        "notes":           "Created by load simulator",
    }


# ─────────────────────────────────────────────────────────────────────────────
# SCENARIOS
# ─────────────────────────────────────────────────────────────────────────────

def scenario_happy_path(base: str, session: requests.Session, iteration: int):
    """
    Full lifecycle:
      POST /api/doctors          → 201
      POST /api/patients         → 201
      POST /api/appointments     → 201
      PATCH .../status CONFIRMED → 200
      GET  /api/appointments/{id}→ 200
      PATCH .../status COMPLETED → 200
    Drives: clinic_appointments_created_total, http_server_requests latency histograms
    """
    _log_section(f"[happy_path] iteration {iteration}")

    # Register a doctor
    doc = call(session, "POST", f"{base}/api/doctors",
               "POST /api/doctors (register)",
               json=doctor_payload())
    think()
    if not doc or doc.status_code != 201:
        return
    doctor_id = doc.json().get("doctorId")

    # Register a patient
    pat = call(session, "POST", f"{base}/api/patients",
               "POST /api/patients (register)",
               json=patient_payload())
    think()
    if not pat or pat.status_code != 201:
        return
    patient_id = pat.json().get("patientId")

    # Book appointment
    appt = call(session, "POST", f"{base}/api/appointments",
                "POST /api/appointments (book)",
                json=appointment_payload(doctor_id, patient_id))
    think()
    if not appt or appt.status_code != 201:
        return
    appt_id = appt.json().get("appointmentId")

    # Confirm it
    call(session, "PATCH", f"{base}/api/appointments/{appt_id}/status",
         f"PATCH /api/appointments/{appt_id}/status CONFIRMED",
         params={"status": "CONFIRMED"})
    think()

    # Read it back
    call(session, "GET", f"{base}/api/appointments/{appt_id}",
         f"GET /api/appointments/{appt_id}")
    think()

    # Complete it
    call(session, "PATCH", f"{base}/api/appointments/{appt_id}/status",
         f"PATCH /api/appointments/{appt_id}/status COMPLETED",
         params={"status": "COMPLETED"})


def scenario_slot_conflict(base: str, session: requests.Session, iteration: int):
    """
    Books the same doctor + date + slot twice.
    Second request must return 409 Conflict (DuplicateResourceException).
    Drives: 4xx error rate panel — yellow but expected
    """
    _log_section(f"[slot_conflict] iteration {iteration}")

    doc = call(session, "POST", f"{base}/api/doctors",
               "POST /api/doctors",
               json=doctor_payload())
    think()
    if not doc or doc.status_code != 201:
        return
    doctor_id = doc.json().get("doctorId")

    pat1 = call(session, "POST", f"{base}/api/patients",
                "POST /api/patients (patient 1)",
                json=patient_payload())
    think()
    pat2 = call(session, "POST", f"{base}/api/patients",
                "POST /api/patients (patient 2)",
                json=patient_payload())
    think()
    if not pat1 or not pat2:
        return
    if pat1.status_code != 201 or pat2.status_code != 201:
        return

    fixed_date = future_date(3)
    fixed_slot = "10:00"

    # First booking — should succeed
    call(session, "POST", f"{base}/api/appointments",
         "POST /api/appointments (booking 1 — should succeed)",
         json={**appointment_payload(doctor_id, pat1.json()["patientId"]),
               "appointmentDate": fixed_date, "slot": fixed_slot})
    think()

    # Second booking on same slot — should return 409
    call(session, "POST", f"{base}/api/appointments",
         "POST /api/appointments (booking 2 — expect 409 conflict)",
         json={**appointment_payload(doctor_id, pat2.json()["patientId"]),
               "appointmentDate": fixed_date, "slot": fixed_slot})


def scenario_bad_doctor(base: str, session: requests.Session, iteration: int):
    """
    Tries to book an appointment with a doctorId that doesn't exist.
    Returns 404 (ResourceNotFoundException).
    Drives: 404 error spike — exercises GlobalExceptionHandler
    """
    _log_section(f"[bad_doctor] iteration {iteration}")

    # Register a valid patient first
    pat = call(session, "POST", f"{base}/api/patients",
               "POST /api/patients",
               json=patient_payload())
    think()
    if not pat or pat.status_code != 201:
        return
    patient_id = pat.json().get("patientId")

    # Use a doctorId that cannot possibly exist
    fake_doctor_id = random.randint(99000, 99999)
    call(session, "POST", f"{base}/api/appointments",
         f"POST /api/appointments (doctorId={fake_doctor_id} — expect 404)",
         json=appointment_payload(fake_doctor_id, patient_id))
    think()

    # Also try fetching a non-existent doctor directly
    call(session, "GET", f"{base}/api/doctors/{fake_doctor_id}",
         f"GET /api/doctors/{fake_doctor_id} — expect 404")


def scenario_bad_validation(base: str, session: requests.Session, iteration: int):
    """
    Sends intentionally malformed payloads to trigger Bean Validation errors.
    Returns 400 (MethodArgumentNotValidException via GlobalExceptionHandler).
    Drives: 4xx spike — shows validationErrors in the error response body
    """
    _log_section(f"[bad_validation] iteration {iteration}")

    bad_payloads = [
        # Missing required name
        {
            "gender": "Male",
            "specialization": "CARDIOLOGY",
            "contact": "9876543210",
            "email": "valid@email.com",
            "password": "Pass@1234",
        },
        # Invalid email format
        {
            "name": "Dr. Test",
            "gender": "Male",
            "specialization": "CARDIOLOGY",
            "contact": "9876543210",
            "email": "not-an-email",
            "password": "Pass@1234",
        },
        # Contact too short
        {
            "name": "Dr. Test",
            "gender": "Female",
            "specialization": "PEDIATRICS",
            "contact": "123",          # must be 10 digits
            "email": "doc@test.com",
            "password": "Pass@1234",
        },
        # Password too short
        {
            "name": "Dr. Test",
            "gender": "Male",
            "specialization": "NEUROLOGY",
            "contact": "9876543210",
            "email": "doc2@test.com",
            "password": "ab",          # min 6 chars
        },
        # Completely empty body
        {},
    ]

    payload = random.choice(bad_payloads)
    call(session, "POST", f"{base}/api/doctors",
         "POST /api/doctors (bad payload — expect 400)",
         json=payload)
    think()

    # Also send a bad appointment (past date)
    bad_appt = {
        "doctorId":        1,
        "patientId":       1,
        "appointmentDate": "2020-01-01",   # @FutureOrPresent will reject this
        "slot":            "09:00",
        "reason":          "Past date test",
    }
    call(session, "POST", f"{base}/api/appointments",
         "POST /api/appointments (past date — expect 400)",
         json=bad_appt)


def scenario_read_traffic(base: str, session: requests.Session, iteration: int):
    """
    Sends a burst of GET requests across all read endpoints.
    Drives: GET request rate, low-latency histogram buckets
    """
    _log_section(f"[read_traffic] iteration {iteration}")

    endpoints = [
        ("GET", "/api/doctors",             "GET /api/doctors (list all)"),
        ("GET", "/api/doctors/available",   "GET /api/doctors/available"),
        ("GET", "/api/patients",            "GET /api/patients (list all)"),
        ("GET", "/api/appointments",        "GET /api/appointments (list all)"),
    ]

    # Add status-filtered reads
    for status in ["SCHEDULED", "CONFIRMED", "COMPLETED", "CANCELLED"]:
        endpoints.append((
            "GET",
            f"/api/appointments/status/{status}",
            f"GET /api/appointments/status/{status}",
        ))

    # Add specialization reads
    for spec in random.sample(SPECIALIZATIONS, 3):
        endpoints.append((
            "GET",
            f"/api/doctors/specialization/{spec}",
            f"GET /api/doctors/specialization/{spec}",
        ))

    random.shuffle(endpoints)
    for method, path, label in endpoints:
        call(session, method, f"{base}{path}", label)
        think()

    # Name search — exercises LIKE query
    name_fragment = random.choice(["Dr", "Sharma", "Kumar", "Patel", "Rao"])
    call(session, "GET", f"{base}/api/doctors/search/name",
         f"GET /api/doctors/search/name?name={name_fragment}",
         params={"name": name_fragment})


def scenario_cancel_flood(base: str, session: requests.Session, iteration: int):
    """
    Creates appointments and immediately cancels them.
    Drives: clinic_appointments_cancelled_total counter,
            high cancellation rate business alert
    """
    _log_section(f"[cancel_flood] iteration {iteration}")

    # Reuse doctors and patients by fetching existing ones
    docs_resp = call(session, "GET", f"{base}/api/doctors/available",
                     "GET /api/doctors/available (for cancel flood)")
    think()
    pats_resp = call(session, "GET", f"{base}/api/patients",
                     "GET /api/patients (for cancel flood)")
    think()

    doctors  = docs_resp.json() if docs_resp and docs_resp.status_code == 200 else []
    patients = pats_resp.json() if pats_resp and pats_resp.status_code == 200 else []

    if not doctors or not patients:
        _log_err("cancel_flood: no doctors or patients yet — skipping this iteration")
        return

    # Create and immediately cancel 3–6 appointments
    n = random.randint(3, 6)
    for i in range(n):
        doctor_id  = random.choice(doctors)["doctorId"]
        patient_id = random.choice(patients)["patientId"]

        appt = call(session, "POST", f"{base}/api/appointments",
                    f"POST /api/appointments (cancel flood {i+1}/{n})",
                    json=appointment_payload(
                        doctor_id, patient_id,
                        days_ahead=random.randint(5, 60)
                    ))
        think()
        if not appt or appt.status_code != 201:
            continue

        appt_id = appt.json().get("appointmentId")
        call(session, "PATCH",
             f"{base}/api/appointments/{appt_id}/status",
             f"PATCH /api/appointments/{appt_id}/status CANCELLED",
             params={"status": "CANCELLED"})
        think()


# ─────────────────────────────────────────────────────────────────────────────
# SCENARIO REGISTRY
# ─────────────────────────────────────────────────────────────────────────────

ALL_SCENARIOS = {
    "happy_path":      scenario_happy_path,
    "slot_conflict":   scenario_slot_conflict,
    "bad_doctor":      scenario_bad_doctor,
    "bad_validation":  scenario_bad_validation,
    "read_traffic":    scenario_read_traffic,
    "cancel_flood":    scenario_cancel_flood,
}


# ─────────────────────────────────────────────────────────────────────────────
# THREAD WORKER
# ─────────────────────────────────────────────────────────────────────────────

_stop_event = threading.Event()

def worker(name: str, fn, base: str, iterations: int):
    """Run one scenario in a loop until stop_event is set or iterations hit."""
    session = make_session()
    i = 0
    while not _stop_event.is_set():
        i += 1
        try:
            fn(base, session, i)
        except Exception as exc:
            _log_err(f"[{name}] unhandled exception: {exc}")
        if iterations and i >= iterations:
            break
        # Brief pause between full scenario cycles
        time.sleep(random.uniform(0.5, 2.0))
    session.close()
    print(f"{Fore.YELLOW}[{name}] worker finished.{Style.RESET_ALL}")


# ─────────────────────────────────────────────────────────────────────────────
# HEALTH CHECK
# ─────────────────────────────────────────────────────────────────────────────

def wait_for_api(base: str, retries: int = 10, delay: float = 3.0):
    print(f"\n{Fore.CYAN}Checking Clinic API at {base}/actuator/health ...{Style.RESET_ALL}")
    session = requests.Session()
    for attempt in range(1, retries + 1):
        try:
            r = session.get(f"{base}/actuator/health", timeout=4)
            if r.status_code == 200:
                status = r.json().get("status", "?")
                print(f"{Fore.GREEN}✔  API is {status} (attempt {attempt}){Style.RESET_ALL}\n")
                return True
        except Exception:
            pass
        print(f"  Attempt {attempt}/{retries} — not ready yet, retrying in {delay}s...")
        time.sleep(delay)
    print(f"{Fore.RED}✘  Could not reach the Clinic API. Is it running on {base}?{Style.RESET_ALL}")
    return False


# ─────────────────────────────────────────────────────────────────────────────
# STATS PRINTER
# ─────────────────────────────────────────────────────────────────────────────

def stats_printer():
    """Prints a summary line every 15 seconds."""
    while not _stop_event.is_set():
        time.sleep(15)
        if not _stop_event.is_set():
            print(
                f"\n{Fore.BLUE}{Style.BRIGHT}"
                f"{'═'*20} STATS {'═'*20}\n"
                f"  {STATS.summary()}\n"
                f"{'═'*47}{Style.RESET_ALL}\n"
            )


# ─────────────────────────────────────────────────────────────────────────────
# BANNER
# ─────────────────────────────────────────────────────────────────────────────

def print_banner(base: str, scenarios: list, workers: int, iterations: int):
    print(f"""
{Fore.BLUE}{Style.BRIGHT}
╔══════════════════════════════════════════════════════════╗
║       Clinic API — Prometheus & Grafana Simulator        ║
║       The Skill Enhancers | Oracle Bootcamp              ║
╚══════════════════════════════════════════════════════════╝
{Style.RESET_ALL}
  {Fore.CYAN}API URL    :{Style.RESET_ALL}  {base}
  {Fore.CYAN}Scenarios  :{Style.RESET_ALL}  {', '.join(scenarios)}
  {Fore.CYAN}Workers    :{Style.RESET_ALL}  {workers}
  {Fore.CYAN}Iterations :{Style.RESET_ALL}  {"∞ (until Ctrl+C)" if not iterations else iterations}

  {Fore.YELLOW}What to watch in Prometheus (http://localhost:9090):{Style.RESET_ALL}
    clinic_appointments_created_total
    clinic_appointments_cancelled_total
    clinic_appointments_scheduled_total
    rate(http_server_requests_seconds_count[1m])

  {Fore.YELLOW}What to watch in Grafana (http://localhost:3000):{Style.RESET_ALL}
    Application dashboard → all panels animate with live data
    Error rate % → spikes from bad_doctor + bad_validation scenarios
    Cancellation rate → rises from cancel_flood scenario

  {Fore.GREEN}Press Ctrl+C to stop gracefully.{Style.RESET_ALL}
""")


# ─────────────────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Clinic API load simulator for Prometheus & Grafana training"
    )
    parser.add_argument(
        "--url", default=DEFAULT_BASE_URL,
        help=f"Base URL of the Clinic API (default: {DEFAULT_BASE_URL})"
    )
    parser.add_argument(
        "--workers", type=int, default=DEFAULT_WORKERS,
        help=f"Number of concurrent worker threads (default: {DEFAULT_WORKERS})"
    )
    parser.add_argument(
        "--iterations", type=int, default=DEFAULT_ITERATIONS,
        help="Iterations per scenario — 0 means run forever (default: 0)"
    )
    parser.add_argument(
        "--scenarios", default=",".join(ALL_SCENARIOS.keys()),
        help="Comma-separated list of scenarios to run. "
             f"Available: {', '.join(ALL_SCENARIOS.keys())}"
    )
    args = parser.parse_args()

    # Parse and validate scenarios
    requested = [s.strip() for s in args.scenarios.split(",") if s.strip()]
    invalid   = [s for s in requested if s not in ALL_SCENARIOS]
    if invalid:
        print(f"[ERROR] Unknown scenarios: {invalid}")
        print(f"Available: {list(ALL_SCENARIOS.keys())}")
        sys.exit(1)

    print_banner(args.url, requested, args.workers, args.iterations)

    # Health check
    if not wait_for_api(args.url):
        sys.exit(1)

    # Graceful shutdown on Ctrl+C
    def _handle_sigint(sig, frame):
        print(f"\n{Fore.YELLOW}Shutting down... (waiting for workers to finish){Style.RESET_ALL}")
        _stop_event.set()

    signal.signal(signal.SIGINT, _handle_sigint)

    # Launch stats printer
    t_stats = threading.Thread(target=stats_printer, daemon=True)
    t_stats.start()

    # Launch worker threads — distribute scenarios across workers
    threads = []
    for i in range(args.workers):
        scenario_name = requested[i % len(requested)]
        fn = ALL_SCENARIOS[scenario_name]
        t = threading.Thread(
            target=worker,
            args=(scenario_name, fn, args.url, args.iterations),
            name=f"worker-{scenario_name}-{i}",
            daemon=True,
        )
        threads.append(t)
        t.start()

    # Wait for all workers
    for t in threads:
        t.join()

    _stop_event.set()

    print(f"\n{Fore.BLUE}{Style.BRIGHT}Final Stats:{Style.RESET_ALL}")
    print(f"  {STATS.summary()}")
    print(f"\n{Fore.GREEN}Done.{Style.RESET_ALL}")


if __name__ == "__main__":
    main()
