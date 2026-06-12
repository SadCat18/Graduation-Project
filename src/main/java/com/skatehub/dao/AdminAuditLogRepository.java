package com.skatehub.dao;

import com.skatehub.pojo.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findAllByOrderByCreateTimeDesc();
}
