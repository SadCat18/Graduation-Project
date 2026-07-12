package com.skatehub.dao;

import com.skatehub.pojo.ReportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ReportRecordRepository extends JpaRepository<ReportRecord, Long>, JpaSpecificationExecutor<ReportRecord> {

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    long countByTargetTypeAndTargetId(String targetType, Long targetId);

    List<ReportRecord> findAllByOrderByCreateTimeDesc();
}
