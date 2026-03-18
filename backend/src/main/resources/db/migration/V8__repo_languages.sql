CREATE TABLE IF NOT EXISTS repo_languages (
    id          BIGSERIAL PRIMARY KEY,
    repo_id     UUID NOT NULL REFERENCES tracked_repos(id) ON DELETE CASCADE,
    language    VARCHAR(100) NOT NULL,
    bytes       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (repo_id, language)
);

CREATE INDEX IF NOT EXISTS idx_repo_languages_repo ON repo_languages(repo_id);
