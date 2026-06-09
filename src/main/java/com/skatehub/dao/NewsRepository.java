package com.skatehub.dao;

import com.skatehub.pojo.News;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsRepository extends JpaRepository<News, Long> {

    List<News> findAllByOrderByCreateTimeDesc();

    List<News> findAllByStatusOrderBySyncTimeDescCreateTimeDesc(String status);

    Optional<News> findByNewsIdAndStatus(Long newsId, String status);

    Optional<News> findFirstBySourceUrl(String sourceUrl);

    Optional<News> findFirstByOriginTitle(String originTitle);

    Optional<News> findFirstByOriginTitleAndOriginContent(String originTitle, String originContent);

    Optional<News> findFirstByTitleAndContent(String title, String content);
}
