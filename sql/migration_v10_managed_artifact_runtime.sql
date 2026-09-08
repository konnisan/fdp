USE fdp;

CREATE TABLE IF NOT EXISTS managed_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_name VARCHAR(128) NOT NULL,
  database_name VARCHAR(128) NOT NULL,
  runtime_image VARCHAR(512) NOT NULL,
  work_directory VARCHAR(512) NOT NULL DEFAULT '.',
  start_command TEXT NOT NULL,
  service_mode VARCHAR(32) NOT NULL DEFAULT 'DIRECT',
  service_port INT NOT NULL,
  nginx_static_directory VARCHAR(512) NULL,
  nginx_api_prefix VARCHAR(128) NULL DEFAULT '/api/',
  nginx_backend_port INT NULL,
  env_content_ciphertext LONGTEXT NULL,
  container_id VARCHAR(128) NULL,
  container_name VARCHAR(128) NOT NULL,
  deployed_runtime_image VARCHAR(512) NULL,
  deployed_version_summary TEXT NULL,
  running_version_summary TEXT NULL,
  deployment_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  last_error TEXT NULL,
  last_deploy_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_managed_project_name (project_name),
  UNIQUE KEY uk_managed_project_database (database_name),
  UNIQUE KEY uk_managed_project_container (container_name),
  KEY idx_managed_project_status (deployment_status)
);

CREATE TABLE IF NOT EXISTS managed_project_artifact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  repository_id VARCHAR(128) NOT NULL,
  repository_name VARCHAR(255) NULL,
  artifact_name VARCHAR(255) NOT NULL,
  target_directory VARCHAR(512) NOT NULL DEFAULT '.',
  sort_order INT NOT NULL DEFAULT 0,
  deployed_version VARCHAR(128) NULL,
  running_version VARCHAR(128) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_managed_artifact_project (project_id),
  KEY idx_managed_artifact_repo (repository_id),
  CONSTRAINT fk_managed_artifact_project FOREIGN KEY (project_id) REFERENCES managed_project(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS fdp_platform_setting (
  setting_key VARCHAR(128) PRIMARY KEY,
  setting_value TEXT NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
