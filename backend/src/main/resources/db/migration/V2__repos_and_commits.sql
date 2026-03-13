-- V2: Repos, commits, pull requests, reviews, sync jobs

CREATE TYPE sync_status AS ENUM ('PENDING', 'SYNCING', 'DONE', 'FAILED');
CREATE TYPE job_type AS ENUM ('FULL_SYNC', 'INCREMENTAL_SYNC', 'WEBHOOK');
CREATE TYPE job_status AS ENUM ('PENDING', 'RUNNING', 'DONE', 'FAILED');
CREATE TYPE pr_state AS ENUM ('OPEN', 'CLOSED', 'MERGED');
CREATE TYPE review_state AS ENUM ('APPROVED', 'CHANGES_REQUESTED', 'COMMENTED');

CREATE TABLE tracked_repos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    owner VARCHAR NOT NULL,
    name VARCHAR NOT NULL,
    full_name VARCHAR NOT NULL,
    github_repo_id BIGINT NOT NULL,
    is_private BOOLEAN DEFAULT false,
    last_synced_at TIMESTAMPTZ,
    sync_status sync_status DEFAULT 'PENDING',
    webhook_id BIGINT,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, github_repo_id)
);

CREATE TABLE commits (
    id BIGSERIAL PRIMARY KEY,
    repo_id UUID NOT NULL REFERENCES tracked_repos(id) ON DELETE CASCADE,
    sha VARCHAR(40) NOT NULL,
    author_login VARCHAR,
    author_github_id BIGINT,
    message_summary VARCHAR(255),
    additions INT DEFAULT 0,
    deletions INT DEFAULT 0,
    committed_at TIMESTAMPTZ NOT NULL,
    UNIQUE(repo_id, sha)
);

CREATE TABLE pull_requests (
    id BIGSERIAL PRIMARY KEY,
    repo_id UUID NOT NULL REFERENCES tracked_repos(id) ON DELETE CASCADE,
    pr_number INT NOT NULL,
    title VARCHAR(500),
    author_login VARCHAR,
    state pr_state DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL,
    first_review_at TIMESTAMPTZ,
    merged_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    additions INT DEFAULT 0,
    deletions INT DEFAULT 0,
    changed_files INT DEFAULT 0,
    UNIQUE(repo_id, pr_number)
);

CREATE TABLE pr_reviews (
    id BIGSERIAL PRIMARY KEY,
    pr_id BIGINT NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
    reviewer_login VARCHAR NOT NULL,
    state review_state NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE sync_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repo_id UUID REFERENCES tracked_repos(id) ON DELETE CASCADE,
    job_type job_type NOT NULL,
    status job_status DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    records_processed INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE digest_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id, week_start)
);

-- Indexes
CREATE INDEX idx_tracked_repos_user ON tracked_repos(user_id);
CREATE INDEX idx_commits_repo_date ON commits(repo_id, committed_at DESC);
CREATE INDEX idx_commits_author ON commits(author_login, committed_at DESC);
CREATE INDEX idx_prs_repo_state ON pull_requests(repo_id, state, created_at DESC);
CREATE INDEX idx_prs_author ON pull_requests(author_login, created_at DESC);
CREATE INDEX idx_reviews_pr ON pr_reviews(pr_id, submitted_at);
CREATE INDEX idx_reviews_reviewer ON pr_reviews(reviewer_login, submitted_at DESC);
CREATE INDEX idx_sync_jobs_user ON sync_jobs(user_id, created_at DESC);
CREATE INDEX idx_sync_jobs_repo ON sync_jobs(repo_id, created_at DESC);
