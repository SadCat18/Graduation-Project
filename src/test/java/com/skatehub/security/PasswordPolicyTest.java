package com.skatehub.security;

import com.skatehub.util.BizException;
import com.skatehub.util.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    void userPasswordRequiresEightToTwentyCharsWithLetterAndDigit() {
        assertThatThrownBy(() -> passwordPolicy.validateUserPassword("abc1234", "13800138000"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> passwordPolicy.validateUserPassword("abcdefgh", "13800138000"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> passwordPolicy.validateUserPassword("12345678", "13800138000"))
                .isInstanceOf(BizException.class);

        passwordPolicy.validateUserPassword("skate2026", "13800138000");
    }

    @Test
    void adminPasswordRequiresTwelveToThirtyTwoCharsAndThreeCharacterClasses() {
        assertThatThrownBy(() -> passwordPolicy.validateAdminPassword("Admin12345", null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> passwordPolicy.validateAdminPassword("adminpassword12", null))
                .isInstanceOf(BizException.class);

        passwordPolicy.validateAdminPassword("SkateHub@2026", null);
        passwordPolicy.validateAdminPassword("SkateHub2026", null);
    }

    @Test
    void weakPasswordsAreRejectedForAllRoles() {
        assertThatThrownBy(() -> passwordPolicy.validateUserPassword("123456", "13800138000"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> passwordPolicy.validateUserPassword("admin123", "13800138000"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> passwordPolicy.validateAdminPassword("password", null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> passwordPolicy.validateAdminPassword("111111", null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void passwordCannotContainPhoneTail() {
        assertThatThrownBy(() -> passwordPolicy.validateUserPassword("skate138000", "13800138000"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> passwordPolicy.validateAdminPassword("Admin@138000", "13800138000"))
                .isInstanceOf(BizException.class);
    }
}
