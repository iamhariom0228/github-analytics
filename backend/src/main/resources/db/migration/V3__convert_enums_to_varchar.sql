-- Convert PostgreSQL native ENUM columns to VARCHAR for Hibernate @Enumerated(EnumType.STRING) compatibility.

ALTER TABLE tracked_repos  ALTER COLUMN sync_status TYPE VARCHAR(20)  USING sync_status::text;
ALTER TABLE sync_jobs      ALTER COLUMN job_type    TYPE VARCHAR(30)  USING job_type::text;
ALTER TABLE sync_jobs      ALTER COLUMN status      TYPE VARCHAR(20)  USING status::text;
ALTER TABLE pull_requests  ALTER COLUMN state       TYPE VARCHAR(20)  USING state::text;
ALTER TABLE pr_reviews     ALTER COLUMN state       TYPE VARCHAR(30)  USING state::text;

DROP TYPE IF EXISTS sync_status  CASCADE;
DROP TYPE IF EXISTS job_type     CASCADE;
DROP TYPE IF EXISTS job_status   CASCADE;
DROP TYPE IF EXISTS pr_state     CASCADE;
DROP TYPE IF EXISTS review_state CASCADE;
