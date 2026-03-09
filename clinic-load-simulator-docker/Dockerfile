FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY clinic_load_simulator.py .

CMD ["python", "clinic_load_simulator.py"]