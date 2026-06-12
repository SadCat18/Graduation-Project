package com.skatehub.util;

public record CurrentUser(Long id, String role, String name, Integer tokenVersion) {

    public CurrentUser(Long id, String role, String name) {
        this(id, role, name, 0);
    }
}
