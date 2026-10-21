package com.skatehub.service;

import com.skatehub.dao.BannerRepository;
import com.skatehub.dao.ActivityRepository;
import com.skatehub.dao.NewsRepository;
import com.skatehub.dao.NoticeRepository;
import com.skatehub.dao.PlaceRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.SkateTrickRepository;
import com.skatehub.dao.VideoRepository;
import com.skatehub.pojo.Activity;
import com.skatehub.pojo.Banner;
import com.skatehub.pojo.News;
import com.skatehub.pojo.Notice;
import com.skatehub.pojo.Place;
import com.skatehub.pojo.place.LocalReviewSummary;
import com.skatehub.pojo.place.PublicPlaceView;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.Video;
import com.skatehub.pojo.skate.SkateTrick;
import com.skatehub.util.ActivityReviewStatus;
import com.skatehub.util.ActivityStatus;
import com.skatehub.util.BizException;
import com.skatehub.util.InputValidator;
import com.skatehub.util.NewsStatus;
import com.skatehub.util.PlaceVerificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;

@Service
public class PublicContentService {

    private final NoticeRepository noticeRepository;
    private final NewsRepository newsRepository;
    private final PlaceRepository placeRepository;
    private final VideoRepository videoRepository;
    private final BannerRepository bannerRepository;
    private final ActivityRepository activityRepository;
    private final PostRepository postRepository;
    private final SkateTrickRepository skateTrickRepository;
    private final CommunityBulletinService communityBulletinService;
    private final PlaceReviewService placeReviewService;

    @Autowired
    public PublicContentService(NoticeRepository noticeRepository,
                                NewsRepository newsRepository,
                                PlaceRepository placeRepository,
                                VideoRepository videoRepository,
                                BannerRepository bannerRepository,
                                ActivityRepository activityRepository,
                                PostRepository postRepository,
                                SkateTrickRepository skateTrickRepository,
                                CommunityBulletinService communityBulletinService,
                                PlaceReviewService placeReviewService) {
        this.noticeRepository = noticeRepository;
        this.newsRepository = newsRepository;
        this.placeRepository = placeRepository;
        this.videoRepository = videoRepository;
        this.bannerRepository = bannerRepository;
        this.activityRepository = activityRepository;
        this.postRepository = postRepository;
        this.skateTrickRepository = skateTrickRepository;
        this.communityBulletinService = communityBulletinService;
        this.placeReviewService = placeReviewService;
    }

