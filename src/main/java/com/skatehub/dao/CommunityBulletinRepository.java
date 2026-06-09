package com.skatehub.dao;

import com.skatehub.pojo.CommunityBulletin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityBulletinRepository extends JpaRepository<CommunityBulletin, Long> {

    List<CommunityBulletin> findAllByOrderByCreateTimeDesc();

    List<CommunityBulletin> findByStatusOrderByCreateTimeDesc(String status);
}
