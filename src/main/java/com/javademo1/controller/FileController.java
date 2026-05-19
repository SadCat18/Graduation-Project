package com.javademo1.controller;

import com.javademo1.util.ApiResponse;
import com.javademo1.util.BizException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L;
    private static final long MAX_VIDEO_SIZE = 300 * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
    private static final Set<String> ALLOWED_VIDEO_EXT = Set.of(".mp4", ".mov", ".webm", ".m4v", ".avi");

    @PostMapping("/upload/image")
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BizException("图片大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase().startsWith("image/")) {
            throw new BizException("仅支持图片文件上传");
        }

        String originalName = file.getOriginalFilename();
        String ext = extractExt(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("图片格式不支持，仅支持 jpg/jpeg/png/gif/webp/bmp");
        }

        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path saveDir = Paths.get(System.getProperty("user.dir"), "upload", "images", day);
        Files.createDirectories(saveDir);

        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path savePath = saveDir.resolve(fileName);
        file.transferTo(savePath.toFile());

        String accessPath = "/upload/images/" + day + "/" + fileName;
        return ApiResponse.success(accessPath);
    }

    @PostMapping("/upload/video")
    public ApiResponse<String> uploadVideo(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的视频");
        }
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new BizException("视频大小不能超过 300MB");
        }
        String contentType = file.getContentType();
        boolean contentTypeValid = StringUtils.hasText(contentType) &&
                (contentType.toLowerCase().startsWith("video/") || contentType.toLowerCase().equals("application/octet-stream"));
        if (!contentTypeValid) {
            throw new BizException("仅支持视频文件上传");
        }

        String originalName = file.getOriginalFilename();
        String ext = extractExt(originalName);
        if (!ALLOWED_VIDEO_EXT.contains(ext)) {
            throw new BizException("视频格式不支持，仅支持 mp4/mov/webm/m4v/avi");
        }

        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path saveDir = Paths.get(System.getProperty("user.dir"), "upload", "videos", day);
        Files.createDirectories(saveDir);

        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path savePath = saveDir.resolve(fileName);
        file.transferTo(savePath.toFile());

        String accessPath = "/upload/videos/" + day + "/" + fileName;
        return ApiResponse.success(accessPath);
    }

    private String extractExt(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String cleanName = StringUtils.cleanPath(fileName);
        int index = cleanName.lastIndexOf('.');
        if (index < 0 || index == cleanName.length() - 1) {
            return "";
        }
        return cleanName.substring(index).toLowerCase();
    }
}
