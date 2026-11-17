-- 独立于数据库主键，删除学员后也不回收学号。
CREATE TABLE student_number_sequence (
    sequence_id INT PRIMARY KEY,
    next_value BIGINT NOT NULL
);
INSERT INTO student_number_sequence(sequence_id,next_value) VALUES(1,1);
