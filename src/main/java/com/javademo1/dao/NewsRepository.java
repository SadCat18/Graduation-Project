package com.javademo1.dao;

import com.javademo1.pojo.News;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    List<News> findAllByOrderByCreateTimeDesc();
}

