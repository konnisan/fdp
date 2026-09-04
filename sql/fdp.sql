CREATE DATABASE IF NOT EXISTS fdp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fdp;

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

CREATE TABLE IF NOT EXISTS delivery_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_code VARCHAR(64) NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  git_url VARCHAR(512) NOT NULL,
  git_branch VARCHAR(128) NOT NULL DEFAULT 'develop',
  credential_id BIGINT NULL,
  project_type VARCHAR(32) NOT NULL COMMENT 'STATIC or CONTAINER execution mode',
  deployment_profile VARCHAR(32) NOT NULL DEFAULT 'LIGHTWEIGHT' COMMENT 'STATIC / LIGHTWEIGHT / CUSTOM',
  project_directory VARCHAR(512) NOT NULL DEFAULT '.',
  build_command VARCHAR(512) NULL COMMENT 'STATIC build only',
  build_output VARCHAR(255) NOT NULL DEFAULT 'dist',
  dockerfile_path VARCHAR(512) NOT NULL DEFAULT 'Dockerfile',
  docker_build_context VARCHAR(512) NOT NULL DEFAULT '.',
  image_name VARCHAR(255) NULL,
  container_name VARCHAR(128) NULL,
  host_port INT NULL,
  container_port INT NULL,
  cpu_limit VARCHAR(32) NULL DEFAULT '1',
  memory_limit VARCHAR(32) NULL DEFAULT '512m',
  host_data_path VARCHAR(512) NULL,
  container_data_path VARCHAR(512) NULL,
  health_check_path VARCHAR(255) NULL,
  preview_path VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  deployed_commit VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_delivery_project_code (project_code),
  UNIQUE KEY uk_delivery_project_preview_path (preview_path),
  UNIQUE KEY uk_delivery_project_host_port (host_port),
  KEY idx_delivery_project_credential (credential_id),
  KEY idx_delivery_project_profile (deployment_profile)
);

CREATE TABLE IF NOT EXISTS deployment_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  commit_id VARCHAR(64) NULL,
  image_tag VARCHAR(512) NULL,
  current_step VARCHAR(64) NULL,
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  KEY idx_deployment_task_project (project_id),
  KEY idx_deployment_task_status (status)
);

CREATE TABLE IF NOT EXISTS deployment_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  step_code VARCHAR(64) NOT NULL,
  step_name VARCHAR(128) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  error_message TEXT NULL,
  KEY idx_deployment_step_task (task_id),
  KEY idx_deployment_step_status (status)
);

CREATE TABLE IF NOT EXISTS deployment_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  content LONGTEXT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_deployment_log_task (task_id)
);

CREATE TABLE IF NOT EXISTS artifact_delivery_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_code VARCHAR(64) NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  pipeline_id VARCHAR(64) NOT NULL,
  pipeline_name VARCHAR(255) NULL,
  package_repo_id VARCHAR(128) NOT NULL,
  package_repo_name VARCHAR(255) NULL,
  artifact_name VARCHAR(255) NOT NULL,
  preview_path VARCHAR(255) NOT NULL,
  host_port INT NOT NULL,
  container_name VARCHAR(128) NOT NULL,
  env_file VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  current_version VARCHAR(128) NULL,
  current_run_id VARCHAR(64) NULL,
  current_image VARCHAR(512) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_artifact_delivery_project_code (project_code),
  UNIQUE KEY uk_artifact_delivery_preview_path (preview_path),
  UNIQUE KEY uk_artifact_delivery_host_port (host_port),
  UNIQUE KEY uk_artifact_delivery_container_name (container_name),
  KEY idx_artifact_delivery_pipeline (pipeline_id),
  KEY idx_artifact_delivery_repo (package_repo_id)
);

CREATE TABLE IF NOT EXISTS artifact_deployment_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  pipeline_run_id VARCHAR(64) NOT NULL,
  artifact_version VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  image_tag VARCHAR(512) NULL,
  message TEXT NULL,
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  KEY idx_artifact_deployment_project (project_id),
  KEY idx_artifact_deployment_status (status),
  KEY idx_artifact_deployment_run (pipeline_run_id)
);
