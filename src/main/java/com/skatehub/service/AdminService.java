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
import com.skatehub.util.InputValidator;
import com.skatehub.util.ActivityReviewStatus;
import com.skatehub.util.ActivityStatus;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Set<String> BINARY_VALUES = Set.of("0", "1");
    private static final Set<String> NEWS_STATUSES = Set.of("0", "1", "2");
    private static final Set<String> ACTIVITY_REVIEW_STATUSES = Set.of("0", "1", "2");
    private static final Set<String> POST_TOP_FILTERS = Set.of("all", "top", "normal");
    private static final Set<String> POST_SORTS = Set.of("latest", "likes", "comments", "collects");
    private static final Set<String> COMMENT_TYPES = Set.of("all", "root", "reply");
    private static final Set<String> COMMENT_SORTS = Set.of("latest", "oldest");

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
        InputValidator.positiveId(userId, "用户ID");
        String normalizedStatus = InputValidator.requiredAllowed(status, BINARY_VALUES, "用户状态");
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        user.setStatus(normalizedStatus);
        return userRepository.save(user);
    }

    public User updateUserBulletinPermission(Long userId, String permission) {
        InputValidator.positiveId(userId, "用户ID");
        String normalizedPermission = InputValidator.requiredAllowed(permission, BINARY_VALUES, "快讯权限");
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        user.setBulletinPermission(normalizedPermission);
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
                InputValidator.likeKeyword(keyword, 50, "搜索关键词"),
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
        String value = InputValidator.requiredAllowed(top, BINARY_VALUES, "置顶值");
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
        String normalizedSort = InputValidator.optionalAllowed(sort, COMMENT_SORTS, "latest", "排序方式");
        Sort.Direction direction = "oldest".equals(normalizedSort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(direction, "createTime").and(Sort.by(direction, "commentId"))
        );
        Page<Comment> result = commentRepository.searchAdminComments(
                InputValidator.likeKeyword(keyword, 50, "搜索关键词"),
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
            String normalizedReviewStatus = InputValidator.requiredAllowed(reviewStatus, ACTIVITY_REVIEW_STATUSES, "审核状态");
            if (ActivityReviewStatus.PENDING.equals(normalizedReviewStatus)) {
                result = activityRepository.findByReviewStatusOrReviewStatusIsNullOrderByCreateTimeDesc(
                        normalizedReviewStatus,
                        pageRequest
                );
            } else {
                result = activityRepository.findByReviewStatusOrderByCreateTimeDesc(
                        normalizedReviewStatus,
                        pageRequest
                );
            }
        }
        result.getContent().forEach(this::normalizeActivityReviewFields);
        return new PageResult<>(result.getTotalElements(), result.getContent());
    }

    private void normalizeActivityReviewFields(Activity activity) {
        if (activity == null) {
            return;
        }
        if (!StringUtils.hasText(activity.getReviewStatus())) {
            activity.setReviewStatus(ActivityReviewStatus.PENDING);
        }
        if (!StringUtils.hasText(activity.getActivityStatus())) {
            activity.setActivityStatus(ActivityStatus.PENDING_REVIEW);
        }
        if (!StringUtils.hasText(activity.getStatus())) {
            activity.setStatus(ActivityStatus.PENDING_REVIEW);
        }
    }

    public List<Notice> listNotices() {
        return noticeRepository.findAllByOrderByCreateTimeDesc();
    }

    public Notice createNotice(CurrentUser currentUser, NoticeSaveRequest request) {
        Notice notice = new Notice();
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setAdminId(currentUser.id());
        notice.setStatus(InputValidator.optionalAllowed(request.getStatus(), BINARY_VALUES, "0", "公告状态"));
        return noticeRepository.save(notice);
    }

    public Notice updateNotice(Long noticeId, NoticeSaveRequest request) {
        Notice notice = noticeRepository.findById(noticeId).orElseThrow(() -> new BizException("公告不存在"));
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setStatus(InputValidator.optionalAllowed(request.getStatus(), BINARY_VALUES, "0", "公告状态"));
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
        news.setStatus(InputValidator.optionalAllowed(request.getStatus(), NEWS_STATUSES, NewsStatus.APPROVED, "资讯状态"));
        news.setSyncTime(LocalDateTime.now());
        return newsRepository.save(news);
    }

    public News updateNews(Long newsId, NewsSaveRequest request) {
        News news = newsRepository.findById(newsId).orElseThrow(() -> new BizException("资讯不存在"));
        fillNewsFields(news, request);
        if (StringUtils.hasText(request.getStatus())) {
            news.setStatus(InputValidator.requiredAllowed(request.getStatus(), NEWS_STATUSES, "资讯状态"));
        }
        return newsRepository.save(news);
    }

    public News reviewNews(Long newsId, String status) {
        News news = newsRepository.findById(newsId).orElseThrow(() -> new BizException("资讯不存在"));
        if (!StringUtils.hasText(status) || !NewsStatus.isValid(status.trim())) {
            throw new BizException("资讯审核状态不合法");
        }
        news.setStatus(InputValidator.requiredAllowed(status, NEWS_STATUSES, "资讯审核状态"));
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
        banner.setStatus(InputValidator.optionalAllowed(request.getStatus(), BINARY_VALUES, "0", "轮播状态"));
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
        banner.setStatus(InputValidator.optionalAllowed(request.getStatus(), BINARY_VALUES, "0", "轮播状态"));
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
        return InputValidator.optionalAllowed(type, COMMENT_TYPES, "all", "评论类型");
    }

    private String normalizePostTop(String top) {
        return InputValidator.optionalAllowed(top, POST_TOP_FILTERS, "all", "置顶筛选值");
    }

    private String normalizePostSort(String sort) {
        return InputValidator.optionalAllowed(sort, POST_SORTS, "latest", "排序方式");
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .peek(id -> InputValidator.positiveId(id, "ID"))
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
        return InputValidator.pageIndex(page);
    }

    private int normalizeSize(Integer size) {
        return InputValidator.size(size);
    }

    private BigDecimal defaultScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score;
    }

    private int defaultSortNum(Integer sortNum) {
        if (sortNum == null) {
            return 0;
        }
        if (sortNum < 0 || sortNum > 9999) {
            throw new BizException("排序值不合法");
        }
        return sortNum;
    }

    private int defaultIntervalSeconds(Integer intervalSeconds) {
        if (intervalSeconds == null) {
            return 5;
        }
        if (intervalSeconds < 2 || intervalSeconds > 60) {
            throw new BizException("轮播间隔不合法");
        }
        return intervalSeconds;
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
