package com.skatehub.service;

import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.MessageRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.Interaction;
import com.skatehub.pojo.Message;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import com.skatehub.pojo.user.PasswordUpdateRequest;
import com.skatehub.pojo.user.UserProfileUpdateRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final PostRepository postRepository;
    private final ActivityRepository activityRepository;
    private final InteractionRepository interactionRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserGrowthService userGrowthService;

    public User profile(CurrentUser currentUser) {
        User user = userRepository.findById(currentUser.id()).orElseThrow(() -> new BizException("用户不存在"));
        userGrowthService.fillGrowthInfo(user);
        return user;
    }

    public User updateProfile(CurrentUser currentUser, UserProfileUpdateRequest request) {
        User user = profile(currentUser);

        String phone = request.getPhone() == null ? null : request.getPhone().trim();
        if (phone == null || phone.isBlank()) {
            user.setPhone(null);
        } else {
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                throw new BizException("手机号格式不正确，请输入 11 位手机号");
            }
            if (!phone.equals(user.getPhone())) {
                userRepository.findByPhone(phone).ifPresent(exist -> {
                    throw new BizException("手机号已被占用");
                });
            }
            user.setPhone(phone);
        }

        user.setAvatar(request.getAvatar());
        user.setGender(request.getGender());
        user.setSkateStyle(request.getSkateStyle());
        user.setBio(request.getBio());
        return userRepository.save(user);
    }

    public void updatePassword(CurrentUser currentUser, PasswordUpdateRequest request) {
        User user = profile(currentUser);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException("旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public List<Message> myMessages(CurrentUser currentUser) {
        return messageRepository.findByUserIdOrderByCreateTimeDesc(currentUser.id());
    }

    public List<Map<String, Object>> myNotifications(CurrentUser currentUser, String readStatus, String msgType) {
        String normalizedReadStatus = normalizeReadStatus(readStatus);
        String normalizedType = normalizeMsgType(msgType);

        List<Message> list;
        if (normalizedReadStatus == null && normalizedType == null) {
            list = messageRepository.findByUserIdOrderByCreateTimeDesc(currentUser.id());
        } else if (normalizedReadStatus != null && normalizedType == null) {
            list = messageRepository.findByUserIdAndIsReadOrderByCreateTimeDesc(currentUser.id(), normalizedReadStatus);
        } else if (normalizedReadStatus == null) {
            list = messageRepository.findByUserIdAndMsgTypeOrderByCreateTimeDesc(currentUser.id(), normalizedType);
        } else {
            list = messageRepository.findByUserIdAndIsReadAndMsgTypeOrderByCreateTimeDesc(currentUser.id(), normalizedReadStatus, normalizedType);
        }

        return list.stream().map(this::toNotificationVO).collect(Collectors.toList());
    }

    public void readMessage(CurrentUser currentUser, Long messageId) {
        Message message = messageRepository.findById(messageId).orElseThrow(() -> new BizException("消息不存在"));
        if (!message.getUserId().equals(currentUser.id())) {
            throw new BizException("无权操作该消息");
        }
        message.setIsRead("1");
        messageRepository.save(message);
    }

    public void readAllMessages(CurrentUser currentUser) {
        List<Message> messages = messageRepository.findByUserIdAndIsReadOrderByCreateTimeDesc(currentUser.id(), "0");
        if (messages.isEmpty()) return;
        messages.forEach(item -> item.setIsRead("1"));
        messageRepository.saveAll(messages);
    }

    public Map<String, Object> dashboard(CurrentUser currentUser) {
        Map<String, Object> map = new HashMap<>();
        map.put("postCount", postRepository.countByUserId(currentUser.id()));
        map.put("activityCount", activityRepository.countByUserId(currentUser.id()));
        map.put("unreadMsgCount", messageRepository.countByUserIdAndIsRead(currentUser.id(), "0"));
        User user = userRepository.findById(currentUser.id()).orElse(null);
        if (user != null) {
            userGrowthService.fillGrowthInfo(user);
            map.put("exp", user.getExp() == null ? 0 : user.getExp());
            map.put("level", user.getLevel());
            map.put("nextLevelNeedExp", user.getNextLevelNeedExp());
            map.put("remainToNextLevel", user.getRemainToNextLevel());
        } else {
            map.put("exp", 0);
            map.put("level", 1);
            map.put("nextLevelNeedExp", 100);
            map.put("remainToNextLevel", 100);
        }
        return map;
    }

    public Map<String, Object> myContent(CurrentUser currentUser) {
        Map<String, Object> result = new HashMap<>();
        result.put("myPosts", myPosts(currentUser.id()));
        result.put("myCollections", myCollections(currentUser.id()));
        result.put("myWatchLater", myWatchLater(currentUser.id()));
        result.put("myActivities", myActivities(currentUser.id()));
        return result;
    }

    private List<Map<String, Object>> myPosts(Long userId) {
        return postRepository.findByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(post -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("postId", post.getPostId());
                    map.put("title", post.getTitle());
                    map.put("category", post.getCategory());
                    map.put("createTime", post.getCreateTime());
                    map.put("likeCount", post.getLikeCount());
                    map.put("collectCount", post.getCollectCount());
                    map.put("commentCount", commentRepository.countByPostId(post.getPostId()));
                    return map;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> myCollections(Long userId) {
        List<Interaction> interactions = interactionRepository
                .findByUserIdAndTargetTypeAndTypeOrderByCreateTimeDesc(userId, "POST", "COLLECT");
        List<Long> postIds = interactions.stream().map(Interaction::getTargetId).distinct().toList();
        Map<Long, Post> postMap = postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getPostId, item -> item));

        return interactions.stream()
                .map(interaction -> {
                    Post post = postMap.get(interaction.getTargetId());
                    if (post == null) return null;
                    Map<String, Object> map = new HashMap<>();
                    map.put("postId", post.getPostId());
                    map.put("title", post.getTitle());
                    map.put("category", post.getCategory());
                    map.put("collectTime", interaction.getCreateTime());
                    map.put("authorId", post.getUserId());
                    map.put("likeCount", post.getLikeCount());
                    map.put("commentCount", commentRepository.countByPostId(post.getPostId()));
                    return map;
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> myActivities(Long userId) {
        return activityRepository.findByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(activity -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("activityId", activity.getActivityId());
                    map.put("title", activity.getTitle());
                    map.put("createTime", activity.getCreateTime());
                    map.put("activityTime", activity.getActivityTime());
                    map.put("activityStatus", activity.getActivityStatus());
                    map.put("status", activity.getStatus());
                    map.put("reviewStatus", activity.getReviewStatus());
                    map.put("signNum", activity.getSignNum());
                    map.put("maxNum", activity.getMaxNum());
                    map.put("city", activity.getCity());
                    map.put("district", activity.getDistrict());
                    map.put("place", activity.getPlace());
                    map.put("placeId", activity.getPlaceId());
                    return map;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> myWatchLater(Long userId) {
        List<Interaction> interactions = interactionRepository
                .findByUserIdAndTargetTypeAndTypeOrderByCreateTimeDesc(userId, "POST", "WATCH_LATER");
        List<Long> postIds = interactions.stream().map(Interaction::getTargetId).distinct().toList();
        Map<Long, Post> postMap = postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getPostId, item -> item));

        return interactions.stream()
                .map(interaction -> {
                    Post post = postMap.get(interaction.getTargetId());
                    if (post == null) return null;
                    Map<String, Object> map = new HashMap<>();
                    map.put("postId", post.getPostId());
                    map.put("title", post.getTitle());
                    map.put("category", post.getCategory());
                    map.put("watchLaterTime", interaction.getCreateTime());
                    map.put("authorId", post.getUserId());
                    map.put("likeCount", post.getLikeCount());
                    map.put("commentCount", commentRepository.countByPostId(post.getPostId()));
                    return map;
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toNotificationVO(Message message) {
        Map<String, Object> map = new HashMap<>();
        map.put("msgId", message.getMsgId());
        map.put("msgType", message.getMsgType());
        map.put("msgTypeLabel", msgTypeLabel(message.getMsgType()));
        map.put("content", message.getContent());
        map.put("isRead", message.getIsRead());
        map.put("createTime", message.getCreateTime());
        map.put("targetType", message.getTargetType());
        map.put("targetId", message.getTargetId());
        map.put("jumpPath", buildJumpPath(message));
        return map;
    }

    private String buildJumpPath(Message message) {
        String targetType = message.getTargetType();
        Long targetId = message.getTargetId();
        if ("POST".equals(targetType) && targetId != null) {
            return "/community/post/" + targetId;
        }
        if ("ACTIVITY".equals(targetType)) {
            return "/activities";
        }
        return null;
    }

    private String normalizeReadStatus(String readStatus) {
        if (readStatus == null || readStatus.isBlank() || "all".equalsIgnoreCase(readStatus)) {
            return null;
        }
        String value = readStatus.trim();
        if (!"0".equals(value) && !"1".equals(value)) {
            throw new BizException("消息状态筛选参数不合法");
        }
        return value;
    }

    private String normalizeMsgType(String msgType) {
        if (msgType == null || msgType.isBlank() || "ALL".equalsIgnoreCase(msgType)) {
            return null;
        }
        String value = msgType.trim().toUpperCase();
        if (!"COMMENT".equals(value) && !"LIKE".equals(value) && !"ACTIVITY".equals(value) && !"SYSTEM".equals(value)) {
            throw new BizException("消息类型筛选参数不合法");
        }
        return value;
    }

    private String msgTypeLabel(String msgType) {
        if ("COMMENT".equalsIgnoreCase(msgType)) return "评论";
        if ("LIKE".equalsIgnoreCase(msgType)) return "点赞";
        if ("ACTIVITY".equalsIgnoreCase(msgType)) return "活动";
        return "系统";
    }
}
