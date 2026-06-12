package com.skatehub.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Component
public class PasswordPolicy {

    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "123456",
            "111111",
            "password",
            "admin123"
    );

    public void validateUserPassword(String password, String phone) {
        validateCommonWeakPassword(password, phone);
        if (password.length() < 8 || password.length() > 20) {
            throw new BizException("密码长度需在 8 到 20 位之间");
        }
        if (!hasLetter(password) || !hasDigit(password)) {
            throw new BizException("密码至少需要包含字母和数字");
        }
    }

    public void validateAdminPassword(String password, String phone) {
        validateCommonWeakPassword(password, phone);
        if (password.length() < 12 || password.length() > 32) {
            throw new BizException("管理员密码长度需在 12 到 32 位之间");
        }
        if (characterClassCount(password) < 3) {
            throw new BizException("管理员密码至少需要包含大写字母、小写字母、数字、特殊字符中的 3 类");
        }
    }

    private void validateCommonWeakPassword(String password, String phone) {
        if (!StringUtils.hasText(password)) {
            throw new BizException("密码不能为空");
        }
        String normalizedPassword = password.trim();
        if (!normalizedPassword.equals(password)) {
            throw new BizException("密码不能包含首尾空格");
        }
        String lower = normalizedPassword.toLowerCase(Locale.ROOT);
        if (WEAK_PASSWORDS.contains(lower)) {
            throw new BizException("密码过于简单，请更换更安全的密码");
        }
        if (StringUtils.hasText(phone)) {
            String normalizedPhone = phone.trim();
            if (normalizedPhone.length() >= 6) {
                String phoneTail = normalizedPhone.substring(normalizedPhone.length() - 6);
                if (normalizedPassword.contains(phoneTail)) {
                    throw new BizException("密码不能包含手机号后六位");
                }
            }
        }
    }

    private boolean hasLetter(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (Character.isLetter(password.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDigit(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (Character.isDigit(password.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private int characterClassCount(String password) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isUpperCase(ch)) {
                upper = true;
            } else if (Character.isLowerCase(ch)) {
                lower = true;
            } else if (Character.isDigit(ch)) {
                digit = true;
            } else {
                special = true;
            }
        }
        int count = 0;
        if (upper) count++;
        if (lower) count++;
        if (digit) count++;
        if (special) count++;
        return count;
    }
}
