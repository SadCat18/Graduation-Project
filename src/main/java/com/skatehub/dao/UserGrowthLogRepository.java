package com.skatehub.dao;

import com.skatehub.pojo.UserGrowthLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserGrowthLogRepository extends JpaRepository<UserGrowthLog, Long> {
    Optional<UserGrowthLog> findByUserIdAndActionTypeAndTargetTypeAndTargetId(Long userId, String actionType, String targetType, Long targetId);
}
