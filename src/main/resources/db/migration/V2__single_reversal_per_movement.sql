-- A full accrual reversal is allowed once per original movement.
-- Both PostgreSQL and Oracle permit multiple NULLs in a unique index.
CREATE UNIQUE INDEX uq_ledger_original_movement
    ON mileage_ledger(original_movement_id);

-- Support the ordered outbox lookup for an earlier unpublished event of the same member.
CREATE INDEX idx_outbox_topic_member_status
    ON outbox_event(topic, message_key, status, created_at, id);
