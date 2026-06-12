package com.skatehub.service;

import com.skatehub.util.BizException;
import com.skatehub.pojo.video.VideoCreateRequest;
import com.skatehub.pojo.Video;
import com.skatehub.dao.VideoRepository;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
