ALTER TABLE evaluation_task ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE evaluation_task ADD COLUMN deleted_by VARCHAR(80) NULL;
-- 为历史重复任务保留全部数据；新发布通过授课唯一占位禁止并发重复。
CREATE TABLE task_publication_guard(lesson_id BIGINT PRIMARY KEY,task_id BIGINT NULL,FOREIGN KEY(lesson_id) REFERENCES lesson(id),FOREIGN KEY(task_id) REFERENCES evaluation_task(id));
INSERT INTO task_publication_guard(lesson_id,task_id) SELECT lesson_id,MAX(id) FROM evaluation_task WHERE deleted_at IS NULL GROUP BY lesson_id;
