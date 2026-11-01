CREATE TABLE demo_user (
  id VARCHAR(36) PRIMARY KEY,
  display_name VARCHAR(80) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_session (
  id VARCHAR(36) PRIMARY KEY,
  token_hash CHAR(64) NOT NULL UNIQUE,
  user_id VARCHAR(36) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (user_id) REFERENCES demo_user(id),
  INDEX idx_session_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE template_category (
  id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  sort_order INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE template (
  id VARCHAR(64) PRIMARY KEY,
  category_id VARCHAR(40) NOT NULL,
  title VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  cover_path VARCHAR(255) NOT NULL,
  sample_path VARCHAR(255) NOT NULL,
  result_source_path VARCHAR(255) NOT NULL,
  required_image_count INT NOT NULL,
  accepted_mime_types JSON NOT NULL,
  max_file_size_bytes BIGINT NOT NULL,
  sort_order INT NOT NULL,
  FOREIGN KEY (category_id) REFERENCES template_category(id),
  CONSTRAINT ck_template_image_count CHECK (required_image_count BETWEEN 1 AND 3),
  CONSTRAINT ck_template_size CHECK (max_file_size_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE asset (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  template_id VARCHAR(64) NOT NULL,
  storage_path VARCHAR(512) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(80) NOT NULL,
  size_bytes BIGINT NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (user_id) REFERENCES demo_user(id),
  FOREIGN KEY (template_id) REFERENCES template(id),
  INDEX idx_asset_owner (user_id, id),
  CHECK (size_bytes > 0 AND width > 0 AND height > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE creation_task (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  template_id VARCHAR(64) NOT NULL,
  template_title_snapshot VARCHAR(120) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
  current_stage VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  failure_stage VARCHAR(20),
  error_code VARCHAR(64),
  error_message VARCHAR(1000),
  result_path VARCHAR(512),
  simulation_json JSON NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  started_at DATETIME(6),
  finished_at DATETIME(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (user_id) REFERENCES demo_user(id),
  FOREIGN KEY (template_id) REFERENCES template(id),
  INDEX idx_task_history (user_id, created_at DESC, id DESC),
  INDEX idx_task_queue (status, created_at, id),
  CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
  CHECK (current_stage IN ('PENDING','ASSET_CHECK','GENERATE','FINALIZE')),
  CHECK (failure_stage IS NULL OR failure_stage IN ('PENDING','ASSET_CHECK','GENERATE','FINALIZE')),
  CHECK (version >= 0),
  CHECK (status <> 'SUCCEEDED' OR (result_path IS NOT NULL AND finished_at IS NOT NULL)),
  CHECK (status <> 'FAILED' OR (error_code IS NOT NULL AND failure_stage IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_asset (
  task_id VARCHAR(36) NOT NULL,
  asset_id VARCHAR(36) NOT NULL,
  slot_index INT NOT NULL,
  PRIMARY KEY (task_id, slot_index),
  UNIQUE KEY uq_task_asset (task_id, asset_id),
  FOREIGN KEY (task_id) REFERENCES creation_task(id),
  FOREIGN KEY (asset_id) REFERENCES asset(id),
  CHECK (slot_index >= 0 AND slot_index <= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_step (
  id VARCHAR(36) PRIMARY KEY,
  task_id VARCHAR(36) NOT NULL,
  stage VARCHAR(20) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  started_at DATETIME(6),
  finished_at DATETIME(6),
  error_code VARCHAR(64),
  error_message VARCHAR(1000),
  UNIQUE KEY uq_task_step (task_id, stage),
  FOREIGN KEY (task_id) REFERENCES creation_task(id),
  CHECK (stage IN ('ASSET_CHECK','GENERATE','FINALIZE')),
  CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','SKIPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
