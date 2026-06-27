package com.skatehub.dao;

import com.skatehub.pojo.skate.SkateEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SkateEquipmentRepository extends JpaRepository<SkateEquipment, Long>, JpaSpecificationExecutor<SkateEquipment> {

    Optional<SkateEquipment> findByEquipmentIdAndStatus(Long equipmentId, String status);
}
