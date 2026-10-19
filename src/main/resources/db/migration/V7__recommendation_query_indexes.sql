CREATE INDEX idx_post_recommendation_candidates
    ON tb_post (is_top, like_count, collect_count, create_time);

CREATE INDEX idx_activity_recommendation_candidates
    ON tb_activity (review_status, activity_status, create_time);

CREATE INDEX idx_comment_user_time_post
    ON tb_comment (user_id, create_time, post_id);

CREATE INDEX idx_interaction_user_target_type_time
    ON tb_interaction (user_id, target_type, type, create_time, target_id);

CREATE INDEX idx_activity_sign_user_time_activity
    ON tb_activity_sign (user_id, sign_time, activity_id);

CREATE INDEX idx_activity_user_time_city
    ON tb_activity (user_id, create_time, city);
