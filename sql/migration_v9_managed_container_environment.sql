USE fdp;

ALTER TABLE artifact_delivery_project
  ADD COLUMN env_content_ciphertext LONGTEXT NULL AFTER env_file;
