USE fdp;

-- V5: reusable Codeup HTTPS credentials.
-- A credential belongs to a Codeup account and can be reused by multiple delivery projects.
-- Existing projects remain credential_id=NULL so SSH/server-side credential-helper deployments keep working.

CREATE TABLE IF NOT EXISTS source_credential (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  provider VARCHAR(32) NOT NULL DEFAULT 'CODEUP',
  clone_username VARCHAR(128) NOT NULL,
  secret_encrypted TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'UNTESTED',
  last_test_message VARCHAR(512) NULL,
  last_test_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_credential_name (name)
);

ALTER TABLE delivery_project
  ADD COLUMN credential_id BIGINT NULL AFTER git_branch,
  ADD KEY idx_delivery_project_credential (credential_id);
