-- Baseline schema placeholder.
-- TODO: paste/translate the full Oracle DDL into Flyway migrations.

CREATE TABLE gg_schema_version (
  id NUMBER(1) PRIMARY KEY,
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);

INSERT INTO gg_schema_version (id) VALUES (1);
