package com.skatehub.pojo.admin;

import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Data
@Builder
public class AdminPostResponse {

    private Long postId;
    private String title;
    private String content;
    private String contentPreview;
    private String images;
    private Integer imageCount;
    private String category;
    private Long userId;
    private String username;
    private String avatar;
    private Integer likeCount;
    private Integer collectCount;
    private Long commentCount;
    private Long reportCount;
    private String isTop;
    private LocalDateTime createTime;

    public static AdminPostResponse from(Post post, User user, long commentCount, long reportCount) {
        String content = post.getContent() == null ? "" : post.getContent();
        return AdminPostResponse.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .content(content)
                .contentPreview(preview(content, 120))
                .images(post.getImages())
                .imageCount(splitImages(post.getImages()).size())
                .category(post.getCategory())
                .userId(post.getUserId())
                .username(user == null ? "已注销用户" : user.getUsername())
                .avatar(user == null ? null : user.getAvatar())
                .likeCount(post.getLikeCount() == null ? 0 : post.getLikeCount())
                .collectCount(post.getCollectCount() == null ? 0 : post.getCollectCount())
                .commentCount(commentCount)
                .reportCount(reportCount)
                .isTop("1".equals(post.getIsTop()) ? "1" : "0")
                .createTime(post.getCreateTime())
                .build();
    }

    public static List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        return Arrays.stream(images.split("[,\\n]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String preview(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
