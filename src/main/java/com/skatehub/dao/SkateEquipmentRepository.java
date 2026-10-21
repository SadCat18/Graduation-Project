package com.skatehub.dao;

import com.skatehub.pojo.skate.SkateEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SkateEquipmentRepository extends JpaRepository<SkateEquipment, Long>, JpaSpecificationExecutor<SkateEquipment> {

    Optional<SkateEquipment> findByEquipmentIdAndStatus(Long equipmentId, String status);
    @Query("select distinct e.category from SkateEquipment e where e.status='1' and e.category is not null and trim(e.category) <> ''")
    java.util.List<String> findPublicCategories();
}
