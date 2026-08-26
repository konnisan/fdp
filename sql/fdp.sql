CREATE DATABASE IF NOT EXISTS fdp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fdp;

CREATE TABLE IF NOT EXISTS delivery_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_code VARCHAR(64) NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  git_url VARCHAR(512) NOT NULL,
  git_branch VARCHAR(128) NOT NULL DEFAULT 'develop',
  project_type VARCHAR(32) NOT NULL COMMENT 'STATIC or NODE_SQLITE',
  build_command VARCHAR(512) NULL,
  start_command VARCHAR(512) NULL,
  build_output VARCHAR(255) NOT NULL DEFAULT 'dist',
  internal_port INT NULL,
  preview_path VARCHAR(255) NOT NULL,
  pm2_name VARCHAR(128) NULL,
  sqlite_path VARCHAR(255) NULL DEFAULT 'app.db',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  deployed_commit VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_delivery_project_code (project_code),
  UNIQUE KEY uk_delivery_project_preview_path (preview_path),
  UNIQUE KEY uk_delivery_project_internal_port (internal_port)
);

CREATE TABLE IF NOT EXISTS deployment_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  commit_id VARCHAR(64) NULL,
  current_step VARCHAR(64) NULL,
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  KEY idx_deployment_task_project (project_id),
  KEY idx_deployment_task_status (status)
);

CREATE TABLE IF NOT EXISTS deployment_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  content LONGTEXT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_deployment_log_task (task_id)
);
