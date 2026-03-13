-- V1: Users and preferences (Phase 1 foundation)

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    github_id BIGINT UNIQUE NOT NULL,
    username VARCHAR NOT NULL,
    email VARCHAR,
    avatar_url VARCHAR,
    access_token_encrypted TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    digest_enabled BOOLEAN DEFAULT true,
    digest_day_of_week INT DEFAULT 1,
    digest_hour INT DEFAULT 9,
    timezone VARCHAR DEFAULT 'UTC'
);

CREATE INDEX idx_users_github_id ON users(github_id);
