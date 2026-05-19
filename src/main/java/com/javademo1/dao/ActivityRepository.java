package com.javademo1.dao;

import com.javademo1.pojo.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ActivityRepository extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    long countByUserId(Long userId);

    Page<Activity> findByReviewStatusOrderByCreateTimeDesc(String reviewStatus, Pageable pageable);
}
