package com.skatehub.dao;

import com.skatehub.pojo.skate.UserEquipmentFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserEquipmentFavoriteRepository extends JpaRepository<UserEquipmentFavorite, Long> {

    Optional<UserEquipmentFavorite> findByUserIdAndEquipmentId(Long userId, Long equipmentId);

    long countByUserId(Long userId);

    void deleteByUserIdAndEquipmentId(Long userId, Long equipmentId);

    List<UserEquipmentFavorite> findByUserIdOrderByCreateTimeDesc(Long userId);
}
