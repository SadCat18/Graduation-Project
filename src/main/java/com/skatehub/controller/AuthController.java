package com.skatehub.controller;

import com.skatehub.pojo.auth.LoginRequest;
import com.skatehub.pojo.auth.LoginResponse;
import com.skatehub.pojo.auth.RegisterRequest;
import com.skatehub.service.AuthService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.BizException;
import com.skatehub.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody @Valid RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success();
    }

    @PostMapping("/login/user")
    public ApiResponse<LoginResponse> userLogin(@RequestBody @Valid LoginRequest request) {
        authService.validateCaptcha(request.getCaptchaId(), request.getCaptchaCode());
        return ApiResponse.success(authService.userLogin(request));
    }

    @PostMapping("/login/admin")
    public ApiResponse<LoginResponse> adminLogin(@RequestBody @Valid LoginRequest request) {
        authService.validateCaptcha(request.getCaptchaId(), request.getCaptchaCode());
        return ApiResponse.success(authService.adminLogin(request));
    }

    @PostMapping("/bootstrap-admin")
    public ApiResponse<Void> bootstrapAdmin(HttpServletRequest request) {
        if (!SecurityUtils.isAdmin()) {
            throw new BizException("仅管理员可执行默认管理员初始化");
        }
        if (!isLocalRequest(request)) {
            throw new BizException("bootstrap-admin 仅允许本机初始化调用");
        }
        authService.ensureDefaultAdmin();
        return ApiResponse.success();
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        String remoteAddr = request == null ? "" : request.getRemoteAddr();
        boolean localRemote = "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr);
        if (!localRemote) {
            return false;
        }
        return isBlankHeader(request, "X-Forwarded-For") && isBlankHeader(request, "Forwarded");
    }

    private boolean isBlankHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank();
    }
}
