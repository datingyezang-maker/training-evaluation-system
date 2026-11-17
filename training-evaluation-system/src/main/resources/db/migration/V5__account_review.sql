-- 历史账号保持原启用状态；仅后续自注册进入显式审核流程。
ALTER TABLE app_user ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE app_user ADD COLUMN review_reason VARCHAR(200) NULL;
ALTER TABLE app_user ADD COLUMN reviewed_by VARCHAR(80) NULL;
ALTER TABLE app_user ADD COLUMN reviewed_at TIMESTAMP NULL;
