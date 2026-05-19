package com.javademo1.dao;

import com.javademo1.pojo.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findAllByOrderByCreateTimeDesc();
}

