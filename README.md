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



## Tech stack

Java 17 · Spring Boot 3.2 · Spring Kafka · Spring Data JPA · Spring Data
Redis (Lettuce) · Spring WebSocket (STOMP + SockJS) · PostgreSQL · Flyway ·
Apache Kafka (KRaft mode) · Docker Compose

## License

MIT — use this however you like, including as a portfolio piece.
