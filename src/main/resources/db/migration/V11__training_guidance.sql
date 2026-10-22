ALTER TABLE tb_user_training_plan MODIFY COLUMN plan_id BIGINT NULL;
ALTER TABLE tb_user_training_plan ADD COLUMN mode VARCHAR(12) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE tb_user_training_plan ADD CONSTRAINT ck_training_plan_mode CHECK
 ((mode = 'LEGACY' AND plan_id IS NOT NULL) OR (mode = 'GUIDED' AND plan_id IS NULL));
CREATE TABLE tb_guidance_catalog_lock (id INT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0);
INSERT INTO tb_guidance_catalog_lock(id) VALUES (1);
CREATE TABLE tb_guidance_template (
 template_id VARCHAR(64) NOT NULL, version BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL, safety_corrected BOOLEAN NOT NULL DEFAULT FALSE,
 document LONGTEXT NOT NULL, publication_reason VARCHAR(1000),
 PRIMARY KEY(template_id, version)
);
CREATE TABLE tb_guidance_plan (
 user_plan_id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, version BIGINT NOT NULL,
 template_id VARCHAR(64) NOT NULL, template_version BIGINT NOT NULL, document LONGTEXT NOT NULL,
 CONSTRAINT fk_guidance_plan FOREIGN KEY(user_plan_id) REFERENCES tb_user_training_plan(user_plan_id),
 INDEX idx_guidance_owner(user_id,user_plan_id)
);
CREATE TABLE tb_guidance_active (
 user_id BIGINT PRIMARY KEY, user_plan_id BIGINT NOT NULL UNIQUE,
 CONSTRAINT fk_guidance_active FOREIGN KEY(user_plan_id) REFERENCES tb_user_training_plan(user_plan_id)
);
CREATE TABLE tb_guidance_practice (
 record_id VARCHAR(36) PRIMARY KEY, user_plan_id BIGINT NOT NULL, instance_id VARCHAR(64) NOT NULL,
 practice_date DATE NOT NULL, actual_minutes INT NOT NULL, document LONGTEXT NOT NULL,
 CONSTRAINT fk_guidance_practice FOREIGN KEY(user_plan_id) REFERENCES tb_guidance_plan(user_plan_id),
 CONSTRAINT ck_guidance_minutes CHECK(actual_minutes >= 0 AND actual_minutes <= 240),
 INDEX idx_guidance_practice_plan(user_plan_id,practice_date)
);
CREATE TABLE tb_guidance_completion (
 completion_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_plan_id BIGINT NOT NULL, instance_id VARCHAR(64) NOT NULL,
 record_id VARCHAR(36) NOT NULL,
 CONSTRAINT uk_guidance_completion UNIQUE(user_plan_id,instance_id),
 CONSTRAINT fk_guidance_completion FOREIGN KEY(user_plan_id) REFERENCES tb_guidance_plan(user_plan_id)
);
CREATE TABLE tb_guidance_request (
 user_id BIGINT NOT NULL, operation VARBINARY(160) NOT NULL, request_key VARBINARY(100) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, result LONGTEXT NOT NULL,
 PRIMARY KEY(user_id,operation,request_key)
);
