package com.skatehub.dao;

import com.skatehub.pojo.skate.SkateTrick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkateTrickRepository extends JpaRepository<SkateTrick, Long> {

    Optional<SkateTrick> findByTrickIdAndStatus(Long trickId, String status);

    List<SkateTrick> findByStatusOrderBySortOrderAscTrickIdAsc(String status);

    List<SkateTrick> findByCategoryAndStatusOrderBySortOrderAscTrickIdAsc(String category, String status);
}
