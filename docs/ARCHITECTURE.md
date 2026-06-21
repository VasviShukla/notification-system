# Architecture

## Why three services instead of one Spring Boot app?

A single monolith could do all of this in less code. The point of this
project is to demonstrate the patterns you'd actually reach for once a
notification system needs to handle real traffic:

- **producer-service** only knows how to accept a request and put an event
  on a topic. It never touches a database or a WebSocket. If it crashes, no
  user-facing connection drops, and you can scale it independently of
  everything else (e.g. during a marketing campaign that fires a lot of
  emails/notifications at once).
- **consumer-service** does the actual work (persistence, caching, fan-out
  trigger) and can be scaled to multiple instances in the same Kafka
  consumer group — Kafka automatically spreads partitions across them, so
  throughput grows roughly linearly with instance count.
- **websocket-gateway** holds nothing but live client connections. It's
  stateless with respect to business logic, so you can run N replicas
  behind a load balancer purely to handle more concurrent sockets.

This is the same shape as production notification systems at companies like
Slack/GitHub: an ingestion API, a stream-processing layer, and a connection
layer, talking through a broker instead of direct calls.

## Why Kafka instead of calling consumer-service directly over REST?

If producer-service called consumer-service synchronously:
- A slow database write would make the *original* HTTP request slow too.
- If consumer-service is down, the notification is just lost (no retry
  unless you build one yourself).
- You couldn't add a second consumer later (e.g. an email-sending consumer
  on the *same* event stream) without producer-service knowing about it.

Kafka decouples all of that: producer-service's job ends the instant the
broker acknowledges the write (`acks=all` + idempotent producer in this
project, so even a leader failover won't duplicate or drop the event). Any
number of independent consumers can subscribe to the same topic later
without touching the producer at all.

Events are **keyed by `userId`** when published
(`KafkaTemplate.send(topic, userId, payload)`), which guarantees Kafka
routes every event for the same user to the same partition — so per-user
ordering is preserved even with multiple partitions and multiple consumer
instances.

## Why Redis for the WebSocket fan-out, instead of just pushing from the consumer?

The hard problem in any "push to a live browser" system is: **which server
process is holding that user's socket right now?** If you run only one
`websocket-gateway` instance, you could skip Redis entirely and call
`SimpMessagingTemplate` straight from consumer-service. The moment you run
*more than one* gateway instance (which you need to once concurrent
connections exceed what one JVM can hold), consumer-service has no way of
knowing which instance has the user's connection.

Redis Pub/Sub solves this without any sticky-session or service-discovery
logic:

1. consumer-service publishes to channel `notif:live:{userId}`.
2. **Every** `websocket-gateway` instance is subscribed to the pattern
   `notif:live:*` and receives **every** message.
3. Each instance calls `SimpMessagingTemplate.convertAndSend(...)` for that
   user. On instances that don't have that user's socket open, this is a
   harmless no-op. On the one instance that does, the browser gets the push.

That's an "at most one instance does anything useful" pattern achieved with
zero coordination — Redis is doing the routing implicitly.

## Why also write to Postgres if Redis already has the data?

Redis is treated as a **cache**, not the system of record:
- It's in-memory — a restart or eviction can lose data; Redis's own
  `LTRIM` here intentionally caps history at the 50 most recent events per
  user and a 7-day TTL, so it's *not even trying* to be durable long-term
  storage.
- Postgres is the durable source of truth for the full notification history
  (paginated `GET /api/notifications/{userId}` reads from here).
- This is the standard **cache-aside** pattern: read the fast path first
  (Redis) for things like the unread badge count, fall back to the durable
  store for anything that needs completeness or history.

## Idempotency and at-least-once delivery

Kafka guarantees at-least-once delivery by default (a consumer can crash
after processing a message but before committing its offset, causing a
redelivery on restart). `NotificationService.process()` uses the event's
UUID as the Postgres primary key and checks `existsById` before inserting,
so a redelivered event is a safe no-op rather than a duplicate notification
or a double-incremented unread counter.

## What's deliberately left as an extension point

- **Dead-letter topic**: a message that fails to deserialize is currently
  just logged. A production system would route it to a
  `notifications.DLT` topic instead.
- **Auth**: the demo identifies users by a plain `userId` string for
  clarity. A real deployment would put a JWT-validating API gateway in
  front of `producer-service` and the WebSocket handshake.
- **STOMP broker relay**: the gateway uses Spring's simple in-memory broker,
  which is enough because Redis (not the broker) does the cross-instance
  fan-out here. At very large scale you'd swap in a full broker relay
  (e.g. RabbitMQ) for STOMP itself.
