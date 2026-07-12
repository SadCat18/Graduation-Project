package com.skatehub.service;

import com.skatehub.dao.BannerRepository;
import com.skatehub.dao.NewsRepository;
import com.skatehub.dao.NoticeRepository;
import com.skatehub.dao.PlaceRepository;
import com.skatehub.dao.VideoRepository;
import com.skatehub.pojo.Place;
import com.skatehub.pojo.Video;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicContentServiceTest {

    @Test
    void placeDetailReturnsStructuredSpotGuidanceAndRelatedVideos() {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        VideoRepository videoRepository = mock(VideoRepository.class);
        PlaceReviewService placeReviewService = mock(PlaceReviewService.class);
        PublicContentService service = new PublicContentService(
                mock(NoticeRepository.class),
                mock(NewsRepository.class),
                placeRepository,
                videoRepository,
                mock(BannerRepository.class),
                mock(CommunityBulletinService.class),
                placeReviewService
        );

        Place place = new Place();
        place.setPlaceId(3L);
        place.setName("West Plaza");
        place.setAddress("Campus west gate");
        place.setIntro("Beginner friendly flat ground with ledge and rail. Wear helmet and wrist guards at night.");
        when(placeRepository.findById(3L)).thenReturn(Optional.of(place));

        Video matching = new Video();
        matching.setVideoId(8L);
        matching.setTitle("West Plaza ledge line");
        matching.setIntro("How to practice ledge balance at West Plaza.");
        Video unrelated = new Video();
        unrelated.setVideoId(9L);
        unrelated.setTitle("Bowl carving");
        unrelated.setIntro("Transition practice.");
        when(videoRepository.findAllByOrderByCreateTimeDesc()).thenReturn(List.of(unrelated, matching));
        when(placeReviewService.latestReview(3L)).thenReturn(Map.of("content", "smooth ground"));

        Map<String, Object> detail = service.placeDetail(3L);

        assertThat(detail.get("suitableLevel")).isEqualTo("BEGINNER");
        assertThat((List<String>) detail.get("obstacleTags")).contains("flat", "ledge", "rail");
        assertThat((List<String>) detail.get("safetyTips")).isNotEmpty();
        assertThat((List<Video>) detail.get("relatedVideos")).containsExactly(matching);
    }
}
