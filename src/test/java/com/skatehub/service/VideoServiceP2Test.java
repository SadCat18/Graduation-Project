package com.skatehub.service;

import com.skatehub.dao.VideoRepository;
import com.skatehub.pojo.Video;
import com.skatehub.pojo.video.VideoCreateRequest;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoServiceP2Test {

    @Test
    void createInfersCreatorSeriesTagsAndQualityScore() {
        VideoRepository videoRepository = mock(VideoRepository.class);
        when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> invocation.getArgument(0));
        VideoService service = new VideoService(videoRepository);
        VideoCreateRequest request = new VideoCreateRequest();
        request.setTitle("Ollie beginner course EP01");
        request.setUrl("/upload/ollie.mp4");
        request.setCover("/upload/ollie.jpg");
        request.setIntro("Step-by-step ollie tutorial with safety tips and practice drills.");

        Video video = service.create(new CurrentUser(9L, "USER", "creator"), request);

        assertThat(video.getCreatorSeries()).isEqualTo("Ollie beginner course");
        assertThat(video.getTrickTags()).contains("ollie", "beginner", "safety");
        assertThat(video.getQualityScore()).isGreaterThanOrEqualTo(70);
    }
}
