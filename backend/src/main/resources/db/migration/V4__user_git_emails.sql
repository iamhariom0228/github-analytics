-- Store all verified emails from GitHub account for commit author matching
ALTER TABLE users ADD COLUMN IF NOT EXISTS git_emails TEXT;
