package com.skatehub.dao;

import com.skatehub.pojo.ReportRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRecordRepository extends JpaRepository<ReportRecord, Long> {

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    long countByTargetTypeAndTargetId(String targetType, Long targetId);

    List<ReportRecord> findAllByOrderByCreateTimeDesc();
}
