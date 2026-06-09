package com.skatehub.dao;

import com.skatehub.pojo.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findAllByOrderByIsTopDescCreateTimeDesc(Pageable pageable);

    Page<Post> findByCategoryOrderByIsTopDescCreateTimeDesc(String category, Pageable pageable);

    @Query(value = """
            SELECT p.*
            FROM tb_post p
            WHERE (:category IS NULL OR :category = '' OR p.category = :category)
              AND (:keyword IS NULL OR :keyword = ''
                   OR p.title LIKE CONCAT('%', :keyword, '%')
                   OR p.content LIKE CONCAT('%', :keyword, '%'))
            ORDER BY
              CASE WHEN :sort <> 'latest' THEN p.is_top ELSE '0' END DESC,
              CASE WHEN :sort = 'likes' THEN IFNULL(p.like_count, 0) ELSE 0 END DESC,
              CASE WHEN :sort = 'comments' THEN (SELECT COUNT(1) FROM tb_comment c WHERE c.post_id = p.post_id) ELSE 0 END DESC,
              CASE WHEN :sort = 'hot' THEN (IFNULL(p.like_count, 0) * 2 + (SELECT COUNT(1) FROM tb_comment c2 WHERE c2.post_id = p.post_id)) ELSE 0 END DESC,
              p.create_time DESC,
              p.post_id DESC
            """,
            countQuery = """
                    SELECT COUNT(1)
                    FROM tb_post p
                    WHERE (:category IS NULL OR :category = '' OR p.category = :category)
                      AND (:keyword IS NULL OR :keyword = ''
                           OR p.title LIKE CONCAT('%', :keyword, '%')
                           OR p.content LIKE CONCAT('%', :keyword, '%'))
                    """,
            nativeQuery = true)
    Page<Post> searchPublicPosts(@Param("keyword") String keyword,
                                 @Param("category") String category,
                                 @Param("sort") String sort,
                                 Pageable pageable);

    @Query(value = """
            SELECT p.*
            FROM tb_post p
            LEFT JOIN tb_user u ON u.user_id = p.user_id
            WHERE (:category IS NULL OR :category = '' OR p.category = :category)
              AND (:top IS NULL OR :top = '' OR :top = 'all'
                   OR (:top = 'top' AND p.is_top = '1')
                   OR (:top = 'normal' AND (p.is_top IS NULL OR p.is_top <> '1')))
              AND (:keyword IS NULL OR :keyword = ''
                   OR p.title LIKE CONCAT('%', :keyword, '%')
                   OR p.content LIKE CONCAT('%', :keyword, '%')
                   OR u.username LIKE CONCAT('%', :keyword, '%'))
            ORDER BY
              CASE WHEN :sort = 'likes' THEN IFNULL(p.like_count, 0) ELSE 0 END DESC,
              CASE WHEN :sort = 'comments' THEN (SELECT COUNT(1) FROM tb_comment c WHERE c.post_id = p.post_id) ELSE 0 END DESC,
              CASE WHEN :sort = 'collects' THEN IFNULL(p.collect_count, 0) ELSE 0 END DESC,
              p.is_top DESC,
              p.create_time DESC,
              p.post_id DESC
            """,
            countQuery = """
                    SELECT COUNT(1)
                    FROM tb_post p
                    LEFT JOIN tb_user u ON u.user_id = p.user_id
                    WHERE (:category IS NULL OR :category = '' OR p.category = :category)
                      AND (:top IS NULL OR :top = '' OR :top = 'all'
                           OR (:top = 'top' AND p.is_top = '1')
                           OR (:top = 'normal' AND (p.is_top IS NULL OR p.is_top <> '1')))
                      AND (:keyword IS NULL OR :keyword = ''
                           OR p.title LIKE CONCAT('%', :keyword, '%')
                           OR p.content LIKE CONCAT('%', :keyword, '%')
                           OR u.username LIKE CONCAT('%', :keyword, '%'))
                    """,
            nativeQuery = true)
    Page<Post> searchAdminPosts(@Param("keyword") String keyword,
                                @Param("category") String category,
                                @Param("top") String top,
                                @Param("sort") String sort,
                                Pageable pageable);

    List<Post> findByUserIdOrderByCreateTimeDesc(Long userId);

    long countByUserId(Long userId);

    long countByCreateTimeBetween(LocalDateTime start, LocalDateTime end);

    List<Post> findTop10ByOrderByLikeCountDescCreateTimeDesc();
}
