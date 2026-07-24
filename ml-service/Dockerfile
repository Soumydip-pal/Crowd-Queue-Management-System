FROM python:3.11-slim
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# opencv-python-headless needs a couple of shared libs even in headless mode
RUN apt-get update \
    && apt-get install -y --no-install-recommends libglib2.0-0 libgl1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py train_model.py ./

# Train the wait-time model at build time so the image ships with a real
# scikit-learn model.pkl instead of falling back to the baseline formula.
# To retrain on real exported queue history later, mount a CSV and run:
#   docker compose exec ml-service python train_model.py --real-data /data/history.csv
RUN python train_model.py

EXPOSE 5001
CMD ["python", "app.py"]
