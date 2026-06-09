package com.skatehub.controller;

import com.skatehub.pojo.auth.LoginRequest;
import com.skatehub.pojo.auth.LoginResponse;
import com.skatehub.pojo.auth.RegisterRequest;
import com.skatehub.service.AuthService;
import com.skatehub.util.ApiResponse;
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
    public ApiResponse<Void> bootstrapAdmin() {
        authService.ensureDefaultAdmin();
        return ApiResponse.success();
    }
}
