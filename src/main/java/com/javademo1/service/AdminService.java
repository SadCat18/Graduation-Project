package com.javademo1.service;

import com.javademo1.dao.*;
import com.javademo1.pojo.*;
import com.javademo1.pojo.admin.BannerSaveRequest;
import com.javademo1.pojo.admin.CommunityBulletinReviewRequest;
import com.javademo1.pojo.admin.NewsSaveRequest;
import com.javademo1.pojo.admin.NoticeSaveRequest;
import com.javademo1.pojo.admin.PlaceSaveRequest;
import com.javademo1.util.BizException;
import com.javademo1.util.CurrentUser;
import com.javademo1.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ActivityRepository activityRepository;
    private final NoticeRepository noticeRepository;
    private final NewsRepository newsRepository;
    private final PlaceRepository placeRepository;
    private final VideoRepository videoRepository;
    private final BannerRepository bannerRepository;
    private final AdminRepository adminRepository;
    private final ActivitySignRepository activitySignRepository;
    private final CommunityBulletinService communityBulletinService;
    private final CommunityBulletinRepository communityBulletinRepository;

    public PageResult<User> listUsers(Integer page, Integer size) {
        Page<User> result = userRepository.findAll(PageRequest.of(normalizePage(page), normalizeSize(size)));
        return new PageResult<>(result.getTotalElements(), result.getContent());
    }

    public User updateUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        user.setStatus("1".equals(status) ? "1" : "0");
        return userRepository.save(user);
    }

    public User updateUserBulletinPermission(Long userId, String permission) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        user.setBulletinPermission("1".equals(permission) ? "1" : "0");
        return userRepository.save(user);
    }

    public PageResult<Post> listPosts(Integer page, Integer size) {
        Page<Post> result = postRepository.findAll(PageRequest.of(normalizePage(page), normalizeSize(size)));
        return new PageResult<>(result.getTotalElements(), result.getContent());
    }

    public PageResult<Comment> listComments(Integer page, Integer size) {
        Page<Comment> result = commentRepository.findAllByOrderByCreateTimeDesc(PageRequest.of(normalizePage(page), normalizeSize(size)));
        return new PageResult<>(result.getTotalElements(), result.getContent());
    }

    public PageResult<Activity> listActivities(Integer page, Integer size, String reviewStatus) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizeSize(size), Sort.by(Sort.Direction.DESC, "createTime"));
        Page<Activity> result;
        if (reviewStatus == null || reviewStatus.isBlank()) {
            result = activityRepository.findAll(pageRequest);
        } else {
            result = activityRepository.findByReviewStatusOrderByCreateTimeDesc(reviewStatus.trim(), pageRequest);
        }
        return new PageResult<>(result.getTotalElements(), result.getContent());
    }

    public List<Notice> listNotices() {
        return noticeRepository.findAllByOrderByCreateTimeDesc();
    }

    public Notice createNotice(CurrentUser currentUser, NoticeSaveRequest request) {
        Notice notice = new Notice();
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setAdminId(currentUser.id());
        notice.setStatus("1".equals(request.getStatus()) ? "1" : "0");
        return noticeRepository.save(notice);
    }

    public Notice updateNotice(Long noticeId, NoticeSaveRequest request) {
        Notice notice = noticeRepository.findById(noticeId).orElseThrow(() -> new BizException("公告不存在"));
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setStatus("1".equals(request.getStatus()) ? "1" : "0");
        return noticeRepository.save(notice);
    }

    public void deleteNotice(Long noticeId) {
        noticeRepository.deleteById(noticeId);
    }

    public List<Map<String, Object>> listCommunityBulletins(String type, String status) {
        return communityBulletinService.adminList(type, status);
    }

    public List<Map<String, Object>> communityBulletinTypeStats() {
        return communityBulletinService.adminTypeStats();
    }

    public CommunityBulletin reviewCommunityBulletin(Long bulletinId, CurrentUser currentUser, CommunityBulletinReviewRequest request) {
        return communityBulletinService.review(bulletinId, currentUser, request);
    }

    public void deleteCommunityBulletin(Long bulletinId) {
        communityBulletinService.delete(bulletinId);
    }

    public List<News> listNews() {
        return newsRepository.findAllByOrderByCreateTimeDesc();
    }

    public News createNews(CurrentUser currentUser, NewsSaveRequest request) {
        News news = new News();
        news.setTitle(request.getTitle());
        news.setContent(request.getContent());
        news.setCategory(request.getCategory());
        news.setCover(request.getCover());
        news.setAdminId(currentUser.id());
        return newsRepository.save(news);
    }

    public News updateNews(Long newsId, NewsSaveRequest request) {
        News news = newsRepository.findById(newsId).orElseThrow(() -> new BizException("资讯不存在"));
        news.setTitle(request.getTitle());
        news.setContent(request.getContent());
        news.setCategory(request.getCategory());
        news.setCover(request.getCover());
        return newsRepository.save(news);
    }

    public void deleteNews(Long newsId) {
        newsRepository.deleteById(newsId);
    }

    public List<Place> listPlaces() {
        return placeRepository.findAllByOrderByScoreDescCreateTimeDesc();
    }

    public Place createPlace(PlaceSaveRequest request) {
        Place place = new Place();
        place.setName(request.getName());
        place.setAddress(request.getAddress());
        place.setIntro(request.getIntro());
        place.setScore(defaultScore(request.getScore()));
        return placeRepository.save(place);
    }

    public Place updatePlace(Long placeId, PlaceSaveRequest request) {
        Place place = placeRepository.findById(placeId).orElseThrow(() -> new BizException("场地不存在"));
        place.setName(request.getName());
        place.setAddress(request.getAddress());
        place.setIntro(request.getIntro());
        place.setScore(defaultScore(request.getScore()));
        return placeRepository.save(place);
    }

    public void deletePlace(Long placeId) {
        placeRepository.deleteById(placeId);
    }

    public List<Video> listVideos() {
        return videoRepository.findAllByOrderByCreateTimeDesc();
    }

    public List<Banner> listBanners() {
        return bannerRepository.findAllByOrderBySortNumAscCreateTimeDesc();
    }

    public Banner createBanner(CurrentUser currentUser, BannerSaveRequest request) {
        Banner banner = new Banner();
        banner.setTitle(request.getTitle());
        banner.setImageUrl(request.getImageUrl());
        banner.setLinkUrl(trimToNull(request.getLinkUrl()));
        banner.setSortNum(defaultSortNum(request.getSortNum()));
        banner.setIntervalSeconds(defaultIntervalSeconds(request.getIntervalSeconds()));
        banner.setStatus("1".equals(request.getStatus()) ? "1" : "0");
        banner.setAdminId(currentUser.id());
        return bannerRepository.save(banner);
    }

    public Banner updateBanner(Long bannerId, BannerSaveRequest request) {
        Banner banner = bannerRepository.findById(bannerId).orElseThrow(() -> new BizException("轮播图不存在"));
        banner.setTitle(request.getTitle());
        banner.setImageUrl(request.getImageUrl());
        banner.setLinkUrl(trimToNull(request.getLinkUrl()));
        banner.setSortNum(defaultSortNum(request.getSortNum()));
        banner.setIntervalSeconds(defaultIntervalSeconds(request.getIntervalSeconds()));
        banner.setStatus("1".equals(request.getStatus()) ? "1" : "0");
        return bannerRepository.save(banner);
    }

    public void deleteBanner(Long bannerId) {
        bannerRepository.deleteById(bannerId);
    }

    public void deleteVideo(Long videoId) {
        videoRepository.deleteById(videoId);
    }

    public Map<String, Object> stats() {
        Map<String, Object> result = new HashMap<>();
        result.put("userTotal", userRepository.count());
        result.put("postTotal", postRepository.count());
        result.put("commentTotal", commentRepository.count());
        result.put("activityTotal", activityRepository.count());
        result.put("noticeTotal", noticeRepository.count());
        result.put("newsTotal", newsRepository.count());
        result.put("videoTotal", videoRepository.count());
        result.put("bannerTotal", bannerRepository.count());
        result.put("bulletinTotal", communityBulletinRepository.count());
        result.put("signTotal", activitySignRepository.count());
        result.put("adminTotal", adminRepository.count());
        return result;
    }

    public Map<String, Object> operationAnalytics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trend7d", buildTrend7d());
        result.put("hotPosts", buildHotPosts());
        result.put("hotActivities", buildHotActivities());
        result.put("bulletinTypeRatio", communityBulletinService.adminTypeStats());
        return result;
    }

    private List<Map<String, Object>> buildTrend7d() {
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());
            row.put("newUsers", userRepository.countByCreateTimeBetween(start, end));
            row.put("newPosts", postRepository.countByCreateTimeBetween(start, end));
            row.put("newActivities", activityRepository.countByCreateTimeBetween(start, end));
            row.put("newSigns", activitySignRepository.countBySignTimeBetween(start, end));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildHotPosts() {
        return postRepository.findTop10ByOrderByLikeCountDescCreateTimeDesc().stream()
                .map(post -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    long commentCount = commentRepository.countByPostId(post.getPostId());
                    row.put("postId", post.getPostId());
                    row.put("title", post.getTitle());
                    row.put("likeCount", post.getLikeCount());
                    row.put("collectCount", post.getCollectCount());
                    row.put("commentCount", commentCount);
                    row.put("hotScore", (post.getLikeCount() == null ? 0 : post.getLikeCount()) * 2
                            + (post.getCollectCount() == null ? 0 : post.getCollectCount())
                            + commentCount);
                    row.put("createTime", post.getCreateTime());
                    return row;
                })
                .sorted((a, b) -> Long.compare(
                        ((Number) b.get("hotScore")).longValue(),
                        ((Number) a.get("hotScore")).longValue()))
                .toList();
    }

    private List<Map<String, Object>> buildHotActivities() {
        return activityRepository.findTop10ByOrderBySignNumDescCreateTimeDesc().stream()
                .map(activity -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("activityId", activity.getActivityId());
                    row.put("title", activity.getTitle());
                    row.put("signNum", activity.getSignNum() == null ? 0 : activity.getSignNum());
                    row.put("maxNum", activity.getMaxNum());
                    row.put("activityStatus", activity.getActivityStatus());
                    row.put("reviewStatus", activity.getReviewStatus());
                    row.put("createTime", activity.getCreateTime());
                    return row;
                })
                .toList();
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 0;
        }
        return page - 1;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 50);
    }

    private BigDecimal defaultScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score;
    }

    private int defaultSortNum(Integer sortNum) {
        return sortNum == null ? 0 : sortNum;
    }

    private int defaultIntervalSeconds(Integer intervalSeconds) {
        if (intervalSeconds == null) {
            return 5;
        }
        if (intervalSeconds < 2) {
            return 2;
        }
        return Math.min(intervalSeconds, 60);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
