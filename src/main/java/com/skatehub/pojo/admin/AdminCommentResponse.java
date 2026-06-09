package com.skatehub.pojo.admin;

import com.skatehub.pojo.Comment;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminCommentResponse {

    private Long commentId;
    private Long postId;
    private String postTitle;
    private Long userId;
    private String username;
    private String avatar;
    private Long parentId;
    private String parentContent;
    private String content;
    private LocalDateTime createTime;
    private String replyType;
    private Integer contentLength;

    public static AdminCommentResponse from(Comment comment, Post post, User user, Comment parent) {
        String content = comment.getContent() == null ? "" : comment.getContent();
        Long parentId = comment.getParentId() == null ? 0L : comment.getParentId();
        return AdminCommentResponse.builder()
                .commentId(comment.getCommentId())
                .postId(comment.getPostId())
                .postTitle(post == null ? "帖子已删除" : post.getTitle())
                .userId(comment.getUserId())
                .username(user == null ? "已注销用户" : user.getUsername())
                .avatar(user == null ? null : user.getAvatar())
                .parentId(parentId)
                .parentContent(parent == null ? "" : parent.getContent())
                .content(content)
                .createTime(comment.getCreateTime())
                .replyType(parentId == 0L ? "root" : "reply")
                .contentLength(content.length())
                .build();
    }
}
