package com.javademo1.service;

import com.javademo1.dao.BannerRepository;
import com.javademo1.dao.NewsRepository;
import com.javademo1.dao.NoticeRepository;
import com.javademo1.dao.PlaceRepository;
import com.javademo1.dao.VideoRepository;
import com.javademo1.pojo.Banner;
import com.javademo1.pojo.News;
import com.javademo1.pojo.Notice;
import com.javademo1.pojo.Place;
import com.javademo1.pojo.Video;
import com.javademo1.util.BizException;
import com.javademo1.util.NewsStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicContentService {

    private final NoticeRepository noticeRepository;
    private final NewsRepository newsRepository;
    private final PlaceRepository placeRepository;
    private final VideoRepository videoRepository;
    private final BannerRepository bannerRepository;
    private final CommunityBulletinService communityBulletinService;
    private final PlaceReviewService placeReviewService;

    public List<Notice> notices() {
        return noticeRepository.findByStatusOrderByCreateTimeDesc("0");
    }

    public List<Map<String, Object>> bulletins(Integer limit) {
        return communityBulletinService.listPublicApproved(limit);
    }

    public List<Map<String, Object>> bulletinsAll() {
        return communityBulletinService.listPublicApprovedAll();
    }

    public Map<String, Object> bulletinDetail(Long bulletinId) {
        return communityBulletinService.publicDetail(bulletinId);
    }

    public List<News> news() {
        return newsRepository.findAllByStatusOrderBySyncTimeDescCreateTimeDesc(NewsStatus.APPROVED);
    }

    public News newsDetail(Long newsId) {
        return newsRepository.findByNewsIdAndStatus(newsId, NewsStatus.APPROVED)
                .orElseThrow(() -> new BizException("资讯不存在或未审核通过"));
    }

    public List<Place> places() {
        return placeRepository.findAllByOrderByScoreDescCreateTimeDesc();
    }

    public Map<String, Object> placeDetail(Long placeId) {
        Place place = placeRepository.findById(placeId).orElseThrow(() -> new BizException("场地不存在"));
        Map<String, Object> map = new HashMap<>();
        map.put("placeId", place.getPlaceId());
        map.put("name", place.getName());
        map.put("address", place.getAddress());
        map.put("intro", place.getIntro());
        map.put("score", place.getScore());
        map.put("reviewCount", place.getReviewCount());
        map.put("createTime", place.getCreateTime());
        map.put("latestReview", placeReviewService.latestReview(placeId));
        return map;
    }

    public List<Map<String, Object>> placeReviews(Long placeId) {
        placeRepository.findById(placeId).orElseThrow(() -> new BizException("场地不存在"));
        return placeReviewService.listByPlace(placeId);
    }

    public List<Video> videos() {
        return videoRepository.findAllByOrderByCreateTimeDesc();
    }

    public List<Banner> banners() {
        return bannerRepository.findByStatusOrderBySortNumAscCreateTimeDesc("0");
    }
}
