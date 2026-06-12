package com.skatehub.util;

import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                CurrentUser currentUser = jwtTokenService.parse(token);
                if (isAccountActive(currentUser)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    currentUser,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role().toUpperCase()))
                            );
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    SecurityContextHolder.clearContext();
                    writeUnauthorized(response);
                    return;
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAccountActive(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null || currentUser.role() == null) {
            return false;
        }
        if ("USER".equalsIgnoreCase(currentUser.role())) {
            return userRepository.findById(currentUser.id())
                    .map(user -> "0".equals(user.getStatus())
                            && normalizeVersion(user.getTokenVersion()).equals(normalizeVersion(currentUser.tokenVersion())))
                    .orElse(false);
        }
        if ("ADMIN".equalsIgnoreCase(currentUser.role())) {
            return adminRepository.existsById(currentUser.id());
        }
        return false;
    }

    private Integer normalizeVersion(Integer tokenVersion) {
        return tokenVersion == null ? 0 : tokenVersion;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"认证失败，请重新登录\"}");
    }
}
