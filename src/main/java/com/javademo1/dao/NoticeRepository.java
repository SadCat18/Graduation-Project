package com.javademo1.dao;

import com.javademo1.pojo.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByStatusOrderByCreateTimeDesc(String status);

    List<Notice> findAllByOrderByCreateTimeDesc();
}

