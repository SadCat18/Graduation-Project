package com.skatehub.security;

import com.skatehub.config.SecurityConfig;
import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.util.JwtAuthenticationFilter;
import com.skatehub.util.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = SecurityBoundaryTest.TestBoundaryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private AdminRepository adminRepository;

    @Test
    void configuredAuthEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/api/auth/captcha")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login/user")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login/admin")).andExpect(status().isOk());
    }

    @Test
    void publicApiOnlyAllowsAnonymousGet() throws Exception {
        mockMvc.perform(get("/api/public/posts")).andExpect(status().isOk());
        mockMvc.perform(post("/api/public/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void uploadPathOnlyAllowsAnonymousGetStaticAccess() throws Exception {
        mockMvc.perform(get("/upload/images/sample.png")).andExpect(status().isOk());
        mockMvc.perform(post("/upload/images/sample.png"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void bootstrapAdminIsNotAnonymous() throws Exception {
        mockMvc.perform(post("/api/auth/bootstrap-admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void otherApisRequireLogin() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void fileUploadApisRequireLogin() throws Exception {
        mockMvc.perform(post("/api/files/upload/image"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void invalidBearerTokenReturnsUnauthorizedJson() throws Exception {
        when(jwtTokenService.parse("bad-token")).thenThrow(new RuntimeException("bad token"));

        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminApisRejectNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(roles = "USER")
    void genericAiApisRejectNonAdminUsers() throws Exception {
        mockMvc.perform(post("/api/ai/chat"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/api/ai/execute"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/ai/config"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminApisAllowAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isOk());
    }

    @RestController
    static class TestBoundaryController {

        @GetMapping("/api/auth/captcha")
        String captcha() {
            return "ok";
        }

        @PostMapping("/api/auth/register")
        String register() {
            return "ok";
        }

        @PostMapping("/api/auth/login/user")
        String userLogin() {
            return "ok";
        }

        @PostMapping("/api/auth/login/admin")
        String adminLogin() {
            return "ok";
        }

        @PostMapping("/api/auth/bootstrap-admin")
        String bootstrapAdmin() {
            return "ok";
        }

        @GetMapping("/api/public/posts")
        String publicPosts() {
            return "ok";
        }

        @PostMapping("/api/public/posts")
        String publicPostsMutation() {
            return "ok";
        }

        @GetMapping("/upload/images/sample.png")
        String uploadStaticResource() {
            return "ok";
        }

        @PostMapping("/upload/images/sample.png")
        String uploadStaticMutation() {
            return "ok";
        }

        @GetMapping("/api/user/profile")
        String profile() {
            return "ok";
        }

        @PostMapping("/api/files/upload/image")
        String uploadImage() {
            return "ok";
        }

        @GetMapping("/api/admin/stats")
        String adminStats() {
            return "ok";
        }

        @PostMapping("/api/ai/chat")
        String aiChat() {
            return "ok";
        }

        @PostMapping("/api/ai/execute")
        String aiExecute() {
            return "ok";
        }

        @GetMapping("/api/ai/config")
        String aiConfig() {
            return "ok";
        }
    }
}
