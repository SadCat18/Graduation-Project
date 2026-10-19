package com.skatehub.service;

import com.skatehub.dao.*;
import com.skatehub.pojo.CommunityBulletin;
import com.skatehub.pojo.ReportRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceDashboardTest {

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private NoticeRepository noticeRepository;
    @Mock private NewsRepository newsRepository;
    @Mock private PlaceRepository placeRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private BannerRepository bannerRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private ActivitySignRepository activitySignRepository;
    @Mock private CommunityBulletinService communityBulletinService;
    @Mock private CommunityBulletinRepository communityBulletinRepository;
    @Mock private ReportRecordRepository reportRecordRepository;
    @Mock private ProductAnalyticsService productAnalyticsService;

    @InjectMocks private AdminService adminService;

    @Test
    void operationAnalyticsIncludesComparisonWindowAndHandlingEfficiency() {
        LocalDateTime now = LocalDateTime.now();
        ReportRecord report = new ReportRecord();
        report.setCreateTime(now.minusHours(4));
        report.setHandleTime(now.minusHours(2));
        CommunityBulletin bulletin = new CommunityBulletin();
        bulletin.setCreateTime(now.minusHours(7));
        bulletin.setReviewTime(now.minusHours(3));

        when(productAnalyticsService.summary7d()).thenReturn(Map.of("trend7d", List.of()));
        when(productAnalyticsService.summary30d()).thenReturn(Map.of("trend30d", List.of()));
        when(reportRecordRepository.findByHandleTimeGreaterThanEqual(any())).thenReturn(List.of(report));
        when(communityBulletinRepository.findByReviewTimeGreaterThanEqual(any())).thenReturn(List.of(bulletin));

        Map<String, Object> result = adminService.operationAnalytics();

        assertEquals(7, ((List<?>) result.get("trend7d")).size());
        assertEquals(7, ((List<?>) result.get("previousTrend7d")).size());
        assertEquals(30, ((List<?>) result.get("trend30d")).size());
        assertEquals(30, ((List<?>) result.get("previousTrend30d")).size());
        assertTrue(result.containsKey("activityConversion30d"));
        assertTrue(result.containsKey("handlingEfficiency"));
        @SuppressWarnings("unchecked")
        Map<String, Object> efficiency = (Map<String, Object>) result.get("handlingEfficiency");
        assertEquals(3.0D, efficiency.get("averageHours"));
        assertEquals(2, efficiency.get("sampleSize"));
        assertEquals(30, efficiency.get("windowDays"));
    }
}
