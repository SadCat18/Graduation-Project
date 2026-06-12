package com.skatehub.service;

import com.skatehub.dao.AdminAuditLogRepository;
import com.skatehub.pojo.AdminAuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public void record(Long adminId, String targetType, Long targetId, String action, String result) {
        record(adminId, targetType, targetId, action, result, null);
    }

    public void record(Long adminId, String targetType, Long targetId, String action, String result, String detail) {
        if (adminId == null || !StringUtils.hasText(targetType) || !StringUtils.hasText(action) || !StringUtils.hasText(result)) {
            return;
        }
        AdminAuditLog log = new AdminAuditLog();
        log.setAdminId(adminId);
        log.setTargetType(targetType.trim().toUpperCase());
        log.setTargetId(targetId);
        log.setAction(action.trim().toUpperCase());
        log.setResult(result.trim().toUpperCase());
        log.setDetail(trimDetail(detail));
        adminAuditLogRepository.save(log);
    }

    public List<AdminAuditLog> list() {
        return adminAuditLogRepository.findAllByOrderByCreateTimeDesc();
    }

    private String trimDetail(String detail) {
        if (!StringUtils.hasText(detail)) {
            return null;
        }
        String trimmed = detail.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
