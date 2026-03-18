CREATE TABLE IF NOT EXISTS repo_stats_snapshots (
    id          BIGSERIAL PRIMARY KEY,
    repo_id     UUID NOT NULL REFERENCES tracked_repos(id) ON DELETE CASCADE,
    snapshotted_on DATE NOT NULL,
    stars       INT NOT NULL DEFAULT 0,
    forks       INT NOT NULL DEFAULT 0,
    watchers    INT NOT NULL DEFAULT 0,
    UNIQUE (repo_id, snapshotted_on)
);

CREATE INDEX IF NOT EXISTS idx_rss_repo_date ON repo_stats_snapshots(repo_id, snapshotted_on DESC);
