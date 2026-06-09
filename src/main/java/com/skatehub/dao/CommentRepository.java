package com.skatehub.dao;

import com.skatehub.pojo.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreateTimeAsc(Long postId);

    long countByPostId(Long postId);

    Page<Comment> findAllByOrderByCreateTimeDesc(Pageable pageable);

    @Query("""
            SELECT c
            FROM Comment c
            WHERE (:postId IS NULL OR c.postId = :postId)
              AND (:userId IS NULL OR c.userId = :userId)
              AND (:type IS NULL OR :type = '' OR :type = 'all'
                   OR (:type = 'root' AND (c.parentId IS NULL OR c.parentId = 0))
                   OR (:type = 'reply' AND c.parentId IS NOT NULL AND c.parentId <> 0))
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR c.postId IN (
                        SELECT p.postId FROM Post p
                        WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   )
                   OR c.userId IN (
                        SELECT u.userId FROM User u
                        WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   ))
            """)
    Page<Comment> searchAdminComments(@Param("keyword") String keyword,
                                      @Param("postId") Long postId,
                                      @Param("userId") Long userId,
                                      @Param("type") String type,
                                      Pageable pageable);

    List<Comment> findTop20ByPostIdOrderByCreateTimeAsc(Long postId);

    List<Comment> findTop5ByPostIdOrderByCreateTimeDesc(Long postId);
}
