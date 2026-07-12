ALTER TABLE tb_place
  ADD COLUMN submitted_by_user_id BIGINT NULL,
  ADD COLUMN verification_status VARCHAR(20) DEFAULT 'APPROVED',
  ADD COLUMN verification_note VARCHAR(200) NULL,
  ADD COLUMN verified_by_admin_id BIGINT NULL,
  ADD COLUMN verified_time DATETIME NULL;

UPDATE tb_place
SET verification_status = 'APPROVED'
WHERE verification_status IS NULL OR verification_status = '';
