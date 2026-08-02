-- Day 2: core domain schema (users, websites, scans, findings).
-- The "report" table from the full ERD is deferred to its own migration
-- when report generation is built (Day 13), it's out of scope here.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- 2048 rather than the default 255, real-world URLs with long query
-- strings can exceed a short varchar.
CREATE TABLE websites (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT NOT NULL REFERENCES users(id),
    url      VARCHAR(2048) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_websites_user_id ON websites(user_id);

CREATE TABLE scans (
    id            BIGSERIAL PRIMARY KEY,
    website_id    BIGINT NOT NULL REFERENCES websites(id),
    status        VARCHAR(50) NOT NULL,
    overall_score INTEGER,
    risk_rating   VARCHAR(50),
    started_at    TIMESTAMP NOT NULL DEFAULT now(),
    completed_at  TIMESTAMP
);

CREATE INDEX idx_scans_website_id ON scans(website_id);

CREATE TABLE findings (
    id             BIGSERIAL PRIMARY KEY,
    scan_id        BIGINT NOT NULL REFERENCES scans(id),
    check_name     VARCHAR(255) NOT NULL,
    severity       VARCHAR(50) NOT NULL,
    description    TEXT NOT NULL,
    recommendation TEXT NOT NULL
);

CREATE INDEX idx_findings_scan_id ON findings(scan_id);
