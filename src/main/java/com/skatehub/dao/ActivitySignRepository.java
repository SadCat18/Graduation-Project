package com.skatehub.dao;

import com.skatehub.pojo.ActivitySign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ActivitySignRepository extends JpaRepository<ActivitySign, Long> {

    Optional<ActivitySign> findByActivityIdAndUserId(Long activityId, Long userId);

    List<ActivitySign> findByActivityId(Long activityId);

    List<ActivitySign> findByUserIdOrderBySignTimeDesc(Long userId);

    List<ActivitySign> findTop50ByUserIdOrderBySignTimeDesc(Long userId);

    List<ActivitySign> findByUserIdAndActivityIdIn(Long userId, List<Long> activityIds);

    long countByActivityId(Long activityId);

    long countByActivityIdAndSignStatus(Long activityId, String signStatus);

    long countByUserIdAndIsCheckin(Long userId, String isCheckin);

    Optional<ActivitySign> findBySignIdAndActivityId(Long signId, Long activityId);

    long countBySignTimeBetween(LocalDateTime start, LocalDateTime end);
}
