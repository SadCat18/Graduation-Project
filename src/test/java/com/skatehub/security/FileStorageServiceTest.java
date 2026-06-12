package com.skatehub.security;

import com.skatehub.service.FileStorageService;
import com.skatehub.util.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsImageWhenContentTypeIsNotWhitelistedEvenIfMagicMatches() {
        FileStorageService service = new FileStorageService(tempDir.resolve("upload"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/svg+xml",
                pngBytes()
        );

        assertThatThrownBy(() -> service.storeImage(file))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsVideoWhenContentTypeIsOctetStreamEvenIfMagicMatches() {
        FileStorageService service = new FileStorageService(tempDir.resolve("upload"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "clip.mp4",
                "application/octet-stream",
                mp4Bytes()
        );

        assertThatThrownBy(() -> service.storeVideo(file))
                .isInstanceOf(BizException.class);
    }

    @Test
    void storesImageWithUuidFileNameUnderFixedUploadRoot() throws Exception {
        Path uploadRoot = tempDir.resolve("upload");
        FileStorageService service = new FileStorageService(uploadRoot);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../avatar.png",
                "image/png",
                pngBytes()
        );

        String accessPath = service.storeImage(file);

        assertThat(accessPath)
                .matches(Pattern.compile("^/upload/images/\\d{8}/[0-9a-f]{32}\\.png$"));
        assertThat(accessPath).doesNotContain("avatar");
        Path storedPath = uploadRoot.resolve(accessPath.substring("/upload/".length())).normalize();
        assertThat(storedPath).startsWith(uploadRoot.toAbsolutePath().normalize());
        assertThat(Files.exists(storedPath)).isTrue();
    }

    @Test
    void rejectsPathTraversalDoubleExtensionInsteadOfSavingOriginalName() {
        FileStorageService service = new FileStorageService(tempDir.resolve("upload"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../avatar.png.exe",
                "image/png",
                pngBytes()
        );

        assertThatThrownBy(() -> service.storeImage(file))
                .isInstanceOf(BizException.class);
    }

    private byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }

    private byte[] mp4Bytes() {
        byte[] bytes = "0000ftypisom".getBytes(StandardCharsets.US_ASCII);
        bytes[0] = 0x00;
        bytes[1] = 0x00;
        bytes[2] = 0x00;
        bytes[3] = 0x18;
        return bytes;
    }
}
