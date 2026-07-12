ALTER TABLE tb_video
  ADD COLUMN creator_series VARCHAR(100) NULL,
  ADD COLUMN trick_tags VARCHAR(255) NULL,
  ADD COLUMN quality_score INT DEFAULT 0;

UPDATE tb_video
SET quality_score = 0
WHERE quality_score IS NULL;
