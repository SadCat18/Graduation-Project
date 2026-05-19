package com.javademo1.service;

import com.javademo1.pojo.News;
import com.javademo1.pojo.Notice;
import com.javademo1.pojo.Place;
import com.javademo1.pojo.Video;
import com.javademo1.pojo.Banner;
import com.javademo1.dao.BannerRepository;
import com.javademo1.dao.NewsRepository;
import com.javademo1.dao.NoticeRepository;
import com.javademo1.dao.PlaceRepository;
import com.javademo1.dao.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicContentService {

    private final NoticeRepository noticeRepository;
    private final NewsRepository newsRepository;
    private final PlaceRepository placeRepository;
    private final VideoRepository videoRepository;
    private final BannerRepository bannerRepository;
    private final CommunityBulletinService communityBulletinService;

    public List<Notice> notices() {
        return noticeRepository.findByStatusOrderByCreateTimeDesc("0");
    }

    public List<java.util.Map<String, Object>> bulletins(Integer limit) {
        return communityBulletinService.listPublicApproved(limit);
    }

    public List<java.util.Map<String, Object>> bulletinsAll() {
        return communityBulletinService.listPublicApprovedAll();
    }

    public java.util.Map<String, Object> bulletinDetail(Long bulletinId) {
        return communityBulletinService.publicDetail(bulletinId);
    }

    public List<News> news() {
        return newsRepository.findAllByOrderByCreateTimeDesc();
    }

    public List<Place> places() {
        return placeRepository.findAllByOrderByScoreDescCreateTimeDesc();
    }

    public List<Video> videos() {
        return videoRepository.findAllByOrderByCreateTimeDesc();
    }

    public List<Banner> banners() {
        return bannerRepository.findByStatusOrderBySortNumAscCreateTimeDesc("0");
    }
}

