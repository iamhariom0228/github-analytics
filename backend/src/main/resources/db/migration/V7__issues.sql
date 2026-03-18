CREATE TABLE IF NOT EXISTS issues (
    id              BIGSERIAL PRIMARY KEY,
    repo_id         UUID NOT NULL REFERENCES tracked_repos(id) ON DELETE CASCADE,
    issue_number    INT NOT NULL,
    title           VARCHAR(500),
    author_login    VARCHAR(100),
    state           VARCHAR(20) NOT NULL DEFAULT 'open',
    created_at      TIMESTAMPTZ NOT NULL,
    closed_at       TIMESTAMPTZ,
    UNIQUE (repo_id, issue_number)
);

CREATE INDEX IF NOT EXISTS idx_issues_repo_state ON issues(repo_id, state);
CREATE INDEX IF NOT EXISTS idx_issues_repo_created ON issues(repo_id, created_at DESC);
