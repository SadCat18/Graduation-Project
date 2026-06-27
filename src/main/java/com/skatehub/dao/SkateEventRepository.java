package com.skatehub.dao;

import com.skatehub.pojo.skate.SkateEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SkateEventRepository extends JpaRepository<SkateEvent, Long>, JpaSpecificationExecutor<SkateEvent> {

    Optional<SkateEvent> findByEventIdAndStatus(Long eventId, String status);
}
