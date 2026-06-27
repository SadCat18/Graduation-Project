package com.skatehub.dao;

import com.skatehub.pojo.skate.UserTrainingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserTrainingRecordRepository extends JpaRepository<UserTrainingRecord, Long> {

    boolean existsByUserPlanIdAndTaskId(Long userPlanId, Long taskId);

    long countByUserPlanId(Long userPlanId);

    List<UserTrainingRecord> findByUserIdOrderByCreateTimeDesc(Long userId);

    List<UserTrainingRecord> findByUserPlanIdOrderByCreateTimeAsc(Long userPlanId);
}
