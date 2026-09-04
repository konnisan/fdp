ALTER TABLE delivery_project
  ADD COLUMN deployment_profile VARCHAR(32) NULL COMMENT 'STATIC / LIGHTWEIGHT / CUSTOM' AFTER project_type;

UPDATE delivery_project
SET deployment_profile = CASE
  WHEN project_type = 'STATIC' THEN 'STATIC'
  ELSE 'LIGHTWEIGHT'
END
WHERE deployment_profile IS NULL OR deployment_profile = '';

ALTER TABLE delivery_project
  MODIFY COLUMN deployment_profile VARCHAR(32) NOT NULL DEFAULT 'LIGHTWEIGHT' COMMENT 'STATIC / LIGHTWEIGHT / CUSTOM',
  ADD KEY idx_delivery_project_profile (deployment_profile);
