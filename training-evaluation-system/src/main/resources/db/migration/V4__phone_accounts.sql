ALTER TABLE app_user ADD COLUMN phone VARCHAR(11) NULL;
ALTER TABLE app_user ADD COLUMN display_name VARCHAR(80) NULL;
ALTER TABLE app_user ADD COLUMN student_id BIGINT NULL;
ALTER TABLE app_user ADD COLUMN teacher_id BIGINT NULL;
ALTER TABLE app_user ADD CONSTRAINT uk_user_phone UNIQUE(phone);
ALTER TABLE app_user ADD CONSTRAINT uk_user_student UNIQUE(student_id);
ALTER TABLE app_user ADD CONSTRAINT uk_user_teacher UNIQUE(teacher_id);
ALTER TABLE app_user ADD CONSTRAINT fk_user_student FOREIGN KEY(student_id) REFERENCES student(id);
ALTER TABLE app_user ADD CONSTRAINT fk_user_teacher FOREIGN KEY(teacher_id) REFERENCES teacher(id);