    public PublicContentService(NoticeRepository noticeRepository,
                                NewsRepository newsRepository,
                                PlaceRepository placeRepository,
                                VideoRepository videoRepository,
                                BannerRepository bannerRepository,
                                CommunityBulletinService communityBulletinService,
                                PlaceReviewService placeReviewService) {
        this(noticeRepository, newsRepository, placeRepository, videoRepository, bannerRepository,
                null, null, null, communityBulletinService, placeReviewService);
    }

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
        InputValidator.positiveId(bulletinId, "快讯ID");
        return communityBulletinService.publicDetail(bulletinId);
    }

    public List<News> news() {
        return newsRepository.findAllByStatusOrderBySyncTimeDescCreateTimeDesc(NewsStatus.APPROVED);
    }

    public News newsDetail(Long newsId) {
        InputValidator.positiveId(newsId, "资讯ID");
        return newsRepository.findByNewsIdAndStatus(newsId, NewsStatus.APPROVED)
                .orElseThrow(() -> new BizException("资讯不存在或未审核通过"));
    }

    public List<PublicPlaceView> places() {
        List<Place> places=placeRepository.findAllByOrderByScoreDescCreateTimeDesc().stream()
                .filter(place -> PlaceVerificationStatus.isApprovedForPublic(place.getVerificationStatus())).toList();
        Map<Long,LocalReviewSummary> summaries=placeReviewService.localReviewSummaries(places.stream().map(Place::getPlaceId).toList());
        return places.stream().map(place->new PublicPlaceView(place,summaries.get(place.getPlaceId())))
                .sorted(Comparator.comparing((PublicPlaceView view)->view.localReviewSummary().score(),Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(view->view.place().getCreateTime(),Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(view->view.place().getPlaceId()))
                .toList();
    }

    public Map<String, Object> placeDetail(Long placeId) {
        InputValidator.positiveId(placeId, "场地ID");
        Place place = placeRepository.findById(placeId).orElseThrow(() -> new BizException("场地不存在"));
        if (!PlaceVerificationStatus.isApprovedForPublic(place.getVerificationStatus())) {
            throw new BizException("Place is pending verification");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("placeId", place.getPlaceId());
        map.put("name", place.getName());
        map.put("address", place.getAddress());
        map.put("intro", place.getIntro());
        map.put("score", place.getScore());
        map.put("reviewCount", place.getReviewCount());
        map.put("createTime", place.getCreateTime());
        map.put("localReviewSummary", placeReviewService.localReviewSummaries(List.of(placeId)).get(placeId));
        map.put("latestReview", placeReviewService.latestReview(placeId));
        map.put("suitableLevel", suitableLevel(place));
        map.put("obstacleTags", obstacleTags(place));
        map.put("safetyTips", safetyTips(place));
        map.put("relatedVideos", relatedVideos(place));
        return map;
    }

    public List<Map<String, Object>> placeReviews(Long placeId) {
        InputValidator.positiveId(placeId, "场地ID");
        placeRepository.findById(placeId).orElseThrow(() -> new BizException("场地不存在"));
        return placeReviewService.listByPlace(placeId);
    }

    public List<Video> videos() {
        return videoRepository.findAllByOrderByCreateTimeDesc();
    }

    public Video videoDetail(Long videoId) {
        InputValidator.positiveId(videoId, "视频ID");
        return videoRepository.findById(videoId).orElseThrow(() -> new BizException("视频不存在"));
    }

    public List<Banner> banners() {
        return bannerRepository.findByStatusOrderBySortNumAscCreateTimeDesc("0");
    }

    public List<Map<String, Object>> search(String keyword, Integer limit) {
        String query = InputValidator.optionalSimpleText(keyword, 50, "搜索关键词");
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int max = limit == null ? 30 : Math.max(1, Math.min(limit, 50));
        String normalizedQuery = normalize(query);
        List<Map<String, Object>> results = new ArrayList<>();

        postRepository.findAll().stream()
                .filter(post -> containsAny(searchablePostText(post), normalizedQuery))
                .limit(8)
                .map(post -> searchResult("POST", post.getPostId(), post.getTitle(), post.getContent(),
                        "/community/post/" + post.getPostId(), post.getCreateTime()))
                .forEach(results::add);

        activityRepository.findAll().stream()
                .filter(this::isPublicActivity)
                .filter(activity -> containsAny(searchableActivityText(activity), normalizedQuery))
                .limit(8)
                .map(activity -> searchResult("ACTIVITY", activity.getActivityId(), activity.getTitle(),
                        firstText(activity.getActivityDesc(), activity.getContent()),
                        "/activities/" + activity.getActivityId(), activity.getCreateTime()))
                .forEach(results::add);

        places().stream().map(PublicPlaceView::place)
                .filter(place -> containsAny(searchableText(place), normalizedQuery))
                .limit(8)
                .map(place -> searchResult("PLACE", place.getPlaceId(), place.getName(), place.getIntro(),
                        "/places/" + place.getPlaceId(), place.getCreateTime()))
                .forEach(results::add);

        videos().stream()
                .filter(video -> containsAny(searchableVideoText(video), normalizedQuery))
                .limit(8)
                .map(video -> searchResult("VIDEO", video.getVideoId(), video.getTitle(), video.getIntro(),
                        "/videos/" + video.getVideoId(), video.getCreateTime()))
                .forEach(results::add);

        skateTrickRepository.findByStatusOrderBySortOrderAscTrickIdAsc("1").stream()
                .filter(trick -> containsAny(searchableTrickText(trick), normalizedQuery))
                .limit(8)
                .map(trick -> searchResult("TRICK", trick.getTrickId(), trick.getTrickName(), trick.getDescription(),
                        "/skate", trick.getUpdateTime() == null ? trick.getCreateTime() : trick.getUpdateTime()))
                .forEach(results::add);

        news().stream()
                .filter(news -> containsAny(searchableNewsText(news), normalizedQuery))
                .limit(8)
                .map(news -> searchResult("NEWS", news.getNewsId(), news.getTitle(), news.getSummary(),
                        "/news/" + news.getNewsId(), news.getCreateTime()))
                .forEach(results::add);

        return results.stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("createTime")), Comparator.reverseOrder()))
                .limit(max)
                .toList();
    }

    private String suitableLevel(Place place) {
        String text = searchableText(place);
        if (containsAny(text, "beginner", "starter", "flat", "newbie", "新手", "入门", "平地", "空旷")) {
            return "BEGINNER";
        }
        if (containsAny(text, "advanced", "expert", "进阶", "高阶", "台阶", "rail", "栏杆", "bowl", "碗池")) {
            return "ADVANCED";
        }
        return "INTERMEDIATE";
    }

    private List<String> obstacleTags(Place place) {
        String text = searchableText(place);
        Set<String> tags = new LinkedHashSet<>();
        addIf(text, tags, "flat", "flat", "平地", "广场", "空旷");
        addIf(text, tags, "ledge", "ledge", "台阶", "边台");
        addIf(text, tags, "rail", "rail", "栏杆", "扶手");
        addIf(text, tags, "bowl", "bowl", "pool", "碗池");
        addIf(text, tags, "stairs", "stairs", "台阶", "楼梯");
        if (tags.isEmpty()) {
            tags.add("open-space");
        }
        return List.copyOf(tags);
    }

    private List<String> safetyTips(Place place) {
        String text = searchableText(place);
        Set<String> tips = new LinkedHashSet<>();
        if (containsAny(text, "night", "夜", "灯")) {
            tips.add("Check lighting before night sessions.");
        }
        if (containsAny(text, "rail", "ledge", "stairs", "栏杆", "台阶", "楼梯")) {
            tips.add("Wear helmet and wrist guards before obstacle practice.");
        }
        tips.add("Check ground, weather, and pedestrian flow before skating.");
        return List.copyOf(tips);
    }

    private List<Video> relatedVideos(Place place) {
        String placeName = place == null ? "" : normalize(place.getName());
        Set<String> obstacleTags = new LinkedHashSet<>(obstacleTags(place));
        return videoRepository.findAllByOrderByCreateTimeDesc().stream()
                .filter(video -> isRelatedVideo(video, placeName, obstacleTags))
                .limit(4)
                .toList();
    }

    private boolean isRelatedVideo(Video video, String placeName, Set<String> tags) {
        String text = normalize((video == null ? "" : video.getTitle()) + " " + (video == null ? "" : video.getIntro()));
        if (placeName.length() >= 2 && text.contains(placeName)) {
            return true;
        }
        return tags.stream().anyMatch(tag -> tag.length() >= 3 && text.contains(tag));
    }

    private void addIf(String text, Set<String> tags, String tag, String... needles) {
        if (containsAny(text, needles)) {
            tags.add(tag);
        }
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private String searchableText(Place place) {
        if (place == null) {
            return "";
        }
        return normalize(String.join(" ",
                place.getName() == null ? "" : place.getName(),
                place.getAddress() == null ? "" : place.getAddress(),
                place.getIntro() == null ? "" : place.getIntro()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isPublicActivity(Activity activity) {
        if (activity == null || !ActivityReviewStatus.APPROVED.equals(activity.getReviewStatus())) {
            return false;
        }
        String status = activity.getActivityStatus() == null ? activity.getStatus() : activity.getActivityStatus();
        return ActivityStatus.SIGNUP_OPEN.equals(status) || ActivityStatus.FULL.equals(status) || ActivityStatus.ENDED.equals(status);
    }

    private Map<String, Object> searchResult(String type, Long id, String title, String summary, String routePath, Object createTime) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("id", id);
        map.put("title", title);
        map.put("summary", preview(summary));
        map.put("routePath", routePath);
        map.put("createTime", createTime);
        return map;
    }

    private String searchablePostText(Post post) {
        return normalize((post == null ? "" : post.getTitle()) + " " + (post == null ? "" : post.getContent()) + " " + (post == null ? "" : post.getCategory()));
    }

    private String searchableActivityText(Activity activity) {
        return normalize((activity == null ? "" : activity.getTitle()) + " "
                + (activity == null ? "" : activity.getContent()) + " "
                + (activity == null ? "" : activity.getActivityDesc()) + " "
                + (activity == null ? "" : activity.getPlace()) + " "
                + (activity == null ? "" : activity.getCity()) + " "
                + (activity == null ? "" : activity.getDistrict()));
    }

    private String searchableVideoText(Video video) {
        return normalize((video == null ? "" : video.getTitle()) + " " + (video == null ? "" : video.getIntro()));
    }

    private String searchableTrickText(SkateTrick trick) {
        return normalize((trick == null ? "" : trick.getTrickName()) + " " + (trick == null ? "" : trick.getCategory()) + " " + (trick == null ? "" : trick.getDescription()));
    }

    private String searchableNewsText(News news) {
        return normalize((news == null ? "" : news.getTitle()) + " " + (news == null ? "" : news.getSummary()) + " " + (news == null ? "" : news.getContent()));
    }

    private String preview(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= 140 ? text : text.substring(0, 140) + "...";
    }

    private String firstText(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
