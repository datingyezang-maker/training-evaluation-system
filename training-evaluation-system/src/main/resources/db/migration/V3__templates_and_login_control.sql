CREATE TABLE evaluation_template(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(120) NOT NULL,
 teaching_label VARCHAR(80) NOT NULL,
 content_label VARCHAR(80) NOT NULL,
 organization_label VARCHAR(80) NOT NULL,
 teaching_weight INT NOT NULL,
 content_weight INT NOT NULL,
 organization_weight INT NOT NULL
);
INSERT INTO evaluation_template(id,name,teaching_label,content_label,organization_label,teaching_weight,content_weight,organization_weight)
VALUES(1,'标准课程评价 v1','教学质量','课程内容','组织安排',1,1,1);
ALTER TABLE evaluation_task ADD COLUMN template_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE evaluation_task ADD CONSTRAINT fk_task_template FOREIGN KEY(template_id) REFERENCES evaluation_template(id);
ALTER TABLE answer ADD COLUMN paper_reference VARCHAR(80);
ALTER TABLE app_user ADD COLUMN login_failures INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN locked_until TIMESTAMP NULL;
