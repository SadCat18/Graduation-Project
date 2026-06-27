package com.skatehub.dao;

import com.skatehub.pojo.skate.TrickCheckin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrickCheckinRepository extends JpaRepository<TrickCheckin, Long> {

    List<TrickCheckin> findByUserIdAndTrickIdOrderByCreateTimeDesc(Long userId, Long trickId);

    List<TrickCheckin> findByUserIdOrderByCreateTimeDesc(Long userId);

    long countByUserId(Long userId);

    long countDistinctByUserIdAndMasterStatusIn(Long userId, List<String> masterStatuses);
}
