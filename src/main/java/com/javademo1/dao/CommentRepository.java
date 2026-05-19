package com.javademo1.dao;

import com.javademo1.pojo.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreateTimeAsc(Long postId);

    long countByPostId(Long postId);

    Page<Comment> findAllByOrderByCreateTimeDesc(Pageable pageable);
}

