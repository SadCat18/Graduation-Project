package com.skatehub.service;

import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Admin;
import com.skatehub.pojo.User;
import com.skatehub.pojo.auth.RegisterRequest;
import com.skatehub.pojo.user.PasswordUpdateRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordPolicyIntegrationTest {

    @Test
    void registerValidatesPolicyAndStoresEncodedPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        PasswordPolicy passwordPolicy = mock(PasswordPolicy.class);
        AuthService authService = new AuthService(
                userRepository,
                mock(AdminRepository.class),
                passwordEncoder,
                mock(com.skatehub.util.JwtTokenService.class),
                mock(StringRedisTemplate.class),
                passwordPolicy
        );
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPhone("13800138000");
        request.setPassword("skate2026");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("13800138000")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("skate2026")).thenReturn("$2a$encoded");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        authService.register(request);

        verify(passwordPolicy).validateUserPassword("skate2026", "13800138000");
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("$2a$encoded");
    }

    @Test
    void registerRejectsWeakPasswordBeforeEncodingAndSaving() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthService authService = new AuthService(
                userRepository,
                mock(AdminRepository.class),
                passwordEncoder,
                mock(com.skatehub.util.JwtTokenService.class),
                mock(StringRedisTemplate.class),
                new PasswordPolicy()
        );
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPhone("13800138000");
        request.setPassword("123456");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("13800138000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BizException.class);

        verify(passwordEncoder, never()).encode("123456");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void updatePasswordValidatesPolicyAndStoresEncodedPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        PasswordPolicy passwordPolicy = mock(PasswordPolicy.class);
        UserService userService = new UserService(
                userRepository,
                mock(com.skatehub.dao.MessageRepository.class),
                mock(com.skatehub.dao.PostRepository.class),
                mock(com.skatehub.dao.ActivityRepository.class),
                mock(com.skatehub.dao.InteractionRepository.class),
                mock(com.skatehub.dao.CommentRepository.class),
                passwordEncoder,
                mock(UserGrowthService.class),
                passwordPolicy
        );
        User user = new User();
        user.setUserId(7L);
        user.setPhone("13800138000");
        user.setPassword("$2a$old");
        user.setTokenVersion(1);
        PasswordUpdateRequest request = new PasswordUpdateRequest();
        request.setOldPassword("old-password");
        request.setNewPassword("skate2026");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "$2a$old")).thenReturn(true);
        when(passwordEncoder.encode("skate2026")).thenReturn("$2a$new");

        userService.updatePassword(new CurrentUser(7L, "USER", "alice", 1), request);

        verify(passwordPolicy).validateUserPassword("skate2026", "13800138000");
        assertThat(user.getPassword()).isEqualTo("$2a$new");
        verify(userRepository).save(user);
    }

    @Test
    void defaultAdminValidatesPolicyAndStoresEncodedStrongPassword() {
        AdminRepository adminRepository = mock(AdminRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        PasswordPolicy passwordPolicy = mock(PasswordPolicy.class);
        AuthService authService = new AuthService(
                mock(UserRepository.class),
                adminRepository,
                passwordEncoder,
                mock(com.skatehub.util.JwtTokenService.class),
                mock(StringRedisTemplate.class),
                passwordPolicy
        );
        ReflectionTestUtils.setField(authService, "defaultAdminPassword", "SkateHub@2026");
        when(adminRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("SkateHub@2026")).thenReturn("$2a$admin");
        ArgumentCaptor<Admin> adminCaptor = ArgumentCaptor.forClass(Admin.class);

        authService.ensureDefaultAdmin();

        verify(passwordPolicy).validateAdminPassword("SkateHub@2026", null);
        verify(adminRepository).save(adminCaptor.capture());
        assertThat(adminCaptor.getValue().getPassword()).isEqualTo("$2a$admin");
        assertThat(adminCaptor.getValue().getPassword()).isNotEqualTo("SkateHub@2026");
    }
}
