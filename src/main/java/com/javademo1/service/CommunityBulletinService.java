package com.javademo1.service;

import com.javademo1.dao.CommunityBulletinRepository;
import com.javademo1.dao.AdminRepository;
import com.javademo1.dao.UserRepository;
import com.javademo1.pojo.Admin;
import com.javademo1.pojo.CommunityBulletin;
import com.javademo1.pojo.User;
import com.javademo1.pojo.admin.CommunityBulletinReviewRequest;
import com.javademo1.pojo.community.CommunityBulletinCreateRequest;
import com.javademo1.util.BizException;
import com.javademo1.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityBulletinService {
    private static final List<String> BULLETIN_TYPE_ORDER = Arrays.asList(
            "活动通知", "赛事快讯", "同城动态", "场地通知", "路线推荐", "安全提醒", "官方公告", "经验分享"
    );
    private static final Set<String> ALLOWED_BULLETIN_TYPES = new HashSet<>(BULLETIN_TYPE_ORDER);

    private final CommunityBulletinRepository communityBulletinRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public CommunityBulletin create(CurrentUser currentUser, CommunityBulletinCreateRequest request) {
        if (!"ADMIN".equalsIgnoreCase(currentUser.role())) {
            User user = userRepository.findById(currentUser.id()).orElseThrow(() -> new BizException("用户不存在"));
            if (!"1".equals(user.getBulletinPermission())) {
                throw new BizException("当前账号暂无社区快讯发布权限");
            }
        }
        CommunityBulletin bulletin = new CommunityBulletin();
        bulletin.setTitle(request.getTitle());
        bulletin.setContent(request.getContent());
        bulletin.setBulletinType(normalizeAndValidateType(request.getBulletinType()));
        bulletin.setImageUrls(trimToNull(request.getImageUrls()));
        bulletin.setPublisherUserId(currentUser.id());
        bulletin.setStatus("0");
        return communityBulletinRepository.save(bulletin);
    }

    public List<Map<String, Object>> listPublicApproved(Integer limit) {
        int actualLimit = (limit == null || limit < 1) ? 5 : Math.min(limit, 20);
        return communityBulletinRepository.findByStatusOrderByCreateTimeDesc("1").stream()
                .limit(actualLimit)
                .map(this::toViewMap)
                .toList();
    }

    public List<Map<String, Object>> listPublicApprovedAll() {
        return communityBulletinRepository.findByStatusOrderByCreateTimeDesc("1").stream()
                .map(this::toViewMap)
                .toList();
    }

    public Map<String, Object> publicDetail(Long bulletinId) {
        CommunityBulletin bulletin = communityBulletinRepository.findById(bulletinId)
                .orElseThrow(() -> new BizException("社区快讯不存在"));
        if (!"1".equals(bulletin.getStatus())) {
            throw new BizException("社区快讯未公开");
        }
        return toViewMap(bulletin);
    }

    public List<Map<String, Object>> adminList(String type, String status) {
        String normalizedType = trimToNull(type);
        String normalizedStatus = trimToNull(status);
        return communityBulletinRepository.findAllByOrderByCreateTimeDesc().stream()
                .filter(item -> normalizedType == null || normalizeForView(item.getBulletinType()).equals(normalizedType))
                .filter(item -> normalizedStatus == null || normalizedStatus.equals(item.getStatus()))
                .map(this::toViewMap)
                .toList();
    }

    public List<Map<String, Object>> adminTypeStats() {
        Map<String, Long> grouped = communityBulletinRepository.findAllByOrderByCreateTimeDesc().stream()
                .collect(Collectors.groupingBy(
                        item -> normalizeForView(item.getBulletinType()),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        return BULLETIN_TYPE_ORDER.stream()
                .map(type -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", type);
                    row.put("count", grouped.getOrDefault(type, 0L));
                    return row;
                })
                .toList();
    }

    public CommunityBulletin review(Long bulletinId, CurrentUser adminUser, CommunityBulletinReviewRequest request) {
        CommunityBulletin bulletin = communityBulletinRepository.findById(bulletinId)
                .orElseThrow(() -> new BizException("社区快讯不存在"));
        String status = "2".equals(request.getStatus()) ? "2" : "1";
        bulletin.setStatus(status);
        bulletin.setReviewAdminId(adminUser.id());
        bulletin.setReviewTime(LocalDateTime.now());
        bulletin.setRejectReason("2".equals(status) ? trimToNull(request.getRejectReason()) : null);
        return communityBulletinRepository.save(bulletin);
    }

    public void delete(Long bulletinId) {
        communityBulletinRepository.deleteById(bulletinId);
    }

    private Map<String, Object> toViewMap(CommunityBulletin bulletin) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bulletinId", bulletin.getBulletinId());
        map.put("title", bulletin.getTitle());
        map.put("content", bulletin.getContent());
        map.put("imageUrls", bulletin.getImageUrls());
        map.put("bulletinType", normalizeForView(bulletin.getBulletinType()));
        map.put("status", bulletin.getStatus());
        map.put("publisherUserId", bulletin.getPublisherUserId());
        String publisherName = userRepository.findById(bulletin.getPublisherUserId())
                .map(User::getUsername)
                .orElseGet(() -> adminRepository.findById(bulletin.getPublisherUserId())
                        .map(Admin::getAccount)
                        .orElse("已注销用户"));
        map.put("publisherName", publisherName);
        map.put("createTime", bulletin.getCreateTime());
        map.put("reviewAdminId", bulletin.getReviewAdminId());
        map.put("reviewTime", bulletin.getReviewTime());
        map.put("rejectReason", bulletin.getRejectReason());
        return map;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeAndValidateType(String type) {
        String normalized = trimToNull(type);
        if (normalized == null) {
            throw new BizException("快讯类型不能为空");
        }
        if (!ALLOWED_BULLETIN_TYPES.contains(normalized)) {
            throw new BizException("快讯类型不合法，请从固定分类中选择");
        }
        return normalized;
    }

    private String normalizeForView(String type) {
        String normalized = trimToNull(type);
        if (normalized == null) return "经验分享";
        return ALLOWED_BULLETIN_TYPES.contains(normalized) ? normalized : "经验分享";
    }
}
