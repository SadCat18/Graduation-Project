package com.skatehub.service;

import com.skatehub.dao.CommunityBulletinRepository;
import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Admin;
import com.skatehub.pojo.CommunityBulletin;
import com.skatehub.pojo.User;
import com.skatehub.pojo.admin.CommunityBulletinReviewRequest;
import com.skatehub.pojo.community.CommunityBulletinCreateRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.InputValidator;
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
    private static final Set<String> REVIEW_STATUSES = Set.of("0", "1", "2");

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
        int actualLimit = limit == null ? 5 : limit;
        if (actualLimit < 1 || actualLimit > 20) {
            throw new BizException("快讯数量限制不合法");
        }
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
        InputValidator.positiveId(bulletinId, "快讯ID");
        CommunityBulletin bulletin = communityBulletinRepository.findById(bulletinId)
                .orElseThrow(() -> new BizException("社区快讯不存在"));
        if (!"1".equals(bulletin.getStatus())) {
            throw new BizException("社区快讯未公开");
        }
        return toViewMap(bulletin);
    }

    public List<Map<String, Object>> adminList(String type, String status) {
        String normalizedType = trimToNull(type);
        if (normalizedType != null && !ALLOWED_BULLETIN_TYPES.contains(normalizedType)) {
            throw new BizException("快讯类型不合法");
        }
        String normalizedStatus = InputValidator.optionalAllowed(status, REVIEW_STATUSES, null, "快讯状态");
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
        InputValidator.positiveId(bulletinId, "快讯ID");
        CommunityBulletin bulletin = communityBulletinRepository.findById(bulletinId)
                .orElseThrow(() -> new BizException("社区快讯不存在"));
        String status = InputValidator.requiredAllowed(request.getStatus(), Set.of("1", "2"), "审核状态");
        bulletin.setStatus(status);
        bulletin.setReviewAdminId(adminUser.id());
        bulletin.setReviewTime(LocalDateTime.now());
        bulletin.setRejectReason("2".equals(status) ? trimToNull(request.getRejectReason()) : null);
        return communityBulletinRepository.save(bulletin);
    }

    public void delete(Long bulletinId) {
        InputValidator.positiveId(bulletinId, "快讯ID");
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
