package com.skatehub.dao;

import com.skatehub.pojo.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    Optional<UserFollow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    boolean existsByFollowerIdAndFolloweeIdAndStatus(Long followerId, Long followeeId, String status);

    long countByFollowerIdAndStatus(Long followerId, String status);

    long countByFolloweeIdAndStatus(Long followeeId, String status);

    List<UserFollow> findByFollowerIdAndStatusOrderByCreateTimeDesc(Long followerId, String status);

    List<UserFollow> findByFolloweeIdAndStatusOrderByCreateTimeDesc(Long followeeId, String status);
}
