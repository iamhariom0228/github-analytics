ALTER TABLE tracked_repos
  ADD COLUMN IF NOT EXISTS stars INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS forks INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS watchers INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS open_issues_count INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS language VARCHAR(100),
  ADD COLUMN IF NOT EXISTS description TEXT;

CREATE TABLE IF NOT EXISTS releases (
    id BIGSERIAL PRIMARY KEY,
    repo_id UUID NOT NULL REFERENCES tracked_repos(id) ON DELETE CASCADE,
    tag_name VARCHAR(200) NOT NULL,
    name VARCHAR(500),
    published_at TIMESTAMPTZ,
    UNIQUE(repo_id, tag_name)
);

CREATE INDEX IF NOT EXISTS idx_releases_repo ON releases(repo_id, published_at DESC);
