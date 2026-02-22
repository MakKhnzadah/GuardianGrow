-- Core schema based on the GuardianGrow MVP spec.
-- Oracle notes:
-- - UUIDs stored as RAW(16)
-- - Events/sessions use TIMESTAMP WITH TIME ZONE

-- ===== Core identity =====
CREATE TABLE households (
  id            RAW(16) PRIMARY KEY,
  name          VARCHAR2(200),
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE users (
  id            RAW(16) PRIMARY KEY,
  household_id  RAW(16) REFERENCES households(id),
  email         VARCHAR2(320) NOT NULL UNIQUE,
  password_hash VARCHAR2(255) NOT NULL,
  display_name  VARCHAR2(200) NOT NULL,
  role          VARCHAR2(20) NOT NULL CHECK (role IN ('PARENT','MODERATOR','ADMIN')),
  status        VARCHAR2(20) NOT NULL CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_login_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE refresh_tokens (
  id            RAW(16) PRIMARY KEY,
  user_id       RAW(16) NOT NULL REFERENCES users(id),
  token_hash    VARCHAR2(255) NOT NULL,
  issued_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at    TIMESTAMP WITH TIME ZONE,
  user_agent    VARCHAR2(400),
  ip_addr       VARCHAR2(64)
);

-- ===== Child profiles =====
CREATE TABLE child_profiles (
  id            RAW(16) PRIMARY KEY,
  household_id  RAW(16) NOT NULL REFERENCES households(id),
  display_name  VARCHAR2(200) NOT NULL,
  birth_date    DATE NOT NULL,
  avatar_key    VARCHAR2(50),
  status        VARCHAR2(20) NOT NULL CHECK (status IN ('ACTIVE','ARCHIVED')),
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- ===== Content & versioning =====
CREATE TABLE content_items (
  id              RAW(16) PRIMARY KEY,
  household_id    RAW(16),
  type            VARCHAR2(20) NOT NULL CHECK (type IN ('LESSON','STORY','PUZZLE')),
  title           VARCHAR2(300) NOT NULL,
  topic           VARCHAR2(100),
  min_age         NUMBER(2) NOT NULL,
  max_age         NUMBER(2) NOT NULL,
  difficulty      NUMBER(1) CHECK (difficulty BETWEEN 1 AND 5),
  est_minutes     NUMBER(3),
  status          VARCHAR2(20) NOT NULL CHECK (status IN ('DRAFT','IN_REVIEW','PUBLISHED','ARCHIVED')),
  published_version_id RAW(16),
  created_by      RAW(16) REFERENCES users(id),
  created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE content_versions (
  id            RAW(16) PRIMARY KEY,
  content_id    RAW(16) NOT NULL REFERENCES content_items(id),
  version_no    NUMBER(6) NOT NULL,
  body_json     CLOB NOT NULL,
  change_note   VARCHAR2(500),
  status        VARCHAR2(20) NOT NULL CHECK (status IN ('DRAFT','IN_REVIEW','PUBLISHED','REJECTED','ARCHIVED')),
  created_by    RAW(16) REFERENCES users(id),
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_content_version UNIQUE (content_id, version_no)
);

-- ===== Moderation =====
CREATE TABLE moderation_queue (
  id            RAW(16) PRIMARY KEY,
  content_id    RAW(16) NOT NULL REFERENCES content_items(id),
  version_id    RAW(16) NOT NULL REFERENCES content_versions(id),
  status        VARCHAR2(20) NOT NULL CHECK (status IN ('PENDING','CHANGES_REQUESTED','APPROVED','REJECTED')),
  submitted_by  RAW(16) REFERENCES users(id),
  submitted_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  reviewed_by   RAW(16) REFERENCES users(id),
  reviewed_at   TIMESTAMP WITH TIME ZONE,
  review_notes  VARCHAR2(2000)
);

CREATE TABLE content_reports (
  id            RAW(16) PRIMARY KEY,
  household_id  RAW(16) REFERENCES households(id),
  content_id    RAW(16) NOT NULL REFERENCES content_items(id),
  reporter_user_id RAW(16) REFERENCES users(id),
  reason        VARCHAR2(30) NOT NULL CHECK (reason IN ('INAPPROPRIATE','INCORRECT','OTHER')),
  details       VARCHAR2(2000),
  status        VARCHAR2(20) NOT NULL CHECK (status IN ('OPEN','RESOLVED','DISMISSED')),
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  resolved_at   TIMESTAMP WITH TIME ZONE,
  resolved_by   RAW(16) REFERENCES users(id)
);

-- ===== Learning plans =====
CREATE TABLE learning_plans (
  id                 RAW(16) PRIMARY KEY,
  household_id       RAW(16) NOT NULL REFERENCES households(id),
  child_id           RAW(16) NOT NULL REFERENCES child_profiles(id),
  name               VARCHAR2(200) NOT NULL,
  week_start         DATE NOT NULL,
  daily_time_limit_min NUMBER(3) NOT NULL,
  break_after_min    NUMBER(3) NOT NULL,
  break_duration_min NUMBER(3) NOT NULL,
  status             VARCHAR2(20) NOT NULL CHECK (status IN ('ACTIVE','ARCHIVED')),
  created_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE plan_items (
  id            RAW(16) PRIMARY KEY,
  plan_id       RAW(16) NOT NULL REFERENCES learning_plans(id),
  content_id    RAW(16) NOT NULL REFERENCES content_items(id),
  day_of_week   VARCHAR2(3) NOT NULL CHECK (day_of_week IN ('MON','TUE','WED','THU','FRI','SAT','SUN')),
  target_minutes NUMBER(3) NOT NULL,
  order_index   NUMBER(4) NOT NULL,
  allowed_start VARCHAR2(5),
  allowed_end   VARCHAR2(5),
  CONSTRAINT uq_plan_day_order UNIQUE (plan_id, day_of_week, order_index)
);

-- ===== Sessions & progress =====
CREATE TABLE sessions (
  id            RAW(16) PRIMARY KEY,
  household_id  RAW(16) NOT NULL REFERENCES households(id),
  child_id      RAW(16) NOT NULL REFERENCES child_profiles(id),
  plan_item_id  RAW(16) REFERENCES plan_items(id),
  content_id    RAW(16) NOT NULL REFERENCES content_items(id),
  started_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  ended_at      TIMESTAMP WITH TIME ZONE,
  state         VARCHAR2(20) NOT NULL CHECK (state IN ('ACTIVE','FORCED_BREAK','ENDED')),
  end_reason    VARCHAR2(30),
  total_active_seconds NUMBER(10) DEFAULT 0 NOT NULL,
  total_break_seconds  NUMBER(10) DEFAULT 0 NOT NULL
);

CREATE TABLE session_events (
  id            RAW(16) PRIMARY KEY,
  session_id    RAW(16) NOT NULL REFERENCES sessions(id),
  event_time    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  type          VARCHAR2(30) NOT NULL,
  meta_json     CLOB
);

CREATE TABLE progress_records (
  id            RAW(16) PRIMARY KEY,
  household_id  RAW(16) NOT NULL REFERENCES households(id),
  child_id      RAW(16) NOT NULL REFERENCES child_profiles(id),
  content_id    RAW(16) NOT NULL REFERENCES content_items(id),
  session_id    RAW(16) REFERENCES sessions(id),
  completed_at  TIMESTAMP WITH TIME ZONE,
  minutes_spent NUMBER(6,2),
  quiz_score    NUMBER(5,2),
  details_json  CLOB
);

-- ===== Share links =====
CREATE TABLE share_links (
  id            RAW(16) PRIMARY KEY,
  household_id  RAW(16) NOT NULL REFERENCES households(id),
  child_id      RAW(16) NOT NULL REFERENCES child_profiles(id),
  scope         VARCHAR2(30) NOT NULL CHECK (scope IN ('WEEKLY_REPORT')),
  week_start    DATE,
  token_hash    VARCHAR2(255) NOT NULL,
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  expires_at    TIMESTAMP WITH TIME ZONE,
  revoked_at    TIMESTAMP WITH TIME ZONE
);

-- ===== App-level audit log =====
CREATE TABLE audit_logs (
  id            RAW(16) PRIMARY KEY,
  household_id  RAW(16),
  actor_user_id RAW(16),
  action        VARCHAR2(100) NOT NULL,
  entity_type   VARCHAR2(50),
  entity_id     RAW(16),
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  ip_addr       VARCHAR2(64),
  user_agent    VARCHAR2(400),
  diff_json     CLOB
);

-- ===== Helpful indexes =====
CREATE INDEX ix_child_household ON child_profiles(household_id);
CREATE INDEX ix_sessions_child_time ON sessions(child_id, started_at);
CREATE INDEX ix_progress_child_time ON progress_records(child_id, completed_at);
CREATE INDEX ix_content_status_age ON content_items(status, min_age, max_age);
