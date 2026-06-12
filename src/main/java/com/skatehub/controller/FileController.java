package com.skatehub.controller;

import com.skatehub.service.FileStorageService;
import com.skatehub.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload/image")
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(fileStorageService.storeImage(file));
    }

    @PostMapping("/upload/video")
    public ApiResponse<String> uploadVideo(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(fileStorageService.storeVideo(file));
    }
}
