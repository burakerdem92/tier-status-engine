CREATE SCHEMA IF NOT EXISTS reference_data;

CREATE TABLE raw_event (
    id VARCHAR(36) PRIMARY KEY,
    ingestion_key VARCHAR(200) NOT NULL UNIQUE,
    event_id VARCHAR(100) UNIQUE,
    event_type VARCHAR(40),
    member_id VARCHAR(64),
    event_time TIMESTAMP WITH TIME ZONE,
    source VARCHAR(80),
    raw_payload TEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    processing_status VARCHAR(40) NOT NULL,
    validation_error VARCHAR(2000),
    processing_note VARCHAR(2000),
    topic VARCHAR(200) NOT NULL,
    partition_no INTEGER NOT NULL,
    offset_no BIGINT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_raw_event_member_time ON raw_event(member_id, event_time);
CREATE INDEX idx_raw_event_status ON raw_event(processing_status, received_at);

CREATE TABLE mileage_ledger (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(64) NOT NULL,
    source_event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(40) NOT NULL,
    source VARCHAR(80) NOT NULL,
    activity_date DATE NOT NULL,
    period_year INTEGER NOT NULL,
    status_miles INTEGER NOT NULL,
    original_movement_id VARCHAR(36),
    rate_version VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ledger_original FOREIGN KEY (original_movement_id) REFERENCES mileage_ledger(id)
);

CREATE INDEX idx_ledger_member_date ON mileage_ledger(member_id, activity_date, id);
CREATE INDEX idx_ledger_member_period ON mileage_ledger(member_id, period_year);

CREATE TABLE member_period (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(64) NOT NULL,
    period_year INTEGER NOT NULL,
    total_status_miles INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_member_period UNIQUE (member_id, period_year)
);

CREATE INDEX idx_member_period_year ON member_period(period_year, member_id);

CREATE TABLE member_status (
    member_id VARCHAR(64) PRIMARY KEY,
    tier VARCHAR(40) NOT NULL,
    valid_until DATE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_member_status_expiry ON member_status(valid_until, member_id);

CREATE TABLE tier_history (
    id VARCHAR(36) PRIMARY KEY,
    decision_key VARCHAR(300) NOT NULL UNIQUE,
    member_id VARCHAR(64) NOT NULL,
    previous_tier VARCHAR(40) NOT NULL,
    new_tier VARCHAR(40) NOT NULL,
    change_type VARCHAR(40) NOT NULL,
    qualification_period INTEGER NOT NULL,
    status_miles_in_period INTEGER NOT NULL,
    effective_date DATE NOT NULL,
    valid_until DATE,
    triggered_by_event_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_tier_history_member_date ON tier_history(member_id, effective_date, id);

CREATE TABLE pending_reversal (
    original_event_id VARCHAR(100) PRIMARY KEY,
    reversal_event_id VARCHAR(100) NOT NULL UNIQUE,
    member_id VARCHAR(64) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(200) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    claimed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_publish ON outbox_event(status, next_attempt_at, created_at);

CREATE TABLE reference_data.flight_rate (
    id VARCHAR(36) PRIMARY KEY,
    operating_carrier VARCHAR(10) NOT NULL,
    booking_class VARCHAR(10) NOT NULL,
    rate NUMERIC(10,4) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    rule_version VARCHAR(80) NOT NULL
);

CREATE INDEX idx_flight_rate_lookup
    ON reference_data.flight_rate(operating_carrier, booking_class, effective_from);

CREATE TABLE reference_data.partner_rate (
    id VARCHAR(36) PRIMARY KEY,
    partner_code VARCHAR(80) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    rate NUMERIC(10,4) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    rule_version VARCHAR(80) NOT NULL
);

CREATE INDEX idx_partner_rate_lookup
    ON reference_data.partner_rate(partner_code, currency, effective_from);

INSERT INTO reference_data.flight_rate
    (id, operating_carrier, booking_class, rate, effective_from, effective_to, rule_version)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'TK', 'J', 1.5000, DATE '2020-01-01', NULL, '2026.1'),
    ('10000000-0000-0000-0000-000000000002', 'TK', 'Y', 1.0000, DATE '2020-01-01', NULL, '2026.1'),
    ('10000000-0000-0000-0000-000000000003', 'TK', 'P', 0.5000, DATE '2020-01-01', NULL, '2026.1'),
    ('10000000-0000-0000-0000-000000000004', 'TK', 'X', 0.0000, DATE '2020-01-01', NULL, '2026.1'),
    ('10000000-0000-0000-0000-000000000005', 'LH', 'C', 1.5000, DATE '2020-01-01', NULL, '2026.1');

INSERT INTO reference_data.partner_rate
    (id, partner_code, currency, rate, effective_from, effective_to, rule_version)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'AURORA_HOTELS', 'USD', 2.0000, DATE '2020-01-01', NULL, '2026.1'),
    ('20000000-0000-0000-0000-000000000002', 'DRIVEGO', 'EUR', 1.0000, DATE '2020-01-01', NULL, '2026.1'),
    ('20000000-0000-0000-0000-000000000003', 'DEMO_BANK', 'TRY', 0.0000, DATE '2020-01-01', NULL, '2026.1');
