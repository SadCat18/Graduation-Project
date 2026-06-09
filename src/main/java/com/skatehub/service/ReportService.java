package com.skatehub.service;

import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.CommunityBulletinRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.ReportRecordRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Admin;
import com.skatehub.pojo.Comment;
import com.skatehub.pojo.CommunityBulletin;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.ReportRecord;
import com.skatehub.pojo.User;
import com.skatehub.pojo.report.ReportCreateRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Set<String> TARGET_TYPES = Set.of("POST", "COMMENT", "BULLETIN");
    private static final Set<String> REASONS = Set.of("广告", "辱骂", "人身攻击", "虚假信息", "违法违规", "其他");

    private final ReportRecordRepository reportRecordRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommunityBulletinRepository communityBulletinRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public ReportRecord create(CurrentUser currentUser, ReportCreateRequest request) {
        String targetType = normalizeTargetType(request.getTargetType());
        String reason = normalizeReason(request.getReason());
        Long targetId = request.getTargetId();
        if (reportRecordRepository.existsByUserIdAndTargetTypeAndTargetId(currentUser.id(), targetType, targetId)) {
            throw new BizException("你已经举报过该内容，请勿重复提交");
        }
        assertTargetExists(targetType, targetId);

        ReportRecord record = new ReportRecord();
        record.setUserId(currentUser.id());
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setReason(reason);
        record.setDetail(trimToNull(request.getDetail()));
        record.setStatus("0");
        return reportRecordRepository.save(record);
    }

    public List<Map<String, Object>> adminList() {
        return reportRecordRepository.findAllByOrderByCreateTimeDesc().stream()
                .map(this::toAdminVO)
                .toList();
    }

    public ReportRecord handle(Long reportId, CurrentUser adminUser, String status, String handleNote) {
        ReportRecord record = reportRecordRepository.findById(reportId)
                .orElseThrow(() -> new BizException("举报记录不存在"));
        if (!"1".equals(status) && !"2".equals(status)) {
            throw new BizException("处理状态不合法");
        }
        record.setStatus(status);
        record.setHandleAdminId(adminUser.id());
        record.setHandleNote(trimToNull(handleNote));
        record.setHandleTime(LocalDateTime.now());
        return reportRecordRepository.save(record);
    }

    public void deleteReportedTarget(Long reportId, CurrentUser adminUser) {
        ReportRecord record = reportRecordRepository.findById(reportId)
                .orElseThrow(() -> new BizException("举报记录不存在"));
        String targetType = record.getTargetType();
        Long targetId = record.getTargetId();
        if ("POST".equals(targetType)) {
            postRepository.findById(targetId).ifPresent(postRepository::delete);
        } else if ("COMMENT".equals(targetType)) {
            commentRepository.findById(targetId).ifPresent(commentRepository::delete);
        } else if ("BULLETIN".equals(targetType)) {
            communityBulletinRepository.findById(targetId).ifPresent(communityBulletinRepository::delete);
        }
        record.setStatus("1");
        record.setHandleAdminId(adminUser.id());
        record.setHandleNote("已删除违规内容");
        record.setHandleTime(LocalDateTime.now());
        reportRecordRepository.save(record);
    }

    private Map<String, Object> toAdminVO(ReportRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reportId", record.getReportId());
        map.put("targetType", record.getTargetType());
        map.put("targetId", record.getTargetId());
        map.put("reason", record.getReason());
        map.put("detail", record.getDetail());
        map.put("status", record.getStatus());
        map.put("statusLabel", statusLabel(record.getStatus()));
        map.put("createTime", record.getCreateTime());
        map.put("handleTime", record.getHandleTime());
        map.put("handleNote", record.getHandleNote());
        map.put("reporterName", userRepository.findById(record.getUserId())
                .map(User::getUsername)
                .orElse("已注销用户"));
        map.put("handlerName", resolveHandlerName(record.getHandleAdminId()));
        map.put("targetTitle", targetTitle(record.getTargetType(), record.getTargetId()));
        return map;
    }

    private String resolveHandlerName(Long handleAdminId) {
        if (handleAdminId == null) {
            return null;
        }
        return adminRepository.findById(handleAdminId)
                .map(Admin::getAccount)
                .orElse(null);
    }

    private String targetTitle(String targetType, Long targetId) {
        if ("POST".equals(targetType)) {
            return postRepository.findById(targetId).map(Post::getTitle).orElse("帖子已删除");
        }
        if ("COMMENT".equals(targetType)) {
            return commentRepository.findById(targetId)
                    .map(Comment::getContent)
                    .map(s -> s.length() > 30 ? s.substring(0, 30) + "..." : s)
                    .orElse("评论已删除");
        }
        return communityBulletinRepository.findById(targetId)
                .map(CommunityBulletin::getTitle)
                .orElse("快讯已删除");
    }

    private void assertTargetExists(String targetType, Long targetId) {
        boolean exists;
        if ("POST".equals(targetType)) {
            exists = postRepository.existsById(targetId);
        } else if ("COMMENT".equals(targetType)) {
            exists = commentRepository.existsById(targetId);
        } else {
            exists = communityBulletinRepository.existsById(targetId);
        }
        if (!exists) {
            throw new BizException("举报内容不存在");
        }
    }

    private String normalizeTargetType(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException("举报目标类型不能为空");
        }
        String targetType = value.trim().toUpperCase();
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BizException("举报目标类型不合法");
        }
        return targetType;
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException("举报原因不能为空");
        }
        String reason = value.trim();
        if (!REASONS.contains(reason)) {
            throw new BizException("举报原因不合法");
        }
        return reason;
    }

    private String statusLabel(String status) {
        if ("1".equals(status)) {
            return "已处理";
        }
        if ("2".equals(status)) {
            return "已驳回";
        }
        return "待处理";
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
