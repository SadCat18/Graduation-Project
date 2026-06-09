package com.skatehub.util;

import com.skatehub.util.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser;
    }

    public static boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(currentUser().role());
    }
}
