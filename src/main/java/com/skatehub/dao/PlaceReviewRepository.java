package com.skatehub.dao;

import com.skatehub.pojo.PlaceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {
    List<PlaceReview> findByPlaceIdOrderByCreateTimeDesc(Long placeId);
    Optional<PlaceReview> findTop1ByPlaceIdOrderByCreateTimeDesc(Long placeId);
    long countByPlaceId(Long placeId);
    long countByUserId(Long userId);
    interface LocalReviewAggregate {
        Long getPlaceId();
        Long getScoreTotal();
        Long getReviewCount();
    }
    @Query("select r.placeId as placeId, sum(r.score) as scoreTotal, count(r) as reviewCount from PlaceReview r where r.placeId in :placeIds group by r.placeId")
    List<LocalReviewAggregate> aggregateLocalReviews(@Param("placeIds") java.util.Collection<Long> placeIds);
}
