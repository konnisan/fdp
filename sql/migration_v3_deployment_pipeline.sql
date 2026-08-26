USE fdp;

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
