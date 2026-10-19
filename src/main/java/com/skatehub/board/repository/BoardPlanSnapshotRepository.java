package com.skatehub.board.repository;

import com.skatehub.board.entity.BoardPlanSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface BoardPlanSnapshotRepository extends JpaRepository<BoardPlanSnapshot,Long> {
    Optional<BoardPlanSnapshot> findByKeyAndOwnerId(String key,Long ownerId);
    Page<BoardPlanSnapshot> findByOwnerId(Long ownerId,Pageable page);
}
