package com.skatehub.pojo.admin;

import com.skatehub.pojo.Comment;
import com.skatehub.pojo.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminPostDetailResponse {

    private AdminPostResponse post;
    private List<String> imageList;
    private List<RecentComment> recentComments;

    @Data
    @Builder
    public static class RecentComment {
        private Long commentId;
        private Long userId;
        private String username;
        private String avatar;
        private String content;
        private LocalDateTime createTime;

        public static RecentComment from(Comment comment, User user) {
            return RecentComment.builder()
                    .commentId(comment.getCommentId())
                    .userId(comment.getUserId())
                    .username(user == null ? "已注销用户" : user.getUsername())
                    .avatar(user == null ? null : user.getAvatar())
                    .content(comment.getContent())
                    .createTime(comment.getCreateTime())
                    .build();
        }
    }
}
