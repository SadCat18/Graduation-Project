package com.skatehub.service;

import com.skatehub.util.BizException;
import com.skatehub.pojo.video.VideoCreateRequest;
import com.skatehub.pojo.Video;
import com.skatehub.dao.VideoRepository;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

    public Video create(CurrentUser currentUser, VideoCreateRequest request) {
        Video video = new Video();
        video.setUserId(currentUser.id());
        video.setTitle(request.getTitle());
        video.setCover(request.getCover());
        video.setUrl(request.getUrl());
        video.setIntro(request.getIntro());
        return videoRepository.save(video);
    }

    public void delete(CurrentUser currentUser, Long videoId, boolean isAdmin) {
        Video video = videoRepository.findById(videoId).orElseThrow(() -> new BizException("视频不存在"));
        if (!isAdmin && !video.getUserId().equals(currentUser.id())) {
            throw new BizException("无权限删除视频");
        }
        videoRepository.delete(video);
    }
}
