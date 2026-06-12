package com.skatehub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skatehub.controller.AdminController;
import com.skatehub.controller.PostController;
import com.skatehub.controller.PublicController;
import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.auth.RegisterRequest;
import com.skatehub.service.ActivityService;
import com.skatehub.service.AdminAuditLogService;
import com.skatehub.service.AdminService;
import com.skatehub.service.NewsAiDiagnosticService;
import com.skatehub.service.NewsAiReprocessService;
import com.skatehub.service.NewsFetchService;
import com.skatehub.service.NewsSyncTaskService;
import com.skatehub.service.PlaceReviewService;
import com.skatehub.service.PostService;
import com.skatehub.service.PublicContentService;
import com.skatehub.service.ReportService;
import com.skatehub.service.VideoService;
import com.skatehub.config.NewsSyncProperties;
import com.skatehub.util.JwtTokenService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PublicController.class, PostController.class, AdminController.class})
@AutoConfigureMockMvc(addFilters = false)
class InputValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    @MockBean
    private PostService postService;
    @MockBean
    private ActivityService activityService;
    @MockBean
    private PublicContentService publicContentService;
    @MockBean
    private AdminService adminService;
    @MockBean
    private AdminAuditLogService adminAuditLogService;
    @MockBean
    private NewsFetchService newsFetchService;
    @MockBean
    private NewsAiDiagnosticService newsAiDiagnosticService;
    @MockBean
    private NewsAiReprocessService newsAiReprocessService;
    @MockBean
    private NewsSyncTaskService newsSyncTaskService;
    @MockBean
    private NewsSyncProperties newsSyncProperties;
    @MockBean
    private VideoService videoService;
    @MockBean
    private ReportService reportService;
    @MockBean
    private PlaceReviewService placeReviewService;
    @MockBean
    private JwtTokenService jwtTokenService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private AdminRepository adminRepository;

    @Test
    void rejectsNonPositivePathVariableIds() throws Exception {
        mockMvc.perform(get("/api/public/posts/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(postService, never()).detail(0L);
    }

    @Test
    void rejectsInvalidPageAndOversizedPageSize() throws Exception {
        mockMvc.perform(get("/api/public/posts").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/public/posts").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(postService, never()).list(null, null, null, 0, null);
        verify(postService, never()).list(null, null, null, null, 51);
    }

    @Test
    void rejectsInvalidCategoryAndOversizedTextInPostRequest() throws Exception {
        Map<String, Object> request = Map.of(
                "title", "A".repeat(101),
                "content", "valid content",
                "category", "bad-category"
        );

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rejectsInvalidStatusValuesBeforeServiceLayer() throws Exception {
        mockMvc.perform(put("/api/admin/users/1/status").param("status", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(adminService, never()).updateUserStatus(1L, "2");
    }

    @Test
    void registerUsernameKeepsLengthAndCharacterFormatValidation() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("bad name!");
        request.setPhone("13800138000");
        request.setPassword("abc12345");

        Set<String> fields = validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());

        org.assertj.core.api.Assertions.assertThat(fields).contains("username");
    }
}
