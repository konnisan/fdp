CREATE DATABASE IF NOT EXISTS fdp DEFAULT CHARACTER SET utf8mb4;
USE fdp;

CREATE TABLE delivery_project(
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 project_name VARCHAR(128),
 git_url VARCHAR(512),
 git_branch VARCHAR(64),
 project_type VARCHAR(32),
 workspace VARCHAR(255),
 build_command VARCHAR(512),
 deploy_command VARCHAR(512),
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE deployment_task(
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 project_id BIGINT,
 status VARCHAR(32),
 start_time DATETIME,
 end_time DATETIME
);

CREATE TABLE deployment_log(
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 task_id BIGINT,
 content LONGTEXT,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);