package com.javademo1.controller;

import com.javademo1.pojo.auth.LoginRequest;
import com.javademo1.pojo.auth.LoginResponse;
import com.javademo1.pojo.auth.RegisterRequest;
import com.javademo1.service.AuthService;
import com.javademo1.util.ApiResponse;
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
