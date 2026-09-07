USE fdp;

ALTER TABLE artifact_delivery_project
  ADD COLUMN container_port INT NULL AFTER host_port,
  ADD COLUMN cpu_limit VARCHAR(32) NULL AFTER container_name,
  ADD COLUMN memory_limit VARCHAR(32) NULL AFTER cpu_limit,
  ADD COLUMN host_data_path VARCHAR(512) NULL AFTER memory_limit,
  ADD COLUMN container_data_path VARCHAR(512) NULL AFTER host_data_path,
  ADD COLUMN health_check_path VARCHAR(255) NULL AFTER container_data_path;
