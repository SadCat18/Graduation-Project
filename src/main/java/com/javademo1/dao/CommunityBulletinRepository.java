package com.javademo1.dao;

import com.javademo1.pojo.CommunityBulletin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityBulletinRepository extends JpaRepository<CommunityBulletin, Long> {

    List<CommunityBulletin> findAllByOrderByCreateTimeDesc();

    List<CommunityBulletin> findByStatusOrderByCreateTimeDesc(String status);
}
