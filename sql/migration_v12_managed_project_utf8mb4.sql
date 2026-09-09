USE fdp;

-- Existing FDP databases may have been created before utf8mb4 became the default.
-- CREATE DATABASE IF NOT EXISTS does not change the charset of an existing database,
-- so explicitly convert the managed-project tables used by Packages metadata.
ALTER DATABASE `fdp` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `managed_project`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `managed_project_artifact`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `fdp_platform_setting`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
