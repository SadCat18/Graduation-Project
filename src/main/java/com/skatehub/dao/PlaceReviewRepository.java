package com.skatehub.dao;

import com.skatehub.pojo.PlaceReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {
    List<PlaceReview> findByPlaceIdOrderByCreateTimeDesc(Long placeId);
    Optional<PlaceReview> findTop1ByPlaceIdOrderByCreateTimeDesc(Long placeId);
    long countByPlaceId(Long placeId);
}
