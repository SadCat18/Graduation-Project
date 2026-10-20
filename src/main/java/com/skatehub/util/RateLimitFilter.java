package com.skatehub.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skatehub.config.RateLimitProperties;
import com.skatehub.service.RateLimitService;
import com.skatehub.service.RateLimitService.RateLimitDecision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private static final Set<String> BOARD_COMPUTATION_PATHS=Set.of(
            "/api/skate/board-assistant/recommend", "/api/skate/board-assistant/evaluate",
            "/api/skate/board-assistant/replacements", "/api/skate/board-assistant/compare");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!rateLimitService.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        RequestLimit requestLimit = resolveLimit(request);
        if (requestLimit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest requestToUse = request;
        RateLimitDecision decision = RateLimitDecision.accepted();
        if (requestLimit.readBodyAccount()) {
            CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
            requestToUse = cachedRequest;
            String account = readAccount(cachedRequest.body());
            decision = combine(decision, check(requestLimit.accountKey(account),
                    requestLimit.accountLimit(),
                    requestLimit.accountWindow()));
        }
        decision = combine(decision, check(requestLimit.key(), requestLimit.limit(), requestLimit.window()));
        if (!decision.allowed()) {
            writeError(response, decision.status(), decision.message());
            return;
        }

        filterChain.doFilter(requestToUse, response);
    }

    private RequestLimit resolveLimit(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (HttpMethod.POST.matches(method) && path.startsWith("/api/auth/login/")) {
            return RequestLimit.login(properties.getLoginIpPerMinute(),
                    properties.getLoginAccountPerFiveMinutes(),
                    clientIp(request));
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/register".equals(path)) {
            return RequestLimit.simple("register:ip:" + clientIp(request), properties.getRegisterIpPerHour(), Duration.ofHours(1));
        }
        if (path.startsWith("/api/ai/") && mutates(method)) {
            return RequestLimit.simple("ai:user:" + userKey(), properties.getAiUserPerMinute(), Duration.ofMinutes(1));
        }
        if (path.startsWith("/api/files/upload/") && HttpMethod.POST.matches(method)) {
            return RequestLimit.simple("upload:user:" + userKey(), properties.getUploadUserPerMinute(), Duration.ofMinutes(1));
        }
        if (HttpMethod.POST.matches(method) && BOARD_COMPUTATION_PATHS.contains(path)) {
            // One IP budget across all four expensive public operations, not four independent allowances.
            return RequestLimit.simple("board:ip:" + clientIp(request), properties.getBoardIpPerMinute(), Duration.ofMinutes(1));
        }
        if (isWriteEndpoint(method, path)) {
            return RequestLimit.simple("write:user:" + userKey(), properties.getWriteUserPerMinute(), Duration.ofMinutes(1));
        }
        return null;
    }

    private boolean isWriteEndpoint(String method, String path) {
        if (!mutates(method)) {
            return false;
        }
        return path.startsWith("/api/posts")
                || path.startsWith("/api/messages")
                || path.startsWith("/api/private-messages");
    }

    private boolean mutates(String method) {
        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.PATCH.matches(method)
                || HttpMethod.DELETE.matches(method);
    }

    private RateLimitDecision check(String key, int limit, Duration window) {
        return rateLimitService.allow("rate-limit:" + key, limit, window);
    }

    private RateLimitDecision combine(RateLimitDecision first, RateLimitDecision second) {
        return first.allowed() ? second : first;
    }

    private String readAccount(byte[] body) {
        if (body.length == 0) {
            return "blank";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String account = node.path("account").asText("");
            return account.isBlank() ? "blank" : account.trim().toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            return "unreadable";
        }
    }

    private String userKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof CurrentUser currentUser && currentUser.id() != null) {
            return currentUser.role().toLowerCase(Locale.ROOT) + ":" + currentUser.id();
        }
        return "anonymous";
    }

    private String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"), properties.getTrustedProxies());
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("code", status, "message", message));
    }

    private record RequestLimit(String key, int limit, Duration window,
                                boolean readBodyAccount, int accountLimit, Duration accountWindow) {
        static RequestLimit simple(String key, int limit, Duration window) {
            return new RequestLimit(key, limit, window, false, 0, Duration.ZERO);
        }

        static RequestLimit login(int ipLimit, int accountLimit, String clientIp) {
            return new RequestLimit("login:ip:" + clientIp, ipLimit, Duration.ofMinutes(1),
                    true, accountLimit, Duration.ofMinutes(5));
        }

        String accountKey(String account) {
            return "login:account:" + account;
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        private byte[] body() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return inputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
