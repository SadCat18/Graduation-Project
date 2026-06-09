package com.skatehub.service;

import com.skatehub.dao.*;
import com.skatehub.pojo.*;
import com.skatehub.pojo.admin.AdminCommentContextResponse;
import com.skatehub.pojo.admin.AdminCommentResponse;
import com.skatehub.pojo.admin.AdminNewsResponse;
import com.skatehub.pojo.admin.AdminPostDetailResponse;
import com.skatehub.pojo.admin.AdminPostResponse;
import com.skatehub.pojo.admin.BannerSaveRequest;
import com.skatehub.pojo.admin.CommunityBulletinReviewRequest;
import com.skatehub.pojo.admin.NewsSaveRequest;
import com.skatehub.pojo.admin.NoticeSaveRequest;
import com.skatehub.pojo.admin.PlaceSaveRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.NewsStatus;
import com.skatehub.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ReportRecordRepository reportRecordRepository;

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

    public PageResult<AdminPostResponse> listPosts(Integer page, Integer size) {
        return listPosts(page, size, null, null, "all", "latest");
    }

    public PageResult<AdminPostResponse> listPosts(Integer page,
                                                   Integer size,
                                                   String keyword,
                                                   String category,
                                                   String top,
                                                   String sort) {
        Page<Post> result = postRepository.searchAdminPosts(
                trimToNull(keyword),
                trimToNull(category),
                normalizePostTop(top),
                normalizePostSort(sort),
                PageRequest.of(normalizePage(page), normalizeSize(size))
        );
        return new PageResult<>(result.getTotalElements(), buildAdminPostResponses(result.getContent()));
    }

    public AdminPostDetailResponse postDetail(Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new BizException("帖子不存在"));
        AdminPostResponse response = buildAdminPostResponses(List.of(post)).get(0);
        List<Comment> recentComments = commentRepository.findTop5ByPostIdOrderByCreateTimeDesc(postId);
        Map<Long, User> userMap = userRepository.findAllById(distinctIds(recentComments.stream()
                        .map(Comment::getUserId)
                        .toList()))
                .stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        return AdminPostDetailResponse.builder()
                .post(response)
                .imageList(AdminPostResponse.splitImages(post.getImages()))
                .recentComments(recentComments.stream()
                        .map(comment -> AdminPostDetailResponse.RecentComment.from(comment, userMap.get(comment.getUserId())))
                        .toList())
                .build();
    }

    public long topPosts(List<Long> postIds, String top) {
        List<Long> ids = distinctIds(postIds);
        if (ids.isEmpty()) {
            return 0;
        }
        List<Post> posts = postRepository.findAllById(ids);
        String value = "1".equals(top) ? "1" : "0";
        posts.forEach(post -> post.setIsTop(value));
        postRepository.saveAll(posts);
        return posts.size();
    }

    public long deletePosts(List<Long> postIds) {
        List<Long> ids = distinctIds(postIds);
        if (ids.isEmpty()) {
            return 0;
        }
        postRepository.deleteAllByIdInBatch(ids);
        return ids.size();
    }

    public PageResult<AdminCommentResponse> listComments(Integer page, Integer size) {
        return listComments(page, size, null, null, null, "all", "latest");
    }

    public PageResult<AdminCommentResponse> listComments(Integer page,
                                                         Integer size,
                                                         String keyword,
                                                         Long postId,
                                                         Long userId,
                                                         String type,
                                                         String sort) {
        Sort.Direction direction = "oldest".equals(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(direction, "createTime").and(Sort.by(direction, "commentId"))
        );
        Page<Comment> result = commentRepository.searchAdminComments(
                trimToNull(keyword),
                postId,
                userId,
                normalizeCommentType(type),
                pageRequest
        );
        return new PageResult<>(result.getTotalElements(), buildAdminCommentResponses(result.getContent()));
    }

    public AdminCommentContextResponse commentContext(Long commentId) {
        Comment current = commentRepository.findById(commentId).orElseThrow(() -> new BizException("评论不存在"));
        Post post = postRepository.findById(current.getPostId()).orElse(null);
        Comment parent = hasParent(current)
                ? commentRepository.findById(current.getParentId()).orElse(null)
                : null;
        List<Comment> nearby = commentRepository.findTop20ByPostIdOrderByCreateTimeAsc(current.getPostId());
        Map<Long, AdminCommentResponse> nearbyMap = buildAdminCommentResponses(nearby).stream()
                .collect(Collectors.toMap(AdminCommentResponse::getCommentId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        AdminCommentResponse currentResponse = nearbyMap.get(current.getCommentId());
        if (currentResponse == null) {
            currentResponse = buildAdminCommentResponses(List.of(current)).get(0);
        }
        AdminCommentResponse parentResponse = null;
        if (parent != null) {
            parentResponse = nearbyMap.get(parent.getCommentId());
            if (parentResponse == null) {
                parentResponse = buildAdminCommentResponses(List.of(parent)).get(0);
            }
        }
        return AdminCommentContextResponse.builder()
                .postId(current.getPostId())
                .postTitle(post == null ? "帖子已删除" : post.getTitle())
                .postContentPreview(preview(post == null ? "" : post.getContent(), 160))
                .postCreateTime(post == null ? null : post.getCreateTime())
                .currentComment(currentResponse)
                .parentComment(parentResponse)
                .nearbyComments(new ArrayList<>(nearbyMap.values()))
                .build();
    }

    public long deleteComments(List<Long> commentIds) {
        List<Long> ids = distinctIds(commentIds);
        if (ids.isEmpty()) {
            return 0;
        }
        commentRepository.deleteAllByIdInBatch(ids);
        return ids.size();
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

    public List<AdminNewsResponse> listNews() {
        return newsRepository.findAllByOrderByCreateTimeDesc().stream()
                .map(AdminNewsResponse::from)
                .toList();
    }

    public AdminNewsResponse newsDetail(Long newsId) {
        News news = newsRepository.findById(newsId).orElseThrow(() -> new BizException("资讯不存在"));
        return AdminNewsResponse.from(news);
    }

    public News createNews(CurrentUser currentUser, NewsSaveRequest request) {
        News news = new News();
        fillNewsFields(news, request);
        news.setAdminId(currentUser.id());
        news.setStatus(firstNonBlank(request.getStatus(), NewsStatus.APPROVED));
        news.setSyncTime(LocalDateTime.now());
        return newsRepository.save(news);
    }

    public News updateNews(Long newsId, NewsSaveRequest request) {
        News news = newsRepository.findById(newsId).orElseThrow(() -> new BizException("资讯不存在"));
        fillNewsFields(news, request);
        if (StringUtils.hasText(request.getStatus()) && NewsStatus.isValid(request.getStatus().trim())) {
            news.setStatus(request.getStatus().trim());
        }
        return newsRepository.save(news);
    }

    public News reviewNews(Long newsId, String status) {
        News news = newsRepository.findById(newsId).orElseThrow(() -> new BizException("资讯不存在"));
        if (!StringUtils.hasText(status) || !NewsStatus.isValid(status.trim())) {
            throw new BizException("资讯审核状态不合法");
        }
        news.setStatus(status.trim());
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

    private List<AdminCommentResponse> buildAdminCommentResponses(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Post> postMap = postRepository.findAllById(distinctIds(comments.stream()
                        .map(Comment::getPostId)
                        .toList()))
                .stream()
                .collect(Collectors.toMap(Post::getPostId, Function.identity()));
        Map<Long, User> userMap = userRepository.findAllById(distinctIds(comments.stream()
                        .map(Comment::getUserId)
                        .toList()))
                .stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, Comment> parentMap = commentRepository.findAllById(distinctIds(comments.stream()
                        .filter(this::hasParent)
                        .map(Comment::getParentId)
                        .toList()))
                .stream()
                .collect(Collectors.toMap(Comment::getCommentId, Function.identity()));
        return comments.stream()
                .map(comment -> AdminCommentResponse.from(
                        comment,
                        postMap.get(comment.getPostId()),
                        userMap.get(comment.getUserId()),
                        parentMap.get(comment.getParentId())
                ))
                .toList();
    }

    private List<AdminPostResponse> buildAdminPostResponses(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, User> userMap = userRepository.findAllById(distinctIds(posts.stream()
                        .map(Post::getUserId)
                        .toList()))
                .stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        return posts.stream()
                .map(post -> AdminPostResponse.from(
                        post,
                        userMap.get(post.getUserId()),
                        commentRepository.countByPostId(post.getPostId()),
                        reportRecordRepository.countByTargetTypeAndTargetId("POST", post.getPostId())
                ))
                .toList();
    }

    private boolean hasParent(Comment comment) {
        return comment != null && comment.getParentId() != null && comment.getParentId() != 0L;
    }

    private String normalizeCommentType(String type) {
        if (!StringUtils.hasText(type)) {
            return "all";
        }
        String value = type.trim();
        if ("root".equals(value) || "reply".equals(value)) {
            return value;
        }
        return "all";
    }

    private String normalizePostTop(String top) {
        if (!StringUtils.hasText(top)) {
            return "all";
        }
        String value = top.trim();
        if ("top".equals(value) || "normal".equals(value)) {
            return value;
        }
        return "all";
    }

    private String normalizePostSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return "latest";
        }
        String value = sort.trim();
        if ("likes".equals(value) || "comments".equals(value) || "collects".equals(value)) {
            return value;
        }
        return "latest";
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
    }

    private String preview(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
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

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return trimToEmpty(fallback);
    }

    private void fillNewsFields(News news, NewsSaveRequest request) {
        news.setTitle(trimToEmpty(request.getTitle()));
        news.setContent(trimToEmpty(request.getContent()));
        news.setSummary(trimToNull(request.getSummary()));
        news.setCategory(trimToNull(request.getCategory()));
        news.setCover(trimToNull(request.getCover()));
        news.setSourceName(trimToNull(request.getSourceName()));
        news.setSourceUrl(trimToNull(request.getSourceUrl()));
        news.setOriginTitle(firstNonBlank(request.getOriginTitle(), request.getTitle()));
        news.setOriginContent(firstNonBlank(request.getOriginContent(), request.getContent()));
        news.setOriginSummary(trimToNull(request.getOriginSummary()));
        news.setAiTitle(trimToNull(request.getAiTitle()));
        news.setAiSummary(trimToNull(request.getAiSummary()));
        news.setAiCategory(trimToNull(request.getAiCategory()));
        news.setAiTranslatedContent(trimToNull(request.getAiTranslatedContent()));
    }
}
