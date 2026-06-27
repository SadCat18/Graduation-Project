package com.skatehub.dao;

import com.skatehub.pojo.skate.TrainingTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainingTaskRepository extends JpaRepository<TrainingTask, Long> {

    List<TrainingTask> findByPlanIdOrderByDayNoAscSortOrderAscTaskIdAsc(Long planId);

    long countByPlanId(Long planId);
}
