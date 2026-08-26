USE fdp;

-- V4: move from NODE_SQLITE/PM2 to generic Docker container delivery.
-- Run this after the V3 deployment pipeline migration.
-- Legacy PM2/SQLite columns are intentionally kept for rollback/history compatibility.

ALTER TABLE delivery_project
  ADD COLUMN project_directory VARCHAR(512) NOT NULL DEFAULT '.' AFTER project_type,
  ADD COLUMN dockerfile_path VARCHAR(512) NOT NULL DEFAULT 'Dockerfile' AFTER build_output,
  ADD COLUMN docker_build_context VARCHAR(512) NOT NULL DEFAULT '.' AFTER dockerfile_path,
  ADD COLUMN image_name VARCHAR(255) NULL AFTER docker_build_context,
  ADD COLUMN container_name VARCHAR(128) NULL AFTER image_name,
  ADD COLUMN host_port INT NULL AFTER container_name,
  ADD COLUMN container_port INT NULL AFTER host_port,
  ADD COLUMN cpu_limit VARCHAR(32) NULL DEFAULT '1' AFTER container_port,
  ADD COLUMN memory_limit VARCHAR(32) NULL DEFAULT '512m' AFTER cpu_limit,
  ADD COLUMN host_data_path VARCHAR(512) NULL AFTER memory_limit,
  ADD COLUMN container_data_path VARCHAR(512) NULL AFTER host_data_path,
  ADD COLUMN health_check_path VARCHAR(255) NULL AFTER container_data_path;

ALTER TABLE deployment_task
  ADD COLUMN image_tag VARCHAR(512) NULL AFTER commit_id;

UPDATE delivery_project
SET image_name = COALESCE(NULLIF(image_name,''), CONCAT('fdp/', project_code)),
    container_name = COALESCE(NULLIF(container_name,''), NULLIF(pm2_name,''), CONCAT('fdp-', project_code)),
    host_port = COALESCE(host_port, internal_port),
    container_port = COALESCE(container_port, 3000),
    cpu_limit = COALESCE(NULLIF(cpu_limit,''), '1'),
    memory_limit = COALESCE(NULLIF(memory_limit,''), '512m'),
    host_data_path = CASE WHEN host_data_path IS NULL OR host_data_path='' THEN CONCAT('/data/fdp/data/', project_code) ELSE host_data_path END,
    container_data_path = CASE WHEN container_data_path IS NULL OR container_data_path='' THEN '/app/data' ELSE container_data_path END,
    project_type = 'CONTAINER'
WHERE project_type = 'NODE_SQLITE';

ALTER TABLE delivery_project
  DROP INDEX uk_delivery_project_internal_port,
  ADD UNIQUE KEY uk_delivery_project_host_port (host_port);
