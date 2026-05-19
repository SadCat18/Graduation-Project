package com.javademo1.dao;

import com.javademo1.pojo.ActivitySign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivitySignRepository extends JpaRepository<ActivitySign, Long> {

    Optional<ActivitySign> findByActivityIdAndUserId(Long activityId, Long userId);

    List<ActivitySign> findByActivityId(Long activityId);

    List<ActivitySign> findByUserIdOrderBySignTimeDesc(Long userId);

    long countByActivityId(Long activityId);

    long countByActivityIdAndSignStatus(Long activityId, String signStatus);

    Optional<ActivitySign> findBySignIdAndActivityId(Long signId, Long activityId);
}

