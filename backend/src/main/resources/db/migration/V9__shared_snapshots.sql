CREATE TABLE IF NOT EXISTS shared_snapshots (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(64) NOT NULL UNIQUE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payload     JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '30 days'
);

CREATE INDEX IF NOT EXISTS idx_shared_snapshots_token ON shared_snapshots(token);
