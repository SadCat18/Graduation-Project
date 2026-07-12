package com.skatehub.dao;

import com.skatehub.pojo.ProductEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductEventRepository extends JpaRepository<ProductEvent, Long> {

    long countByEventTypeAndCreateTimeBetween(String eventType, LocalDateTime start, LocalDateTime end);

    long countByCreateTimeBetween(LocalDateTime start, LocalDateTime end);

    @Query(value = """
            SELECT COUNT(DISTINCT user_id)
            FROM tb_product_event
            WHERE user_id IS NOT NULL
              AND create_time BETWEEN ?1 AND ?2
            """, nativeQuery = true)
    long countDistinctUsersBetween(LocalDateTime start, LocalDateTime end);

    List<ProductEvent> findTop100ByOrderByCreateTimeDesc();
}
