package com.skatehub.dao;

import com.skatehub.pojo.skate.EventSignup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventSignupRepository extends JpaRepository<EventSignup, Long> {

    Optional<EventSignup> findByEventIdAndUserId(Long eventId, Long userId);

    long countByEventIdAndSignupStatus(Long eventId, String signupStatus);

    long countByUserIdAndSignupStatus(Long userId, String signupStatus);

    List<EventSignup> findByUserIdOrderBySignupTimeDesc(Long userId);
}
