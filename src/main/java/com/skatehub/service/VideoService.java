package com.skatehub.service;

import com.skatehub.util.BizException;
import com.skatehub.pojo.video.VideoCreateRequest;
import com.skatehub.pojo.Video;
import com.skatehub.dao.VideoRepository;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

    public Video create(CurrentUser currentUser, VideoCreateRequest request) {
        Long currentUserId = requireCurrentUserId(currentUser);
        Video video = new Video();
        video.setUserId(currentUserId);
        video.setTitle(request.getTitle());
        video.setCover(request.getCover());
        video.setUrl(request.getUrl());
        video.setIntro(request.getIntro());
        video.setCreatorSeries(resolveSeries(request));
        video.setTrickTags(resolveTags(request));
        video.setQualityScore(qualityScore(request, video.getTrickTags(), video.getCreatorSeries()));
        return videoRepository.save(video);
    }

    public void delete(CurrentUser currentUser, Long videoId, boolean isAdmin) {
        Long currentUserId = requireCurrentUserId(currentUser);
        boolean adminMode = requireAdminModeIfRequested(currentUser, isAdmin);
        InputValidator.positiveId(videoId, "视频ID");
        Video video = videoRepository.findById(videoId).orElseThrow(() -> new BizException("视频不存在"));
        if (!adminMode && !video.getUserId().equals(currentUserId)) {
            throw new BizException("无权限删除视频");
        }
        videoRepository.delete(video);
    }

    private Long requireCurrentUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser.id();
    }

    private boolean requireAdminModeIfRequested(CurrentUser currentUser, boolean isAdmin) {
        boolean adminMode = isAdmin && currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.role());
        if (isAdmin && !adminMode) {
            throw new BizException("无管理员操作权限");
        }
        return adminMode;
    }

    private String resolveSeries(VideoCreateRequest request) {
        if (StringUtils.hasText(request.getCreatorSeries())) {
            return trimToLength(request.getCreatorSeries(), 100);
        }
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        String inferred = title.replaceAll("(?i)\\s*(ep|episode|第)\\s*\\d+.*$", "").trim();
        return trimToLength(inferred, 100);
    }

    private String resolveTags(VideoCreateRequest request) {
        if (StringUtils.hasText(request.getTrickTags())) {
            return trimToLength(request.getTrickTags(), 255);
        }
        String text = ((request.getTitle() == null ? "" : request.getTitle()) + " "
                + (request.getIntro() == null ? "" : request.getIntro())).toLowerCase(Locale.ROOT);
        Set<String> tags = new LinkedHashSet<>();
        addTagIf(text, tags, "ollie", "ollie", "豚跳");
        addTagIf(text, tags, "beginner", "beginner", "starter", "入门", "新手");
        addTagIf(text, tags, "safety", "safety", "helmet", "protective", "护具", "安全");
        addTagIf(text, tags, "balance", "balance", "manual", "重心", "平衡");
        addTagIf(text, tags, "kickflip", "kickflip");
        return trimToLength(String.join(",", tags), 255);
    }

    private void addTagIf(String text, Set<String> tags, String tag, String... values) {
        for (String value : values) {
            if (text.contains(value.toLowerCase(Locale.ROOT))) {
                tags.add(tag);
                return;
            }
        }
    }

    private int qualityScore(VideoCreateRequest request, String tags, String series) {
        int score = 35;
        if (StringUtils.hasText(request.getCover())) score += 15;
        if (StringUtils.hasText(request.getIntro()) && request.getIntro().trim().length() >= 40) score += 20;
        if (StringUtils.hasText(tags)) score += 15;
        if (StringUtils.hasText(series)) score += 10;
        if (StringUtils.hasText(request.getUrl())) score += 5;
        return Math.min(100, score);
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
