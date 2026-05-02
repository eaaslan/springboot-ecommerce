CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL,
    order_id        BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    payload         TEXT NOT NULL,
    failure_reason  VARCHAR(500),
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX idx_notifications_order_id ON notifications(order_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);

CREATE TABLE processed_events (
    event_id        VARCHAR(36) PRIMARY KEY,
    event_type      VARCHAR(80) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_type ON processed_events(event_type);
