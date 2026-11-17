-- 功能授权独立于人员身份；默认不向既有学员、教师开放后台。
CREATE TABLE account_permission (
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id,permission_code),
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
