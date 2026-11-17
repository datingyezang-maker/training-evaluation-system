-- 既有未撤销任务全部收齐答卷时补齐完成状态；零人任务不视为完成。
UPDATE evaluation_task SET status='COMPLETED'
WHERE deleted_at IS NULL AND status IN('OPEN','CLOSED')
AND EXISTS(SELECT 1 FROM eligibility e WHERE e.task_id=evaluation_task.id)
AND NOT EXISTS(SELECT 1 FROM eligibility e LEFT JOIN answer a ON a.eligibility_id=e.id WHERE e.task_id=evaluation_task.id AND a.id IS NULL);
