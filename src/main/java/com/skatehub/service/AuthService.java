package com.skatehub.service;

import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Admin;
import com.skatehub.pojo.User;
import com.skatehub.pojo.auth.LoginRequest;
import com.skatehub.pojo.auth.LoginResponse;
import com.skatehub.pojo.auth.RegisterRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CaptchaConstants;
import com.skatehub.util.JwtTokenService;
import com.skatehub.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordPolicy passwordPolicy;

    @Value("${admin.default-password:SkateHub@2026}")
    private String defaultAdminPassword;

    public void register(RegisterRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();

        if (username.isBlank()) {
            throw new BizException("用户名不能为空");
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException("手机号格式不正确，请输入11位手机号");
        }

        userRepository.findByUsername(username).ifPresent(user -> {
            throw new BizException("用户名已存在");
        });
        userRepository.findByPhone(phone).ifPresent(user -> {
            throw new BizException("手机号已被占用");
        });

        passwordPolicy.validateUserPassword(request.getPassword(), phone);

        User user = new User();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
    }

    public void validateCaptcha(String captchaId, String captchaCode) {
        String captchaKey = CaptchaConstants.LOGIN_CAPTCHA_KEY_PREFIX + captchaId.trim();
        String captchaInRedis = stringRedisTemplate.opsForValue().get(captchaKey);
        stringRedisTemplate.delete(captchaKey);
        if (captchaInRedis == null) {
            throw new BizException("验证码已过期，请刷新后重试");
        }
        if (!captchaInRedis.equalsIgnoreCase(captchaCode.trim())) {
            throw new BizException("验证码错误");
        }
    }

    public LoginResponse userLogin(LoginRequest request) {
        String mode = request.getLoginType() == null ? "username" : request.getLoginType().trim().toLowerCase(Locale.ROOT);
        String account = request.getAccount() == null ? "" : request.getAccount().trim();

        Optional<User> userOptional;
        if ("phone".equals(mode)) {
            userOptional = userRepository.findByPhone(account);
        } else if ("username".equals(mode)) {
            userOptional = userRepository.findByUsername(account);
        } else {
            throw new BizException("不支持的登录方式");
        }

        User user = userOptional.orElseThrow(() -> new BizException("账号或密码错误"));
        if (!"0".equals(user.getStatus())) {
            throw new BizException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException("账号或密码错误");
        }
        String token = jwtTokenService.createToken(user.getUserId(), "USER", user.getUsername(), user.getTokenVersion());
        return new LoginResponse(token, "USER", user.getUsername());
    }

    public LoginResponse adminLogin(LoginRequest request) {
        Admin admin = adminRepository.findByAccount(request.getAccount())
                .orElseThrow(() -> new BizException("账号或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BizException("账号或密码错误");
        }
        String token = jwtTokenService.createToken(admin.getAdminId(), "ADMIN", admin.getAccount());
        return new LoginResponse(token, "ADMIN", admin.getAccount());
    }

    public void ensureDefaultAdmin() {
        if (adminRepository.count() > 0) {
            return;
        }
        Admin admin = new Admin();
        admin.setAccount("admin");
        passwordPolicy.validateAdminPassword(defaultAdminPassword, null);
        admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
        admin.setRealName("系统管理员");
        admin.setRole("1");
        adminRepository.save(admin);
    }
}
