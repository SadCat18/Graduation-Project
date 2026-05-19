package com.javademo1.dao;

import com.javademo1.pojo.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findAllByOrderByIsTopDescCreateTimeDesc(Pageable pageable);

    Page<Post> findByCategoryOrderByIsTopDescCreateTimeDesc(String category, Pageable pageable);

    long countByUserId(Long userId);
}

