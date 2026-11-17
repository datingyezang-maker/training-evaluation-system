-- 跨 H2(MySQL 模式) / MySQL 的基础迁移；后续变更新增版本，禁止改写已执行脚本。
CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    path VARCHAR(512) NOT NULL,
    http_status INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_created_at ON audit_log(created_at);
