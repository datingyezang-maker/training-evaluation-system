-- 一次性补齐已有教师权限；以后管理员撤权不会在重启时被恢复。
INSERT INTO account_permission(user_id,permission_code)
SELECT u.id,p.code FROM app_user u CROSS JOIN (
 SELECT 'CLASS_MANAGE' code UNION ALL SELECT 'LESSON_MANAGE' UNION ALL SELECT 'TASK_MANAGE'
) p WHERE u.role='TEACHER' AND NOT EXISTS (
 SELECT 1 FROM account_permission a WHERE a.user_id=u.id AND a.permission_code=p.code
);
