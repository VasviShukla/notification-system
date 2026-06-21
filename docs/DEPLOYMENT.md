# Deployment Guide

This walks through (1) pushing the project to GitHub, and (2) deploying it
live using free infrastructure. Read the **"About the free Kafka problem"**
section before you start — it affects which path you take.

---

## Part 1 — Push to GitHub

```bash
cd notification-system
git init
git add .
git commit -m "Initial commit: notification system (Kafka + Redis + WebSocket + Spring Boot)"
```

Create an empty repo on GitHub (no README/license, you already have one):
[github.com/new](https://github.com/new) → name it e.g. `notification-system` →
**Create repository**.

GitHub will show you the remote URL. Back in your terminal:

```bash
git branch -M main
git remote add origin https://github.com/<your-username>/notification-system.git
git push -u origin main
```

That's it — refresh the GitHub page and your code is there. Every step
below deploys *from* this GitHub repo, so do this part first.

---

## About the free Kafka problem (read this first)

Short version: **there is no permanently-free hosted Kafka in 2026.**
Upstash discontinued its serverless Kafka offering, and Confluent Cloud /
Redpanda Cloud both give you a one-time trial credit ($400 for 30 days, and
$100 for 30 days respectively) rather than an ongoing free tier. Pick
whichever of these fits what you're trying to do:

| Goal | Recommended option |
|---|---|
| Record a demo video / take screenshots / show it live in an interview | **Confluent Cloud free trial** — fastest to set up, $400 credit covers months of light, intermittent demo traffic. |
| Have the whole thing genuinely live and free *indefinitely* | **Self-hosted Kafka-compatible broker (Redpanda) on an Oracle Cloud Always Free VM** — takes ~20 extra minutes but never expires and costs $0 forever. |
| Just want to run it on your own machine | **Skip this section** — `docker compose up` already gives you real Kafka for free, forever, with no cloud account at all. |

Both cloud paths are documented below; pick one.

### Option A — Confluent Cloud (fastest, time-limited)

1. Sign up at [confluent.cloud](https://confluent.cloud) (no credit card
   required for the trial) — you'll get **$400 in credits for your first 30
   days**.
2. Create a **Basic** cluster on any provider/region.
3. In the cluster, go to **Topics** → create a topic named `notifications`.
4. Go to **API Keys** → create a key scoped to that cluster. Note the **Key**
   and **Secret**.
5. From the cluster's **Cluster Settings**, copy the **Bootstrap server**
   address (looks like `pkc-xxxxx.us-east-1.aws.confluent.cloud:9092`).
6. You'll set these env vars on both `producer-service` and
   `consumer-service` when you deploy them in Part 2:
   ```
   KAFKA_BOOTSTRAP_SERVERS=pkc-xxxxx.us-east-1.aws.confluent.cloud:9092
   KAFKA_SECURITY_PROTOCOL=SASL_SSL
   KAFKA_SASL_MECHANISM=PLAIN
   KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="<API_KEY>" password="<API_SECRET>";
   ```
   (Both services already support these env vars — see
   `KafkaProducerConfig.java` and `consumer-service/application.yml`.)

Set a calendar reminder for day 25 — after the trial, Confluent bills you
unless you cancel.

### Option B — Self-hosted Redpanda on Oracle Cloud Always Free (permanent, $0)

Redpanda speaks the Kafka protocol, so nothing in the code changes — you
just point `KAFKA_BOOTSTRAP_SERVERS` at your own VM instead of a managed
service.

1. Sign up for [Oracle Cloud](https://signup.oraclecloud.com) — note: Oracle
   requires a credit card for identity verification, but **Always Free**
   resources are never billed as long as you stay within the free limits.
2. Create a VM: **Compute → Instances → Create Instance**. Choose shape
   **VM.Standard.A1.Flex** (Ampere/ARM) and set it to the free allotment
   (2 OCPU / 12 GB RAM as of mid-2026 — the console shows current limits).
   Pick Ubuntu as the image. Download/save the SSH key when prompted.
3. Open the firewall: under the instance's subnet **Security List**, add an
   ingress rule allowing TCP port `9092` from your Render services' IP
   range (or `0.0.0.0/0` for simplicity in a portfolio project — don't do
   this for anything holding real user data).
4. SSH in and run Redpanda via Docker:
   ```bash
   ssh ubuntu@<your-vm-public-ip>
   sudo apt update && sudo apt install -y docker.io
   sudo docker run -d --name redpanda -p 9092:9092 \
     docker.redpanda.com/redpandadata/redpanda:latest \
     redpanda start --smp 1 --memory 1G --overprovisioned \
     --node-id 0 --check=false \
     --kafka-addr PLAINTEXT://0.0.0.0:9092 \
     --advertise-kafka-addr PLAINTEXT://<your-vm-public-ip>:9092
   ```
5. Set on both `producer-service` and `consumer-service`:
   ```
   KAFKA_BOOTSTRAP_SERVERS=<your-vm-public-ip>:9092
   ```
   (leave `KAFKA_SECURITY_PROTOCOL` unset — it defaults to `PLAINTEXT`,
   which is fine since this is a demo, not a production deployment handling
   real user data).

---

## Part 2 — Free Postgres (Neon) and free Redis (Upstash)

### Postgres → Neon

1. Sign up at [neon.tech](https://neon.tech) (no credit card). The free
   plan never expires: 0.5 GB storage, 100 compute-hours/month, scales to
   zero when idle.
2. Create a project. Neon gives you a connection string immediately, e.g.:
   ```
   postgresql://<user>:<password>@<host>/<dbname>?sslmode=require
   ```
3. `consumer-service` expects a JDBC URL, not the `postgresql://` form Neon
   shows by default. Convert it:
   ```
   DATABASE_URL=jdbc:postgresql://<host>/<dbname>?sslmode=require
   DATABASE_USERNAME=<user>
   DATABASE_PASSWORD=<password>
   ```
4. Flyway will create the `notifications` table automatically on
   `consumer-service`'s first startup — you don't need to run any SQL by
   hand.

### Redis → Upstash

1. Sign up at [upstash.com](https://upstash.com) (no credit card). The free
   plan never expires: 256 MB storage, 10,000 commands/day — plenty for a
   demo.
2. Create a Redis database (any region close to where you'll deploy on
   Render).
3. From the database details page, copy:
   ```
   REDIS_HOST=<name>.upstash.io
   REDIS_PORT=6379
   REDIS_PASSWORD=<your-password>
   REDIS_SSL=true
   ```
   Both `consumer-service` and `websocket-gateway` need all four of these.

---

## Part 3 — Deploy the three Spring Boot services on Render

1. Sign up at [render.com](https://render.com) (you can use your GitHub
   account — no credit card needed for free services).
2. **New → Blueprint** → connect your GitHub account → select the
   `notification-system` repo. Render reads `render.yaml` at the repo root
   and proposes all three services at once (`notifsys-producer`,
   `notifsys-consumer`, `notifsys-gateway`).
3. Render will prompt you to fill in the env vars marked `sync: false` in
   `render.yaml`. Fill them in from what you gathered in Parts 1 and 2:

   **notifsys-producer**
   - `KAFKA_BOOTSTRAP_SERVERS`, and if using Confluent Cloud also
     `KAFKA_SECURITY_PROTOCOL`, `KAFKA_SASL_MECHANISM`, `KAFKA_SASL_JAAS_CONFIG`

   **notifsys-consumer**
   - same Kafka vars as above, plus
   - `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (from Neon)
   - `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` (from Upstash)

   **notifsys-gateway**
   - `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` (from Upstash)

4. Click **Apply** / **Create**. Render builds each service from its own
   Dockerfile (this takes a few minutes the first time — Maven is
   downloading dependencies inside the build).
5. Once live, each service has a public URL like
   `https://notifsys-producer.onrender.com`. Note all three.

### Free-tier behavior to expect on Render

- Each free web service **spins down after 15 minutes of no traffic** and
  takes 30-60 seconds to wake back up on the next request. Your first demo
  request after a break will look "slow" — that's this, not a bug.
- 512 MB RAM / 0.1 CPU per free service is enough for this app's light demo
  traffic, not for load testing.
- If you outgrow this later, Render's paid plans remove the spin-down
  behavior — nothing in the code needs to change.

---

## Part 4 — Deploy the frontend

The demo page is a single static HTML file with no build step, so the
simplest free option is **GitHub Pages** (you already have the repo there):

1. In your GitHub repo: **Settings → Pages**.
2. Under **Source**, choose **Deploy from a branch**, branch `main`,
   folder `/frontend`. Save.
3. GitHub gives you a URL like
   `https://<username>.github.io/notification-system/`.
4. Open it, and in the **endpoints** panel at the top-left, replace the
   three `localhost` URLs with your three Render URLs from Part 3, e.g.:
   ```
   http://localhost:8081  →  https://notifsys-producer.onrender.com
   http://localhost:8082  →  https://notifsys-consumer.onrender.com
   http://localhost:8083  →  https://notifsys-gateway.onrender.com
   ```
   (Use `https://`, not `http://` — Render serves everything over TLS.)

---

## Verifying the full deployed pipeline

1. Open your GitHub Pages URL, type a user id, click **connect**. The
   connection LED should turn amber (give it up to a minute the first time,
   for Render's cold start).
2. Click any demo button (e.g. **order shipped**). Within a couple of
   seconds you should see it appear in the **live feed** panel — that
   round trip touched all three services, Kafka, Postgres, and Redis.
3. Click **refresh history** — you should see the same event, now read
   back from Postgres via Neon.
4. Open the same page in a second tab with the **same user id**, fire
   another event from either tab, and confirm it shows up in both — that's
   the Redis pub/sub fan-out working across whichever gateway instance
   each tab happens to be connected to.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Live feed never updates, but "refresh history" eventually shows the event | `websocket-gateway` can't reach Redis, or the page is still pointed at `localhost` for the gateway URL. Check `REDIS_*` env vars and the endpoint fields in the frontend. |
| Producer returns 202 but nothing ever shows up anywhere | `consumer-service` can't reach Kafka — check `KAFKA_BOOTSTRAP_SERVERS`/SASL settings and Render's logs for that service. |
| `consumer-service` crashes on startup | Usually a Postgres connection/credentials issue, or Flyway failing because `DATABASE_URL` is in `postgresql://` form instead of `jdbc:postgresql://`. |
| Everything works locally but not on Render | Double- and triple-check you're using the Render `https://` URLs (not `localhost`) in the frontend, and that Confluent/Redpanda firewall rules allow Render's outbound IPs. |
