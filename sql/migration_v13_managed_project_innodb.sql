USE fdp;

-- Managed-project atomic operations rely on transactional table semantics.
ALTER TABLE managed_project ENGINE=InnoDB;
ALTER TABLE managed_project_artifact ENGINE=InnoDB;
ALTER TABLE fdp_platform_setting ENGINE=InnoDB;
