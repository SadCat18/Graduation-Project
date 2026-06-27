package com.skatehub.dao;

import com.skatehub.pojo.skate.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {

    List<TrainingPlan> findByStatusOrderByPlanIdAsc(String status);
}
