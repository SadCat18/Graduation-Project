package com.skatehub;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodingSanityTest {

    private static final List<String> TEXT_EXTENSIONS = List.of(
            ".java", ".vue", ".js", ".json", ".html", ".md", ".yml", ".yaml", ".properties", ".sql", ".xml"
    );

    private static final List<String> MOJIBAKE_MARKERS = List.of(
            "\u9422\u3126\u57db", // 用户
            "\u7025\u55df\u721c", // 密码
            "\u6d93\u5db6\u8dbe", // 不能
            "\u935a\u6a3f",       // 同/含等常见乱码片段前缀
            "\u95c0\u57ae",       // 长度
            "\u951b",
            "\u9225",
            "\u699b",
            "\u5a32",
            "\ufffd"
    );

    @Test
    void textFilesDoNotContainCommonMojibakeMarkers() throws IOException {
        Path root = Path.of("").toAbsolutePath();

        try (Stream<Path> paths = Files.walk(root)) {
            List<String> badFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isTextFile(path.getFileName().toString()))
                    .filter(path -> !isIgnored(path, root))
                    .filter(EncodingSanityTest::containsMojibakeMarker)
                    .map(root::relativize)
                    .map(Path::toString)
                    .toList();

            assertTrue(badFiles.isEmpty(), "Files contain likely Chinese mojibake: " + badFiles);
        }
    }

    private static boolean isTextFile(String fileName) {
        return TEXT_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private static boolean isIgnored(Path path, Path root) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        return relative.startsWith("target/")
                || relative.startsWith(".idea/")
                || relative.startsWith("frontend/node_modules/")
                || relative.startsWith("frontend/dist/");
    }

    private static boolean containsMojibakeMarker(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return MOJIBAKE_MARKERS.stream().anyMatch(content::contains);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
