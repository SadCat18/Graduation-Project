package com.skatehub.service;

import com.skatehub.util.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L;
    public static final long MAX_VIDEO_SIZE = 300 * 1024 * 1024L;

    private static final Map<String, Set<String>> IMAGE_CONTENT_TYPES = Map.of(
            ".jpg", Set.of("image/jpeg"),
            ".jpeg", Set.of("image/jpeg"),
            ".png", Set.of("image/png"),
            ".gif", Set.of("image/gif"),
            ".webp", Set.of("image/webp"),
            ".bmp", Set.of("image/bmp", "image/x-ms-bmp")
    );
    private static final Map<String, Set<String>> VIDEO_CONTENT_TYPES = Map.of(
            ".mp4", Set.of("video/mp4"),
            ".m4v", Set.of("video/x-m4v", "video/mp4"),
            ".mov", Set.of("video/quicktime"),
            ".webm", Set.of("video/webm"),
            ".avi", Set.of("video/x-msvideo", "video/avi")
    );

    private final Path uploadRoot;

    public FileStorageService() {
        this(Paths.get(System.getProperty("user.dir"), "upload"));
    }

    public FileStorageService(Path uploadRoot) {
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
    }

    public String storeImage(MultipartFile file) throws IOException {
        validateCommon(file, MAX_IMAGE_SIZE, IMAGE_CONTENT_TYPES, "图片");
        String ext = extractExt(file.getOriginalFilename());
        if (!hasValidImageSignature(file, ext)) {
            throw new BizException("图片内容与文件格式不匹配");
        }
        return store(file, "images", ext);
    }

    public String storeVideo(MultipartFile file) throws IOException {
        validateCommon(file, MAX_VIDEO_SIZE, VIDEO_CONTENT_TYPES, "视频");
        String ext = extractExt(file.getOriginalFilename());
        if (!hasValidVideoSignature(file, ext)) {
            throw new BizException("视频内容与文件格式不匹配");
        }
        return store(file, "videos", ext);
    }

    private void validateCommon(MultipartFile file,
                                long maxSize,
                                Map<String, Set<String>> allowedContentTypes,
                                String label) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的" + label);
        }
        if (file.getSize() > maxSize) {
            throw new BizException(label + "大小超出限制");
        }
        String ext = extractExt(file.getOriginalFilename());
        if (!allowedContentTypes.containsKey(ext)) {
            throw new BizException(label + "格式不支持");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!allowedContentTypes.get(ext).contains(contentType)) {
            throw new BizException(label + "Content-Type 不支持");
        }
    }

    private String store(MultipartFile file, String folder, String ext) throws IOException {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path saveDir = uploadRoot.resolve(folder).resolve(day).normalize();
        ensureUnderUploadRoot(saveDir);
        Files.createDirectories(saveDir);

        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path savePath = saveDir.resolve(fileName).normalize();
        ensureUnderUploadRoot(savePath);
        file.transferTo(savePath.toFile());
        return "/upload/" + folder + "/" + day + "/" + fileName;
    }

    private void ensureUnderUploadRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(uploadRoot)) {
            throw new BizException("上传路径不合法");
        }
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

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.toLowerCase().trim() : "";
    }

    private boolean hasValidImageSignature(MultipartFile file, String ext) throws IOException {
        byte[] header = readHeader(file, 12);
        return switch (ext) {
            case ".jpg", ".jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case ".png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case ".gif" -> startsWithAscii(header, "GIF87a") || startsWithAscii(header, "GIF89a");
            case ".webp" -> startsWithAscii(header, "RIFF") && asciiAt(header, 8, "WEBP");
            case ".bmp" -> startsWithAscii(header, "BM");
            default -> false;
        };
    }

    private boolean hasValidVideoSignature(MultipartFile file, String ext) throws IOException {
        byte[] header = readHeader(file, 16);
        return switch (ext) {
            case ".mp4", ".m4v", ".mov" -> asciiAt(header, 4, "ftyp");
            case ".webm" -> startsWith(header, 0x1A, 0x45, 0xDF, 0xA3);
            case ".avi" -> startsWithAscii(header, "RIFF") && asciiAt(header, 8, "AVI ");
            default -> false;
        };
    }

    private byte[] readHeader(MultipartFile file, int maxLength) throws IOException {
        return file.getInputStream().readNBytes(maxLength);
    }

    private boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] bytes, String expected) {
        return asciiAt(bytes, 0, expected);
    }

    private boolean asciiAt(byte[] bytes, int offset, String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expectedBytes.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(bytes, offset, offset + expectedBytes.length), expectedBytes);
    }
}
