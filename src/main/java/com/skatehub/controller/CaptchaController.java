package com.skatehub.controller;

import com.skatehub.util.CaptchaConstants;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CaptchaController {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Random RANDOM = new Random();
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public void captcha(@RequestParam("captchaId") String captchaId, HttpServletResponse response) throws IOException {
        int width = 120;
        int height = 42;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(20, 25, 40));
            graphics.fillRect(0, 0, width, height);

            for (int i = 0; i < 8; i++) {
                graphics.setColor(new Color(80 + RANDOM.nextInt(120), 80 + RANDOM.nextInt(120), 80 + RANDOM.nextInt(120)));
                int x1 = RANDOM.nextInt(width);
                int y1 = RANDOM.nextInt(height);
                int x2 = RANDOM.nextInt(width);
                int y2 = RANDOM.nextInt(height);
                graphics.drawLine(x1, y1, x2, y2);
            }

            String code = randomCode(4);
            String captchaKey = CaptchaConstants.LOGIN_CAPTCHA_KEY_PREFIX + captchaId.trim();
            stringRedisTemplate.opsForValue().set(
                    captchaKey,
                    code,
                    CaptchaConstants.LOGIN_CAPTCHA_EXPIRE_SECONDS,
                    TimeUnit.SECONDS
            );

            graphics.setFont(new Font("Arial", Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(new Color(140 + RANDOM.nextInt(100), 140 + RANDOM.nextInt(100), 140 + RANDOM.nextInt(100)));
                graphics.drawString(String.valueOf(code.charAt(i)), 18 + i * 24, 30 + RANDOM.nextInt(4));
            }

            response.setHeader("Pragma", "No-cache");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setDateHeader("Expires", 0);
            response.setContentType(MediaType.IMAGE_PNG_VALUE);
            ImageIO.write(image, "png", response.getOutputStream());
        } finally {
            graphics.dispose();
        }
    }

    private String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return code.toString();
    }
}
