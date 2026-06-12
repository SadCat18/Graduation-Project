package com.skatehub.security;

import com.skatehub.config.NewsSyncProperties;
import com.skatehub.controller.AdminController;
import com.skatehub.service.ActivityService;
import com.skatehub.service.AdminAuditLogService;
import com.skatehub.service.AdminService;
import com.skatehub.service.NewsAiDiagnosticService;
import com.skatehub.service.NewsAiReprocessService;
import com.skatehub.service.NewsFetchService;
import com.skatehub.service.NewsSyncTaskService;
import com.skatehub.service.PlaceReviewService;
import com.skatehub.service.PostService;
import com.skatehub.service.ReportService;
import com.skatehub.service.VideoService;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminControllerAuditTest {

    private final AdminService adminService = mock(AdminService.class);
    private final NewsFetchService newsFetchService = mock(NewsFetchService.class);
    private final NewsAiDiagnosticService newsAiDiagnosticService = mock(NewsAiDiagnosticService.class);
    private final NewsAiReprocessService newsAiReprocessService = mock(NewsAiReprocessService.class);
    private final NewsSyncTaskService newsSyncTaskService = mock(NewsSyncTaskService.class);
    private final NewsSyncProperties newsSyncProperties = mock(NewsSyncProperties.class);
    private final PostService postService = mock(PostService.class);
    private final ActivityService activityService = mock(ActivityService.class);
    private final VideoService videoService = mock(VideoService.class);
    private final ReportService reportService = mock(ReportService.class);
    private final PlaceReviewService placeReviewService = mock(PlaceReviewService.class);
    private final AdminAuditLogService adminAuditLogService = mock(AdminAuditLogService.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminPostDeleteWritesAuditLog() {
        CurrentUser currentUser = new CurrentUser(1L, "ADMIN", "admin");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
        AdminController controller = new AdminController(
                adminService,
                newsFetchService,
                newsAiDiagnosticService,
                newsAiReprocessService,
                newsSyncTaskService,
                newsSyncProperties,
                postService,
                activityService,
                videoService,
                reportService,
                placeReviewService,
                adminAuditLogService
        );

        controller.deletePost(9L);

        verify(postService).delete(currentUser, 9L, true);
        verify(adminAuditLogService).record(1L, "POST", 9L, "DELETE", "SUCCESS");
    }
}
