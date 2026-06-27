package com.skatehub.service;

import com.skatehub.dao.UserFollowRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.User;
import com.skatehub.pojo.UserFollow;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final String ACTIVE = "1";
    private static final String CANCELED = "0";

    private final UserFollowRepository followRepository;
    private final UserRepository userRepository;
    private final UserGrowthService userGrowthService;
    private final MessageNotifyService messageNotifyService;

    @Transactional
    public Map<String, Object> follow(CurrentUser currentUser, Long targetUserId) {
        Long currentUserId = requireUserId(currentUser);
        User target = requireTarget(currentUserId, targetUserId);
        UserFollow existing = followRepository.findByFollowerIdAndFolloweeId(currentUserId, targetUserId).orElse(null);
        if (existing == null) {
            UserFollow follow = new UserFollow();
            follow.setFollowerId(currentUserId);
            follow.setFolloweeId(targetUserId);
            follow.setStatus(ACTIVE);
            followRepository.save(follow);
            userGrowthService.grantExp(currentUserId, "USER_FOLLOW", "USER", targetUserId, 2);
            messageNotifyService.send(targetUserId, "FOLLOW", displayName(currentUser) + " 关注了你", "USER", currentUserId);
        } else if (!ACTIVE.equals(existing.getStatus())) {
            existing.setStatus(ACTIVE);
            followRepository.save(existing);
            userGrowthService.grantExp(currentUserId, "USER_FOLLOW", "USER", targetUserId, 2);
            messageNotifyService.send(targetUserId, "FOLLOW", displayName(currentUser) + " 关注了你", "USER", currentUserId);
        }
        Map<String, Object> result = status(currentUser, target.getUserId());
        result.put("following", true);
        return result;
    }

    @Transactional
    public Map<String, Object> unfollow(CurrentUser currentUser, Long targetUserId) {
        Long currentUserId = requireUserId(currentUser);
        followRepository.findByFollowerIdAndFolloweeId(currentUserId, targetUserId).ifPresent(follow -> {
            follow.setStatus(CANCELED);
            followRepository.save(follow);
        });
        Map<String, Object> result = status(currentUser, targetUserId);
        result.put("following", false);
        result.put("mutual", false);
        return result;
    }

    public Map<String, Object> status(CurrentUser currentUser, Long targetUserId) {
        Long currentUserId = currentUser == null ? null : currentUser.id();
        boolean following = currentUserId != null
                && followRepository.existsByFollowerIdAndFolloweeIdAndStatus(currentUserId, targetUserId, ACTIVE);
        boolean mutual = currentUserId != null
                && following
                && followRepository.existsByFollowerIdAndFolloweeIdAndStatus(targetUserId, currentUserId, ACTIVE);
        Map<String, Object> result = new HashMap<>();
        result.put("following", following);
        result.put("mutual", mutual);
        result.put("followingCount", followRepository.countByFollowerIdAndStatus(targetUserId, ACTIVE));
        result.put("followerCount", followRepository.countByFolloweeIdAndStatus(targetUserId, ACTIVE));
        return result;
    }

    public List<Map<String, Object>> following(Long userId) {
        List<UserFollow> follows = followRepository.findByFollowerIdAndStatusOrderByCreateTimeDesc(userId, ACTIVE);
        Map<Long, User> users = userRepository.findAllById(follows.stream().map(UserFollow::getFolloweeId).toList()).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        return follows.stream().map(row -> toUserCard(users.get(row.getFolloweeId()), row.getCreateTime())).toList();
    }

    public List<Map<String, Object>> followers(Long userId) {
        List<UserFollow> follows = followRepository.findByFolloweeIdAndStatusOrderByCreateTimeDesc(userId, ACTIVE);
        Map<Long, User> users = userRepository.findAllById(follows.stream().map(UserFollow::getFollowerId).toList()).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        return follows.stream().map(row -> toUserCard(users.get(row.getFollowerId()), row.getCreateTime())).toList();
    }

    private Map<String, Object> toUserCard(User user, Object followTime) {
        Map<String, Object> card = new HashMap<>();
        if (user == null) {
            card.put("userId", null);
            card.put("username", "已注销用户");
            card.put("avatar", null);
        } else {
            card.put("userId", user.getUserId());
            card.put("username", user.getUsername());
            card.put("avatar", user.getAvatar());
            card.put("skateStyle", user.getSkateStyle());
            card.put("level", user.getLevel() == null ? 1 : user.getLevel());
        }
        card.put("followTime", followTime);
        return card;
    }

    private User requireTarget(Long currentUserId, Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException("目标用户不存在");
        }
        if (currentUserId.equals(targetUserId)) {
            throw new BizException("不能关注自己");
        }
        return userRepository.findById(targetUserId).orElseThrow(() -> new BizException("目标用户不存在"));
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser.id();
    }

    private String displayName(CurrentUser currentUser) {
        return currentUser == null || currentUser.name() == null || currentUser.name().isBlank()
                ? "有用户"
                : currentUser.name();
    }
}
