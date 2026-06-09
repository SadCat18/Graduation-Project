package com.skatehub.dao;

import com.skatehub.pojo.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByPhone(String phone);

    long countByCreateTimeBetween(LocalDateTime start, LocalDateTime end);
}
