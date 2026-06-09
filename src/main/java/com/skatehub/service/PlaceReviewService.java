package com.skatehub.service;

import com.skatehub.dao.PlaceRepository;
import com.skatehub.dao.PlaceReviewRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Place;
import com.skatehub.pojo.PlaceReview;
import com.skatehub.pojo.User;
import com.skatehub.pojo.place.PlaceReviewCreateRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaceReviewService {

    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final UserRepository userRepository;

    public PlaceReview create(CurrentUser currentUser, PlaceReviewCreateRequest request) {
        Place place = placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new BizException("场地不存在或已下线"));
        Integer score = request.getScore();
        if (score == null || score < 1 || score > 5) {
            throw new BizException("评分范围应为 1 到 5 分");
        }

        PlaceReview review = new PlaceReview();
        review.setPlaceId(place.getPlaceId());
        review.setUserId(currentUser.id());
        review.setScore(score);
        review.setContent(trimToNull(request.getContent()));
        review.setImages(trimToNull(request.getImages()));
        PlaceReview saved = placeReviewRepository.save(review);
        refreshPlaceScore(place.getPlaceId());
        return saved;
    }

    public List<Map<String, Object>> listByPlace(Long placeId) {
        return placeReviewRepository.findByPlaceIdOrderByCreateTimeDesc(placeId).stream()
                .map(this::toReviewVO)
                .collect(Collectors.toList());
    }

    public Map<String, Object> latestReview(Long placeId) {
        return placeReviewRepository.findTop1ByPlaceIdOrderByCreateTimeDesc(placeId)
                .map(this::toReviewVO)
                .orElse(null);
    }

    public List<Map<String, Object>> adminReviews() {
        return placeReviewRepository.findAll().stream()
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .map(review -> {
                    Map<String, Object> map = toReviewVO(review);
                    Place place = placeRepository.findById(review.getPlaceId()).orElse(null);
                    map.put("placeName", place == null ? "未知场地" : place.getName());
                    map.put("placeAddress", place == null ? "" : place.getAddress());
                    return map;
                })
                .collect(Collectors.toList());
    }

    public void adminDeleteReview(Long reviewId) {
        PlaceReview review = placeReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException("评价不存在"));
        Long placeId = review.getPlaceId();
        placeReviewRepository.delete(review);
        refreshPlaceScore(placeId);
    }

    public void refreshPlaceScore(Long placeId) {
        Place place = placeRepository.findById(placeId).orElse(null);
        if (place == null) return;
        List<PlaceReview> reviews = placeReviewRepository.findByPlaceIdOrderByCreateTimeDesc(placeId);
        if (reviews.isEmpty()) {
            place.setScore(BigDecimal.ZERO);
            place.setReviewCount(0);
        } else {
            BigDecimal total = reviews.stream()
                    .map(item -> BigDecimal.valueOf(item.getScore()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = total.divide(BigDecimal.valueOf(reviews.size()), 1, RoundingMode.HALF_UP);
            place.setScore(avg);
            place.setReviewCount(reviews.size());
        }
        placeRepository.save(place);
    }

    private Map<String, Object> toReviewVO(PlaceReview review) {
        Map<String, Object> map = new HashMap<>();
        map.put("reviewId", review.getReviewId());
        map.put("placeId", review.getPlaceId());
        map.put("userId", review.getUserId());
        map.put("score", review.getScore());
        map.put("content", review.getContent());
        map.put("images", review.getImages());
        map.put("createTime", review.getCreateTime());
        User user = userRepository.findById(review.getUserId()).orElse(null);
        map.put("username", user == null ? "已注销用户" : user.getUsername());
        return map;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }
}
