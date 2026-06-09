package com.skatehub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/login",
            "/register",
            "/community",
            "/community/**",
            "/activities",
            "/activities/**",
            "/places/**",
            "/bulletins",
            "/bulletins/**",
            "/news/**",
            "/profile",
            "/admin"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
