package com.skatehub.controller;

import com.skatehub.config.SecurityConfig;
import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.growth.BadgeView;
import com.skatehub.pojo.growth.CheckinCalendarResponse;
import com.skatehub.pojo.growth.CheckinResponse;
import com.skatehub.pojo.growth.CheckinStatusResponse;
import com.skatehub.pojo.growth.LeaderboardEntry;
import com.skatehub.pojo.growth.LeaderboardResponse;
import com.skatehub.service.BadgeService;
import com.skatehub.service.CheckinService;
import com.skatehub.service.LeaderboardService;
import com.skatehub.service.PlaceReviewService;
import com.skatehub.service.RecommendationService;
import com.skatehub.service.ReportService;
import com.skatehub.service.UserService;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.JwtAuthenticationFilter;
import com.skatehub.util.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserGrowthControllerTest {

    private static final Long USER_ID = 73L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private ReportService reportService;
    @MockBean
    private PlaceReviewService placeReviewService;
    @MockBean
    private RecommendationService recommendationService;
    @MockBean
    private CheckinService checkinService;
    @MockBean
    private LeaderboardService leaderboardService;
    @MockBean
    private BadgeService badgeService;
    @MockBean
    private JwtTokenService jwtTokenService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private AdminRepository adminRepository;

    @Test
    void anonymousCheckinIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/user/checkin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(checkinService);
    }

    @Test
    void anonymousCheckinStatusIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/checkin/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(checkinService);
    }

    @Test
    void anonymousCheckinCalendarIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/checkin/calendar").param("month", "2026-06"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(checkinService);
    }

    @Test
    void anonymousLeaderboardIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/leaderboard").param("type", "total"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(leaderboardService);
    }

    @Test
    void anonymousBadgesAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/badges"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(badgeService);
    }

    @Test
    void checkinUsesAuthenticatedUserAndSerializesResponse() throws Exception {
        CheckinResponse response = new CheckinResponse(
                false, LocalDate.of(2026, 6, 18), 4, 12, 5, 20, 125);
        when(checkinService.checkin(USER_ID)).thenReturn(response);

        mockMvc.perform(post("/api/user/checkin").with(currentUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.alreadyCheckedIn").value(false))
                .andExpect(jsonPath("$.data.checkinDate").value("2026-06-18"))
                .andExpect(jsonPath("$.data.streakDays").value(4))
                .andExpect(jsonPath("$.data.totalDays").value(12))
                .andExpect(jsonPath("$.data.baseExp").value(5))
                .andExpect(jsonPath("$.data.bonusExp").value(20))
                .andExpect(jsonPath("$.data.totalExp").value(125));

        verify(checkinService).checkin(USER_ID);
    }

    @Test
    void statusUsesAuthenticatedUserAndSerializesResponse() throws Exception {
        when(checkinService.status(USER_ID)).thenReturn(new CheckinStatusResponse(true, 8, 31, 240));

        mockMvc.perform(get("/api/user/checkin/status").with(currentUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.checkedInToday").value(true))
                .andExpect(jsonPath("$.data.streakDays").value(8))
                .andExpect(jsonPath("$.data.totalDays").value(31))
                .andExpect(jsonPath("$.data.totalExp").value(240));

        verify(checkinService).status(USER_ID);
    }

    @Test
    void calendarParsesMonthAndUsesAuthenticatedUser() throws Exception {
        CheckinCalendarResponse response = new CheckinCalendarResponse(
                "2026-06", List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 18)));
        when(checkinService.calendar(USER_ID, YearMonth.of(2026, 6))).thenReturn(response);

        mockMvc.perform(get("/api/user/checkin/calendar")
                        .param("month", "2026-06")
                        .with(currentUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.month").value("2026-06"))
                .andExpect(jsonPath("$.data.checkedDates[0]").value("2026-06-01"))
                .andExpect(jsonPath("$.data.checkedDates[1]").value("2026-06-18"));

        verify(checkinService).calendar(USER_ID, YearMonth.of(2026, 6));
    }

    @Test
    void invalidCalendarMonthIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/api/user/checkin/calendar")
                        .param("month", "2026-13")
                        .with(currentUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/user/checkin/calendar")
                        .param("month", "2026-6")
                        .with(currentUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(checkinService);
    }

    @Test
    void leaderboardUsesRequestedPaginationAndAuthenticatedUser() throws Exception {
        LeaderboardEntry entry = new LeaderboardEntry(1, 11L, "leader", "/avatar.png", 420, 6);
        LeaderboardEntry current = new LeaderboardEntry(9, USER_ID, "alice", null, 180, 3);
        when(leaderboardService.list("weekly", 2, 5, USER_ID))
                .thenReturn(new LeaderboardResponse(21, List.of(entry), current));

        mockMvc.perform(get("/api/user/leaderboard")
                        .param("type", "weekly")
                        .param("page", "2")
                        .param("size", "5")
                        .with(currentUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.list[0].rankNo").value(1))
                .andExpect(jsonPath("$.data.list[0].username").value("leader"))
                .andExpect(jsonPath("$.data.currentUser.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.currentUser.rankNo").value(9));

        verify(leaderboardService).list("weekly", 2, 5, USER_ID);
    }

    @Test
    void leaderboardDefaultsToFirstPageWithTwentyEntries() throws Exception {
        when(leaderboardService.list("total", 1, 20, USER_ID))
                .thenReturn(new LeaderboardResponse(0, List.of(), null));

        mockMvc.perform(get("/api/user/leaderboard")
                        .param("type", "total")
                        .with(currentUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(leaderboardService).list("total", 1, 20, USER_ID);
    }

    @Test
    void invalidLeaderboardTypeIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/api/user/leaderboard")
                        .param("type", "TOTAL")
                        .with(currentUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(leaderboardService);
    }

    @Test
    void nonPositiveLeaderboardPaginationIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/api/user/leaderboard")
                        .param("type", "total")
                        .param("page", "0")
                        .with(currentUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/user/leaderboard")
                        .param("type", "total")
                        .param("size", "-1")
                        .with(currentUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(leaderboardService, never()).list("total", 0, 20, USER_ID);
        verify(leaderboardService, never()).list("total", 1, -1, USER_ID);
        verifyNoInteractions(leaderboardService);
    }

    @Test
    void badgesUseAuthenticatedUserAndSerializeList() throws Exception {
        BadgeView badge = new BadgeView(
                "FIRST_CHECKIN", "First Check-in", "Check in once", "COMMON", "calendar",
                true, LocalDateTime.of(2026, 6, 18, 9, 30), 1, 1);
        when(badgeService.evaluateAndList(USER_ID)).thenReturn(List.of(badge));

        mockMvc.perform(get("/api/user/badges").with(currentUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].code").value("FIRST_CHECKIN"))
                .andExpect(jsonPath("$.data[0].unlocked").value(true))
                .andExpect(jsonPath("$.data[0].unlockTime").value("2026-06-18T09:30:00"))
                .andExpect(jsonPath("$.data[0].current").value(1))
                .andExpect(jsonPath("$.data[0].target").value(1));

        verify(badgeService).evaluateAndList(USER_ID);
    }

    private RequestPostProcessor currentUser() {
        CurrentUser principal = new CurrentUser(USER_ID, "USER", "alice", 3);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
