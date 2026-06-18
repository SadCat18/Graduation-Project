package com.skatehub.dao;

import com.skatehub.pojo.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BadgeRepository extends JpaRepository<Badge, Long> {

    List<Badge> findByEnabledOrderBySortOrderAsc(String enabled);

    Optional<Badge> findByCode(String code);
}
