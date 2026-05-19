package com.javademo1.service;

import com.javademo1.util.BizException;
import com.javademo1.pojo.video.VideoCreateRequest;
import com.javademo1.pojo.Video;
import com.javademo1.dao.VideoRepository;
import com.javademo1.util.CurrentUser;
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

