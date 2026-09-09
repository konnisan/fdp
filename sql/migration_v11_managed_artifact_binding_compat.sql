USE fdp;

DROP PROCEDURE IF EXISTS fdp_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE fdp_add_column_if_missing(
    IN p_table VARCHAR(128),
    IN p_column VARCHAR(128),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = p_table
           AND COLUMN_NAME = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL fdp_add_column_if_missing('managed_project_artifact', 'repository_name', 'VARCHAR(255) NULL');
CALL fdp_add_column_if_missing('managed_project_artifact', 'target_directory', 'VARCHAR(512) NOT NULL DEFAULT ''.''');
CALL fdp_add_column_if_missing('managed_project_artifact', 'sort_order', 'INT NOT NULL DEFAULT 0');
CALL fdp_add_column_if_missing('managed_project_artifact', 'deployed_version', 'VARCHAR(128) NULL');
CALL fdp_add_column_if_missing('managed_project_artifact', 'running_version', 'VARCHAR(128) NULL');
CALL fdp_add_column_if_missing('managed_project', 'deployed_version_summary', 'TEXT NULL');
CALL fdp_add_column_if_missing('managed_project', 'running_version_summary', 'TEXT NULL');
CALL fdp_add_column_if_missing('managed_project', 'deployed_runtime_image', 'VARCHAR(512) NULL');
CALL fdp_add_column_if_missing('managed_project', 'last_deploy_time', 'DATETIME NULL');
CALL fdp_add_column_if_missing('managed_project', 'last_error', 'TEXT NULL');

DROP PROCEDURE IF EXISTS fdp_add_column_if_missing;
