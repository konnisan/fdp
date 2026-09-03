USE fdp;

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
