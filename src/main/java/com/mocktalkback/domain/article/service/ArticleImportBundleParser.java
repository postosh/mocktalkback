package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Component
public class ArticleImportBundleParser {

    private static final Set<String> MARKDOWN_EXTENSIONS = Set.of(".md", ".markdown");
    private static final Set<String> YAML_EXTENSIONS = Set.of(".yml", ".yaml");
    private final Yaml yaml;

    public ArticleImportBundleParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        this.yaml = new Yaml(new SafeConstructor(loaderOptions));
    }

    public ArticleImportBundle parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_ZIP_EMPTY);
        }

        Map<String, byte[]> zipEntries = readZipEntries(file);
        Map<String, String> textEntries = readTextEntries(zipEntries);
        String manifestPath = resolveManifestPath(textEntries.keySet());

        List<ArticleImportCandidate> candidates = manifestPath == null
            ? parseWithoutManifest(textEntries)
            : parseWithManifest(textEntries, manifestPath);

        return new ArticleImportBundle(file.getOriginalFilename(), zipEntries, candidates);
    }

    private List<ArticleImportCandidate> parseWithManifest(Map<String, String> textEntries, String manifestPath) {
        Map<String, Object> manifest = asMap(yaml.load(textEntries.get(manifestPath)), "manifest 형식이 올바르지 않습니다.");
        Map<String, Object> defaults = getMap(manifest, "defaults");
        List<?> articles = getList(manifest.get("articles"), "manifest articles는 배열이어야 합니다.");
        if (articles.isEmpty()) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_MANIFEST_ARTICLES);
        }

        String manifestDirectory = extractDirectory(manifestPath);
        List<ArticleImportCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < articles.size(); index += 1) {
            Object rawItem = articles.get(index);
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                candidates.add(new ArticleImportCandidate(
                    "articles[" + index + "]",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "",
                    List.of(),
                    List.of("articles[" + index + "] 항목 형식이 올바르지 않습니다.")
                ));
                continue;
            }

            Map<String, Object> item = castMap(rawMap);
            List<String> warnings = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            String filePath = normalizeZipPath(readString(item, "file"));
            if (!StringUtils.hasText(filePath)) {
                errors.add("markdown 파일 경로(file)가 없습니다.");
                candidates.add(new ArticleImportCandidate(
                    "articles[" + index + "]",
                    null,
                    normalizeText(resolveString(item, "title")),
                    normalizeText(resolveString(item, "boardSlug", "board_slug", "board-slug")),
                    normalizeText(resolveString(item, "visibility")),
                    normalizeText(resolveString(item, "categoryName", "category_name", "category-name", "category")),
                    "",
                    warnings,
                    errors
                ));
                continue;
            }

            String resolvedPath = resolveRelativePath(manifestDirectory, filePath);
            String markdown = textEntries.get(resolvedPath);
            if (!StringUtils.hasText(markdown)) {
                errors.add("markdown 파일을 찾을 수 없습니다: " + filePath);
                candidates.add(new ArticleImportCandidate(
                    filePath,
                    resolvedPath,
                    normalizeText(resolveString(item, "title")),
                    normalizeText(resolveString(item, "boardSlug", "board_slug", "board-slug")),
                    normalizeText(resolveString(item, "visibility")),
                    normalizeText(resolveString(item, "categoryName", "category_name", "category-name", "category")),
                    "",
                    warnings,
                    errors
                ));
                continue;
            }

            candidates.add(buildCandidate(filePath, resolvedPath, markdown, item, defaults, warnings, errors));
        }

        return candidates;
    }

    private List<ArticleImportCandidate> parseWithoutManifest(Map<String, String> textEntries) {
        List<String> markdownPaths = textEntries.keySet().stream()
            .filter(this::isMarkdownCandidateForAutoScan)
            .sorted()
            .toList();
        if (markdownPaths.isEmpty()) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_NO_MARKDOWN);
        }

        List<ArticleImportCandidate> candidates = new ArrayList<>();
        for (String markdownPath : markdownPaths) {
            String markdown = textEntries.get(markdownPath);
            candidates.add(buildCandidate(
                markdownPath,
                markdownPath,
                markdown,
                Map.of(),
                Map.of(),
                new ArrayList<>(),
                new ArrayList<>()
            ));
        }
        return candidates;
    }

    private ArticleImportCandidate buildCandidate(
        String filePath,
        String markdownPath,
        String markdown,
        Map<String, Object> item,
        Map<String, Object> defaults,
        List<String> warnings,
        List<String> errors
    ) {
        FrontmatterResult frontmatter = parseFrontmatter(markdown);
        warnings.addAll(frontmatter.warnings());
        errors.addAll(frontmatter.errors());

        String title = firstNonBlank(
            resolveString(item, "title"),
            frontmatter.metadata().title(),
            deriveTitleFromPath(filePath)
        );
        String boardSlug = firstNonBlank(
            resolveString(item, "boardSlug", "board_slug", "board-slug"),
            frontmatter.metadata().boardSlug(),
            resolveString(defaults, "boardSlug", "board_slug", "board-slug")
        );
        String visibility = firstNonBlank(
            resolveString(item, "visibility"),
            frontmatter.metadata().visibility(),
            resolveString(defaults, "visibility"),
            "PUBLIC"
        );
        String categoryName = firstNonBlank(
            resolveString(item, "categoryName", "category_name", "category-name", "category"),
            frontmatter.metadata().categoryName(),
            resolveString(defaults, "categoryName", "category_name", "category-name", "category")
        );

        if (!frontmatter.metadata().tags().isEmpty()) {
            warnings.add("frontmatter tags는 원본 content_source에 보존되며 별도 UI에는 아직 반영되지 않습니다.");
        }
        if (StringUtils.hasText(frontmatter.metadata().summary())) {
            warnings.add("frontmatter summary는 원본 content_source에 보존되며 별도 UI에는 아직 반영되지 않습니다.");
        }

        return new ArticleImportCandidate(
            filePath,
            markdownPath,
            normalizeText(title),
            normalizeText(boardSlug),
            normalizeText(visibility),
            normalizeText(categoryName),
            frontmatter.contentSource(),
            warnings,
            errors
        );
    }

    private Map<String, byte[]> readZipEntries(MultipartFile file) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalizedPath = normalizeZipPath(entry.getName());
                if (!StringUtils.hasText(normalizedPath) || isIgnoredSystemPath(normalizedPath)) {
                    continue;
                }
                entries.put(normalizedPath, readEntry(zipInputStream));
            }
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_ZIP_READ_FAILED);
        }
        return entries;
    }

    private Map<String, String> readTextEntries(Map<String, byte[]> zipEntries) {
        Map<String, String> textEntries = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : zipEntries.entrySet()) {
            if (!isSupportedTextEntry(entry.getKey())) {
                continue;
            }
            textEntries.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
        }
        return textEntries;
    }

    private byte[] readEntry(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        return outputStream.toByteArray();
    }

    private boolean isSupportedTextEntry(String path) {
        String lowerCasePath = path.toLowerCase(Locale.ROOT);
        return MARKDOWN_EXTENSIONS.stream().anyMatch(lowerCasePath::endsWith)
            || YAML_EXTENSIONS.stream().anyMatch(lowerCasePath::endsWith);
    }

    private boolean isMarkdownCandidateForAutoScan(String path) {
        String lowerCasePath = path.toLowerCase(Locale.ROOT);
        if (YAML_EXTENSIONS.stream().anyMatch(lowerCasePath::endsWith)) {
            return false;
        }
        if (!MARKDOWN_EXTENSIONS.stream().anyMatch(lowerCasePath::endsWith)) {
            return false;
        }
        return !isIgnoredSystemPath(path);
    }

    private boolean isIgnoredSystemPath(String path) {
        String normalized = normalizeZipPath(path);
        if (normalized.startsWith("__MACOSX/")) {
            return true;
        }
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.startsWith(".") && !segment.equals(".well-known")) {
                return true;
            }
        }
        return false;
    }

    private String resolveManifestPath(Set<String> paths) {
        List<String> candidates = paths.stream()
            .filter(path -> path.endsWith("/manifest.yml")
                || path.endsWith("/manifest.yaml")
                || "manifest.yml".equals(path)
                || "manifest.yaml".equals(path))
            .sorted()
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_MANIFEST_SINGLE);
        }
        return candidates.get(0);
    }

    private FrontmatterResult parseFrontmatter(String rawContent) {
        String content = stripBom(rawContent);
        if (!content.startsWith("---")) {
            return new FrontmatterResult(content, ArticleFrontmatterMetadata.empty(), List.of(), List.of());
        }

        String[] lines = content.split("\\R", -1);
        if (lines.length == 0 || !"---".equals(lines[0].trim())) {
            return new FrontmatterResult(content, ArticleFrontmatterMetadata.empty(), List.of(), List.of());
        }

        int closingIndex = -1;
        for (int index = 1; index < lines.length; index += 1) {
            if ("---".equals(lines[index].trim()) || "...".equals(lines[index].trim())) {
                closingIndex = index;
                break;
            }
        }

        if (closingIndex < 0) {
            return new FrontmatterResult(
                content,
                ArticleFrontmatterMetadata.empty(),
                List.of("frontmatter 종료 구분자가 없어 원본 전체를 Markdown으로 사용합니다."),
                List.of()
            );
        }

        String yamlBlock = String.join("\n", List.of(lines).subList(1, closingIndex));
        Object loaded;
        try {
            loaded = yaml.load(yamlBlock);
        } catch (RuntimeException exception) {
            return new FrontmatterResult(
                content,
                ArticleFrontmatterMetadata.empty(),
                List.of("frontmatter 파싱에 실패해 원본 전체를 Markdown으로 사용합니다."),
                List.of()
            );
        }

        if (loaded == null) {
            return new FrontmatterResult(content, ArticleFrontmatterMetadata.empty(), List.of(), List.of());
        }
        if (!(loaded instanceof Map<?, ?> rawMap)) {
            return new FrontmatterResult(
                content,
                ArticleFrontmatterMetadata.empty(),
                List.of("frontmatter 형식이 올바르지 않아 원본 전체를 Markdown으로 사용합니다."),
                List.of()
            );
        }

        Map<String, Object> metadataMap = castMap(rawMap);
        ArticleFrontmatterMetadata metadata = new ArticleFrontmatterMetadata(
            normalizeText(resolveString(metadataMap, "title")),
            resolveStringList(metadataMap, "tags"),
            normalizeText(resolveString(metadataMap, "boardSlug", "board_slug", "board-slug")),
            normalizeText(resolveString(metadataMap, "visibility")),
            normalizeText(resolveString(metadataMap, "categoryName", "category_name", "category-name", "category")),
            normalizeText(resolveString(metadataMap, "summary"))
        );
        return new FrontmatterResult(content, metadata, List.of(), List.of());
    }

    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return Map.of();
        }
        return asMap(value, key + " 형식이 올바르지 않습니다.");
    }

    private List<?> getList(Object value, String message) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        throw new ApiException(ErrorCode.ARTICLE_IMPORT_VALIDATION_FAILED, message);
    }

    private Map<String, Object> asMap(Object value, String message) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_VALIDATION_FAILED, message);
        }
        return castMap(rawMap);
    }

    private Map<String, Object> castMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = Objects.toString(entry.getKey(), "").trim();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private String resolveString(Map<String, Object> source, String... candidateKeys) {
        for (String candidateKey : candidateKeys) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (canonicalizeKey(entry.getKey()).equals(canonicalizeKey(candidateKey))) {
                    return entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
                }
            }
        }
        return null;
    }

    private String readString(Map<String, Object> source, String... candidateKeys) {
        return normalizeText(resolveString(source, candidateKeys));
    }

    private List<String> resolveStringList(Map<String, Object> source, String... candidateKeys) {
        for (String candidateKey : candidateKeys) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (!canonicalizeKey(entry.getKey()).equals(canonicalizeKey(candidateKey))) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof Iterable<?> iterable) {
                    List<String> result = new ArrayList<>();
                    for (Object item : iterable) {
                        String normalized = normalizeText(item == null ? null : String.valueOf(item));
                        if (StringUtils.hasText(normalized)) {
                            result.add(normalized);
                        }
                    }
                    return result;
                }
                String normalized = normalizeText(value == null ? null : String.valueOf(value));
                return StringUtils.hasText(normalized) ? List.of(normalized) : List.of();
            }
        }
        return List.of();
    }

    private String canonicalizeKey(String key) {
        return key == null ? "" : key.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private String stripBom(String value) {
        if (value != null && value.startsWith("\uFEFF")) {
            return value.substring(1);
        }
        return value;
    }

    private String extractDirectory(String path) {
        int separatorIndex = path.lastIndexOf('/');
        if (separatorIndex < 0) {
            return "";
        }
        return path.substring(0, separatorIndex);
    }

    private String resolveRelativePath(String baseDirectory, String relativePath) {
        if (!StringUtils.hasText(baseDirectory)) {
            return normalizeZipPath(relativePath);
        }
        return normalizeZipPath(baseDirectory + "/" + relativePath);
    }

    private String normalizeZipPath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return "";
        }
        String normalized = rawPath.replace('\\', '/').trim();
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : normalized.split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new ApiException(ErrorCode.ARTICLE_IMPORT_ZIP_PATH_INVALID, rawPath);
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(segment);
        }
        return String.join("/", segments);
    }

    private String deriveTitleFromPath(String filePath) {
        String normalized = normalizeZipPath(filePath);
        int separatorIndex = normalized.lastIndexOf('/');
        String fileName = separatorIndex >= 0 ? normalized.substring(separatorIndex + 1) : normalized;
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        return normalizeText(baseName.replace('-', ' ').replace('_', ' '));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeText(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ArticleImportBundle(
        String sourceFileName,
        Map<String, byte[]> zipEntries,
        List<ArticleImportCandidate> articles
    ) {
    }

    public record ArticleImportCandidate(
        String filePath,
        String markdownPath,
        String title,
        String boardSlug,
        String visibility,
        String categoryName,
        String contentSource,
        List<String> warnings,
        List<String> errors
    ) {
    }

    private record FrontmatterResult(
        String contentSource,
        ArticleFrontmatterMetadata metadata,
        List<String> warnings,
        List<String> errors
    ) {
    }

    private record ArticleFrontmatterMetadata(
        String title,
        List<String> tags,
        String boardSlug,
        String visibility,
        String categoryName,
        String summary
    ) {
        private static ArticleFrontmatterMetadata empty() {
            return new ArticleFrontmatterMetadata(null, List.of(), null, null, null, null);
        }
    }
}
