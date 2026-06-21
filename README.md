# notif:sys — Real-Time Notification System

A distributed, event-driven notification system built to demonstrate the core
patterns behind systems like Slack/GitHub/Twitter notifications: **Kafka**
for decoupled event delivery, **Redis** for caching and pub/sub fan-out,
**WebSockets** for live push, and three independently deployable **Spring
Boot** microservices.

```
                    ┌─────────────────┐
   REST request --> │ producer-service │ --> Kafka topic "notifications"
                    └─────────────────┘
                                            │
                                            ▼
                                  ┌──────────────────┐
                                  │ consumer-service  │
                                  │  • saves -> Postgres (history)
                                  │  • caches -> Redis (recent feed, unread count)
                                  │  • publishes -> Redis pub/sub (live)
                                  └──────────────────┘
                                            │
                                            ▼
                                  ┌──────────────────┐
                                  │ websocket-gateway │ --> browser (STOMP/WS)
                                  └──────────────────┘
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design
rationale (why Kafka here, why Redis pub/sub instead of just WebSockets, how
this scales horizontally) and [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for
pushing this to GitHub and deploying it on free infrastructure.

## Repo layout

| Path                  | What it is                                                   |
|------------------------|---------------------------------------------------------------|
| `producer-service/`    | Spring Boot REST API that publishes events to Kafka           |
| `consumer-service/`     | Spring Boot Kafka consumer — Postgres + Redis + pub/sub        |
| `websocket-gateway/`    | Spring Boot STOMP/WebSocket server — relays Redis pub/sub      |
| `frontend/index.html`   | Single-file demo console (no build step, just open it)        |
| `docker-compose.yml`   | Full local stack: Kafka, Postgres, Redis, Kafka UI, all 3 apps |
| `docs/`                | Architecture + deployment guides                              |
| `render.yaml`          | Render Blueprint for one-click multi-service deploy            |

## Run it locally (free, no signups, ~2 minutes)

Requires Docker + Docker Compose.

```bash
git clone <your-fork-url>
cd notification-system
docker compose up --build
```

This starts:
- `producer-service` → http://localhost:8081
- `consumer-service` → http://localhost:8082
- `websocket-gateway` → http://localhost:8083
- Kafka UI (watch topics/messages live) → http://localhost:8085
- Postgres on `5432`, Redis on `6379`, Kafka on `9092`

Then open `frontend/index.html` directly in your browser (double-click it,
or `open frontend/index.html` / `xdg-open frontend/index.html`). The default
endpoint URLs in the page already point at `localhost`, so it works
immediately. Type a `user id`, hit **connect**, then click one of the demo
buttons — you'll see the event tail through the live feed in real time.

To prove the architecture is real, open `frontend/index.html` in **two
browser tabs** with the same user id, fire one event, and watch it land in
both tabs — that's Redis pub/sub fanning the message out to every
`websocket-gateway` subscriber, not the browser tabs talking to each other.

## Try the API directly

```bash
# Trigger a notification
curl -X POST http://localhost:8081/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"userId":"alice","type":"ORDER_SHIPPED","title":"Shipped!","message":"Order #4821 is on its way"}'

# Read history (Postgres)
curl http://localhost:8082/api/notifications/alice

# Unread count (Redis)
curl http://localhost:8082/api/notifications/alice/unread-count
```

## Tech stack

Java 17 · Spring Boot 3.2 · Spring Kafka · Spring Data JPA · Spring Data
Redis (Lettuce) · Spring WebSocket (STOMP + SockJS) · PostgreSQL · Flyway ·
Apache Kafka (KRaft mode) · Docker Compose

## License

MIT — use this however you like, including as a portfolio piece.
