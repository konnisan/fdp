USE fdp;

ALTER TABLE delivery_project
  ADD COLUMN project_code VARCHAR(64) NULL AFTER id,
  ADD COLUMN start_command VARCHAR(512) NULL AFTER build_command,
  ADD COLUMN build_output VARCHAR(255) NOT NULL DEFAULT 'dist' AFTER start_command,
  ADD COLUMN internal_port INT NULL AFTER build_output,
  ADD COLUMN preview_path VARCHAR(255) NULL AFTER internal_port,
  ADD COLUMN pm2_name VARCHAR(128) NULL AFTER preview_path,
  ADD COLUMN sqlite_path VARCHAR(255) NULL DEFAULT 'app.db' AFTER pm2_name,
  ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' AFTER sqlite_path,
  ADD COLUMN deployed_commit VARCHAR(64) NULL AFTER status,
  ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER create_time;

ALTER TABLE deployment_task
  ADD COLUMN commit_id VARCHAR(64) NULL AFTER status,
  ADD COLUMN current_step VARCHAR(64) NULL AFTER commit_id;

ALTER TABLE delivery_project
  ADD UNIQUE KEY uk_delivery_project_code (project_code),
  ADD UNIQUE KEY uk_delivery_project_preview_path (preview_path),
  ADD UNIQUE KEY uk_delivery_project_internal_port (internal_port);

ALTER TABLE deployment_task
  ADD KEY idx_deployment_task_project (project_id),
  ADD KEY idx_deployment_task_status (status);

ALTER TABLE deployment_log
  ADD KEY idx_deployment_log_task (task_id);
