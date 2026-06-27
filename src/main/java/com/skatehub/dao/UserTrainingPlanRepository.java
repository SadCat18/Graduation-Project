package com.skatehub.dao;

import com.skatehub.pojo.skate.UserTrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserTrainingPlanRepository extends JpaRepository<UserTrainingPlan, Long> {

    Optional<UserTrainingPlan> findFirstByUserIdAndPlanIdAndStatusOrderByCreateTimeDesc(Long userId, Long planId, String status);

    Optional<UserTrainingPlan> findFirstByUserIdAndStatusOrderByCreateTimeDesc(Long userId, String status);

    Optional<UserTrainingPlan> findByUserPlanIdAndUserId(Long userPlanId, Long userId);

    List<UserTrainingPlan> findByUserIdOrderByCreateTimeDesc(Long userId);

    long countByUserIdAndStatus(Long userId, String status);
}
