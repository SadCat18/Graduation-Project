package com.javademo1.dao;

import com.javademo1.pojo.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    Optional<Interaction> findByUserIdAndTargetTypeAndTargetIdAndType(Long userId, String targetType, Long targetId, String type);

    long countByTargetTypeAndTargetIdAndType(String targetType, Long targetId, String type);
}

