CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type  VARCHAR(80) NOT NULL,
    aggregate_id    VARCHAR(80) NOT NULL,
    event_type      VARCHAR(80) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);
