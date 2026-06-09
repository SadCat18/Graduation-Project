package com.skatehub.dao;

import com.skatehub.pojo.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByStatusOrderByCreateTimeDesc(String status);

    List<Notice> findAllByOrderByCreateTimeDesc();
}
