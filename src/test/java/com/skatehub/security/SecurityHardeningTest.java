package com.skatehub.security;

import com.skatehub.controller.AuthController;
import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.User;
import com.skatehub.pojo.auth.RegisterRequest;
import com.skatehub.pojo.comment.CommentCreateRequest;
import com.skatehub.pojo.post.PostCreateRequest;
import com.skatehub.pojo.user.PasswordUpdateRequest;
import com.skatehub.pojo.user.UserProfileUpdateRequest;
import com.skatehub.service.AuthService;
import com.skatehub.service.FileStorageService;
import com.skatehub.service.UserService;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.JwtAuthenticationFilter;
import com.skatehub.util.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityHardeningTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void jwtFilterDoesNotAuthenticateDisabledUserToken() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        UserRepository userRepository = mock(UserRepository.class);
        AdminRepository adminRepository = mock(AdminRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService, userRepository, adminRepository);

        User disabled = new User();
        disabled.setUserId(7L);
        disabled.setStatus("1");
        when(jwtTokenService.parse("token")).thenReturn(new CurrentUser(7L, "USER", "alice"));
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(disabled));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(chain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void jwtFilterDoesNotAuthenticateStaleUserTokenVersion() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        UserRepository userRepository = mock(UserRepository.class);
        AdminRepository adminRepository = mock(AdminRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService, userRepository, adminRepository);

        User user = new User();
        user.setUserId(7L);
        user.setStatus("0");
        user.setTokenVersion(2);
        when(jwtTokenService.parse("token")).thenReturn(new CurrentUser(7L, "USER", "alice", 1));
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(chain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void jwtTokenServiceRejectsDefaultSecretInProduction() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod");

        assertThatThrownBy(() -> new JwtTokenService(
                JwtTokenService.DEFAULT_SECRET,
                24,
                environment
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passwordUpdateIncrementsTokenVersion() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserService userService = newUserService(userRepository, passwordEncoder);
        User user = new User();
        user.setUserId(7L);
        user.setPassword("encoded-old");
        user.setTokenVersion(3);
        PasswordUpdateRequest request = new PasswordUpdateRequest();
        request.setOldPassword("old");
        request.setNewPassword("new-password");
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("old", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");

        userService.updatePassword(new CurrentUser(7L, "USER", "alice", 3), request);

        assertThat(user.getTokenVersion()).isEqualTo(4);
        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userRepository).save(user);
    }

    @Test
    void passwordUpdateInitializesMissingTokenVersion() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserService userService = newUserService(userRepository, passwordEncoder);
        User user = new User();
        user.setUserId(7L);
        user.setPassword("encoded-old");
        user.setTokenVersion(null);
        PasswordUpdateRequest request = new PasswordUpdateRequest();
        request.setOldPassword("old");
        request.setNewPassword("new-password");
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("old", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");

        userService.updatePassword(new CurrentUser(7L, "USER", "alice"), request);

        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    @Test
    void bootstrapAdminRejectsRemoteRequests() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");

        assertThatThrownBy(() -> controller.bootstrapAdmin(request))
                .isInstanceOf(BizException.class);
        verify(authService, never()).ensureDefaultAdmin();
    }

    @Test
    void uploadImageRejectsSpoofedContent() {
        FileStorageService fileStorageService = new FileStorageService(java.nio.file.Paths.get("target", "test-upload"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "not really a png".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> fileStorageService.storeImage(file))
                .isInstanceOf(BizException.class);
    }

    @Test
    void userGeneratedDtosRejectOversizedInput() {
        PostCreateRequest post = new PostCreateRequest();
        post.setTitle("A".repeat(101));
        post.setContent("B".repeat(20001));

        CommentCreateRequest comment = new CommentCreateRequest();
        comment.setContent("C".repeat(2001));

        UserProfileUpdateRequest profile = new UserProfileUpdateRequest();
        profile.setBio("D".repeat(201));

        RegisterRequest register = new RegisterRequest();
        register.setUsername("E".repeat(21));
        register.setPhone("13800138000");
        register.setPassword("123456");

        assertThat(messages(validate(post))).contains("标题长度不能超过 100");
        assertThat(messages(validate(post))).contains("正文长度不能超过 20000");
        assertThat(messages(validate(comment))).contains("评论内容长度不能超过 2000");
        assertThat(messages(validate(profile))).contains("个人简介长度不能超过 200");
        assertThat(messages(validate(register))).anyMatch(message -> message.contains("20"));
    }

    private <T> Set<ConstraintViolation<T>> validate(T target) {
        return VALIDATOR.validate(target);
    }

    private Set<String> messages(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    private UserService newUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return new UserService(
                userRepository,
                mock(com.skatehub.dao.MessageRepository.class),
                mock(com.skatehub.dao.PostRepository.class),
                mock(com.skatehub.dao.ActivityRepository.class),
                mock(com.skatehub.dao.InteractionRepository.class),
                mock(com.skatehub.dao.CommentRepository.class),
                passwordEncoder,
                mock(com.skatehub.service.UserGrowthService.class),
                mock(com.skatehub.util.PasswordPolicy.class)
        );
    }
}
