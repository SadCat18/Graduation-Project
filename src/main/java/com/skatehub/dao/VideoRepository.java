package com.skatehub.dao;

import com.skatehub.pojo.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findAllByOrderByCreateTimeDesc();
}
