package com.mocktalkback.domain.article.service;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.mocktalkback.domain.article.dto.ArticleCreateRequest;
import com.mocktalkback.domain.article.dto.ArticleImportExecuteItemResponse;
import com.mocktalkback.domain.article.dto.ArticleImportExecuteResponse;
import com.mocktalkback.domain.article.dto.ArticleImportPreviewItemResponse;
import com.mocktalkback.domain.article.dto.ArticleImportPreviewResponse;
import com.mocktalkback.domain.article.dto.ArticleResponse;
import com.mocktalkback.domain.article.entity.ArticleCategoryEntity;
import com.mocktalkback.domain.article.repository.ArticleCategoryRepository;
import com.mocktalkback.domain.article.service.ArticleImportBundleParser.ArticleImportBundle;
import com.mocktalkback.domain.article.service.ArticleImportBundleParser.ArticleImportCandidate;
import com.mocktalkback.domain.article.type.ArticleContentFormat;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.repository.BoardMemberRepository;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.common.policy.BoardAccessPolicy;
import com.mocktalkback.domain.file.dto.FileResponse;
import com.mocktalkback.domain.role.type.ContentVisibility;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import com.mocktalkback.global.i18n.ApiMessageResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleImportService {

    private static final long MAX_ASSET_SIZE = 50L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "svg");
    private static final Set<String> SUPPORTED_VIDEO_EXTENSIONS = Set.of("mp4", "webm", "ogg");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern HTML_VIDEO_SRC_PATTERN = Pattern.compile("<video\\b[^>]*\\bsrc\\s*=\\s*(['\"])(.*?)\\1[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_SOURCE_SRC_PATTERN = Pattern.compile("<source\\b[^>]*\\bsrc\\s*=\\s*(['\"])(.*?)\\1[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile("!youtube\\[(.+?)]");
    private static final Pattern FILE_VIEW_PATTERN = Pattern.compile("/api/files/(\\d+)/view");
    private static final Pattern FRONTMATTER_BOUNDARY_PATTERN = Pattern.compile("^---\\s*$");
    private static final Pattern FRONTMATTER_END_PATTERN = Pattern.compile("^(---|\\.\\.\\.)\\s*$");
    private static final Pattern TOP_LEVEL_KEY_PATTERN = Pattern.compile("^([A-Za-z0-9_-]+)\\s*:");
    private static final Set<String> MANAGED_FRONTMATTER_KEYS = Set.of(
        "title", "boardslug", "visibility", "categoryname", "category_name", "category-name", "category"
    );

    private final ArticleImportBundleParser articleImportBundleParser;
    private final ArticleImportAssetStorageService articleImportAssetStorageService;
    private final ArticleService articleService;
    private final BoardRepository boardRepository;
    private final BoardMemberRepository boardMemberRepository;
    private final ArticleCategoryRepository articleCategoryRepository;
    private final UserRepository userRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final CurrentUserService currentUserService;
    private final ApiMessageResolver messageResolver;

    @Transactional(readOnly = true)
    public ArticleImportPreviewResponse preview(MultipartFile file, boolean autoCreateMissingCategories) {
        ArticleImportBundle bundle = articleImportBundleParser.parse(file);
        UserEntity actor = getCurrentUser();
        List<PreparedImportArticle> articles = prepareArticles(bundle, actor, autoCreateMissingCategories);
        int executableCount = (int) articles.stream().filter(PreparedImportArticle::executable).count();
        List<ArticleImportPreviewItemResponse> items = articles.stream()
            .map(article -> new ArticleImportPreviewItemResponse(
                article.filePath(),
                article.title(),
                article.boardSlug(),
                article.categoryName(),
                article.visibility(),
                article.contentAnalysis().relativeImageCount(),
                article.contentAnalysis().relativeVideoCount(),
                article.contentAnalysis().youtubeEmbedCount(),
                article.contentAnalysis().missingAssetCount(),
                article.contentAnalysis().oversizedAssetCount(),
                article.contentAnalysis().unsupportedAssetCount(),
                article.executable(),
                article.warnings(),
                article.errors()
            ))
            .toList();
        return new ArticleImportPreviewResponse(executableCount > 0, items.size(), executableCount, items.size() - executableCount, items);
    }

    public ArticleImportExecuteResponse execute(MultipartFile file, boolean autoCreateMissingCategories) {
        ArticleImportBundle bundle = articleImportBundleParser.parse(file);
        UserEntity actor = getCurrentUser();
        List<PreparedImportArticle> articles = prepareArticles(bundle, actor, autoCreateMissingCategories);

        List<ArticleImportExecuteItemResponse> items = new ArrayList<>();
        int successCount = 0;
        for (PreparedImportArticle article : articles) {
            List<String> warnings = new ArrayList<>(article.warnings());
            List<String> errors = new ArrayList<>(article.errors());
            Long createdArticleId = null;
            boolean created = false;
            Long categoryId = article.categoryId();
            int uploadedImageCount = 0;
            int uploadedVideoCount = 0;

            if (article.executable()) {
                try {
                    if (categoryId == null && autoCreateMissingCategories && article.boardId() != null && article.categoryName() != null) {
                        categoryId = ensureCategory(article.boardId(), article.categoryName());
                    }
                    MaterializedContent materializedContent = materializeContentSource(
                        article.contentSource(),
                        article.markdownPath(),
                        bundle.zipEntries(),
                        actor.getId()
                    );
                    uploadedImageCount = materializedContent.uploadedImageCount();
                    uploadedVideoCount = materializedContent.uploadedVideoCount();
                    String finalContentSource = mergeManagedMarkdownFrontmatter(
                        materializedContent.contentSource(),
                        new ManagedFrontmatterValues(article.title(), article.boardSlug(), article.visibility(), article.categoryName())
                    );
                    ArticleResponse response = articleService.create(new ArticleCreateRequest(
                        article.boardId(),
                        actor.getId(),
                        categoryId,
                        article.visibilityEnum(),
                        article.title(),
                        finalContentSource,
                        ArticleContentFormat.MARKDOWN,
                        false,
                        extractFileIdsFromContent(finalContentSource)
                    ));
                    createdArticleId = response.id();
                    created = true;
                    successCount += 1;
                } catch (RuntimeException exception) {
                    errors.add(resolveExecutionErrorMessage(exception));
                }
            }

            items.add(new ArticleImportExecuteItemResponse(
                article.filePath(),
                article.title(),
                article.boardSlug(),
                article.categoryName(),
                article.visibility(),
                uploadedImageCount,
                uploadedVideoCount,
                article.contentAnalysis().youtubeEmbedCount(),
                created,
                createdArticleId,
                warnings,
                errors
            ));
        }

        return new ArticleImportExecuteResponse(items.size(), successCount, items.size() - successCount, items);
    }

    private List<PreparedImportArticle> prepareArticles(ArticleImportBundle bundle, UserEntity actor, boolean autoCreateMissingCategories) {
        List<PreparedImportArticle> prepared = new ArrayList<>();
        for (ArticleImportCandidate candidate : bundle.articles()) {
            List<String> warnings = new ArrayList<>(candidate.warnings());
            List<String> errors = new ArrayList<>(candidate.errors());

            String title = normalizeText(candidate.title());
            if (title == null) {
                errors.add("제목을 확인할 수 없습니다.");
            } else if (title.length() > 255) {
                errors.add("제목은 255자를 초과할 수 없습니다.");
            }

            String contentSource = candidate.contentSource();
            String bodyContent = stripMarkdownFrontmatter(contentSource);
            if (bodyContent == null || bodyContent.isBlank()) {
                errors.add("본문이 비어 있습니다.");
            }

            ContentAnalysis contentAnalysis = analyzeContentSource(contentSource, candidate.markdownPath(), bundle.zipEntries());
            warnings.addAll(contentAnalysis.warnings());
            errors.addAll(contentAnalysis.errors());

            String boardSlug = normalizeText(candidate.boardSlug());
            String categoryName = normalizeText(candidate.categoryName());
            BoardEntity board = null;
            BoardMemberEntity member = null;
            ArticleCategoryEntity category = null;
            if (boardSlug == null) {
                errors.add("대상 게시판 slug가 없습니다.");
            } else {
                board = boardRepository.findBySlugAndDeletedAtIsNull(boardSlug).orElse(null);
                if (board == null) {
                    errors.add("게시판을 찾을 수 없습니다: " + boardSlug);
                } else {
                    member = boardMemberRepository.findByUserIdAndBoardId(actor.getId(), board.getId()).orElse(null);
                    if (!boardAccessPolicy.canAccessBoard(board, actor, member)) {
                        errors.add("게시판에 접근할 수 없습니다: " + boardSlug);
                    } else {
                        try {
                            boardAccessPolicy.requireCanWrite(board, actor, member);
                        } catch (AccessDeniedException exception) {
                            errors.add(exception.getMessage());
                        }
                        if (categoryName != null) {
                            category = articleCategoryRepository.findByBoardIdAndCategoryNameIgnoreCase(board.getId(), categoryName).orElse(null);
                            if (category == null) {
                                if (autoCreateMissingCategories) {
                                    warnings.add("게시판 카테고리가 없어 실행 시 자동 생성합니다: " + categoryName);
                                } else {
                                    errors.add("게시판 카테고리를 찾을 수 없습니다: " + categoryName);
                                }
                            }
                        }
                    }
                }
            }

            ContentVisibility visibility = null;
            String visibilityName = normalizeText(candidate.visibility());
            if (visibilityName == null) {
                visibilityName = ContentVisibility.PUBLIC.name();
            }
            try {
                visibility = ContentVisibility.valueOf(visibilityName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                errors.add("지원하지 않는 공개 범위입니다: " + visibilityName);
            }
            if (board != null && visibility != null) {
                EnumSet<ContentVisibility> allowed = boardAccessPolicy.resolveAllowedVisibilities(board, actor, member);
                if (!allowed.contains(visibility)) {
                    errors.add("현재 계정으로 선택할 수 없는 공개 범위입니다: " + visibility.name());
                }
            }

            prepared.add(new PreparedImportArticle(
                candidate.filePath(),
                candidate.markdownPath(),
                title,
                boardSlug,
                categoryName,
                visibilityName != null ? visibilityName.toUpperCase(Locale.ROOT) : null,
                contentSource,
                board != null ? board.getId() : null,
                category != null ? category.getId() : null,
                visibility,
                contentAnalysis,
                warnings,
                errors,
                errors.isEmpty()
            ));
        }
        return prepared;
    }

    private ContentAnalysis analyzeContentSource(String contentSource, String markdownPath, Map<String, byte[]> zipEntries) {
        if (!StringUtils.hasText(contentSource) || !StringUtils.hasText(markdownPath) || zipEntries == null) {
            return ContentAnalysis.empty();
        }

        MarkdownSplit markdownSplit = splitMarkdownFrontmatter(contentSource);
        String body = markdownSplit.body();
        if (!StringUtils.hasText(body)) {
            return ContentAnalysis.empty();
        }

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int relativeImageCount = 0;
        int relativeVideoCount = 0;
        int youtubeEmbedCount = 0;
        int missingAssetCount = 0;
        int oversizedAssetCount = 0;
        int unsupportedAssetCount = 0;

        Matcher youtubeMatcher = YOUTUBE_PATTERN.matcher(body);
        while (youtubeMatcher.find()) {
            String rawValue = normalizeText(youtubeMatcher.group(1));
            if (resolveYouTubeVideoId(rawValue) != null) {
                youtubeEmbedCount += 1;
            } else {
                warnings.add("유튜브 문법을 해석하지 못해 원문을 그대로 보존합니다: " + youtubeMatcher.group(0));
            }
        }

        Matcher imageMatcher = MARKDOWN_IMAGE_PATTERN.matcher(body);
        while (imageMatcher.find()) {
            String rawPath = normalizeReferencePath(imageMatcher.group(1));
            if (!isRelativeAssetReference(rawPath)) {
                continue;
            }
            relativeImageCount += 1;
            AssetValidationResult validation = validateAsset(rawPath, markdownPath, AssetKind.IMAGE, zipEntries);
            if (validation.errorType() == AssetValidationError.NONE) {
                continue;
            }
            if (validation.errorType() == AssetValidationError.MISSING) {
                missingAssetCount += 1;
            } else if (validation.errorType() == AssetValidationError.OVERSIZED) {
                oversizedAssetCount += 1;
            } else {
                unsupportedAssetCount += 1;
            }
            errors.add(validation.message());
        }

        Matcher videoMatcher = HTML_VIDEO_SRC_PATTERN.matcher(body);
        while (videoMatcher.find()) {
            String rawPath = normalizeReferencePath(videoMatcher.group(2));
            if (!isRelativeAssetReference(rawPath)) {
                continue;
            }
            relativeVideoCount += 1;
            AssetValidationResult validation = validateAsset(rawPath, markdownPath, AssetKind.VIDEO, zipEntries);
            if (validation.errorType() == AssetValidationError.NONE) {
                continue;
            }
            if (validation.errorType() == AssetValidationError.MISSING) {
                missingAssetCount += 1;
            } else if (validation.errorType() == AssetValidationError.OVERSIZED) {
                oversizedAssetCount += 1;
            } else {
                unsupportedAssetCount += 1;
            }
            errors.add(validation.message());
        }

        Matcher sourceMatcher = HTML_SOURCE_SRC_PATTERN.matcher(body);
        while (sourceMatcher.find()) {
            String rawPath = normalizeReferencePath(sourceMatcher.group(2));
            if (!isRelativeAssetReference(rawPath)) {
                continue;
            }
            relativeVideoCount += 1;
            AssetValidationResult validation = validateAsset(rawPath, markdownPath, AssetKind.VIDEO, zipEntries);
            if (validation.errorType() == AssetValidationError.NONE) {
                continue;
            }
            if (validation.errorType() == AssetValidationError.MISSING) {
                missingAssetCount += 1;
            } else if (validation.errorType() == AssetValidationError.OVERSIZED) {
                oversizedAssetCount += 1;
            } else {
                unsupportedAssetCount += 1;
            }
            errors.add(validation.message());
        }

        return new ContentAnalysis(
            relativeImageCount,
            relativeVideoCount,
            youtubeEmbedCount,
            missingAssetCount,
            oversizedAssetCount,
            unsupportedAssetCount,
            warnings,
            errors
        );
    }

    private MaterializedContent materializeContentSource(
        String contentSource,
        String markdownPath,
        Map<String, byte[]> zipEntries,
        Long ownerId
    ) {
        if (!StringUtils.hasText(contentSource)) {
            return new MaterializedContent(contentSource, 0, 0);
        }

        MarkdownSplit split = splitMarkdownFrontmatter(contentSource);
        Map<String, UploadedAsset> uploadedAssets = new LinkedHashMap<>();
        String body = split.body();
        body = replaceMarkdownImageSources(body, markdownPath, zipEntries, ownerId, uploadedAssets);
        body = replaceHtmlVideoSources(body, markdownPath, zipEntries, ownerId, uploadedAssets);
        String rebuiltSource = rebuildMarkdownWithBody(split, body);

        int uploadedImageCount = 0;
        int uploadedVideoCount = 0;
        for (UploadedAsset uploadedAsset : uploadedAssets.values()) {
            if (uploadedAsset.kind() == AssetKind.IMAGE) {
                uploadedImageCount += 1;
            } else {
                uploadedVideoCount += 1;
            }
        }
        return new MaterializedContent(rebuiltSource, uploadedImageCount, uploadedVideoCount);
    }

    private String replaceMarkdownImageSources(
        String markdown,
        String markdownPath,
        Map<String, byte[]> zipEntries,
        Long ownerId,
        Map<String, UploadedAsset> uploadedAssets
    ) {
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rawPath = normalizeReferencePath(matcher.group(1));
            String replacement = matcher.group(0);
            if (isRelativeAssetReference(rawPath)) {
                UploadedAsset uploadedAsset = uploadAsset(rawPath, markdownPath, AssetKind.IMAGE, zipEntries, ownerId, uploadedAssets);
                replacement = replacement.replace(matcher.group(1), uploadedAsset.viewUrl());
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceHtmlVideoSources(
        String markdown,
        String markdownPath,
        Map<String, byte[]> zipEntries,
        Long ownerId,
        Map<String, UploadedAsset> uploadedAssets
    ) {
        String replaced = replaceHtmlSourceAttribute(markdown, HTML_VIDEO_SRC_PATTERN, markdownPath, zipEntries, ownerId, uploadedAssets);
        return replaceHtmlSourceAttribute(replaced, HTML_SOURCE_SRC_PATTERN, markdownPath, zipEntries, ownerId, uploadedAssets);
    }

    private String replaceHtmlSourceAttribute(
        String markdown,
        Pattern pattern,
        String markdownPath,
        Map<String, byte[]> zipEntries,
        Long ownerId,
        Map<String, UploadedAsset> uploadedAssets
    ) {
        Matcher matcher = pattern.matcher(markdown);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rawPath = normalizeReferencePath(matcher.group(2));
            String replacement = matcher.group(0);
            if (isRelativeAssetReference(rawPath)) {
                UploadedAsset uploadedAsset = uploadAsset(rawPath, markdownPath, AssetKind.VIDEO, zipEntries, ownerId, uploadedAssets);
                replacement = replacement.replace(matcher.group(2), uploadedAsset.viewUrl());
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private UploadedAsset uploadAsset(
        String rawPath,
        String markdownPath,
        AssetKind assetKind,
        Map<String, byte[]> zipEntries,
        Long ownerId,
        Map<String, UploadedAsset> uploadedAssets
    ) {
        AssetValidationResult validation = validateAsset(rawPath, markdownPath, assetKind, zipEntries);
        if (validation.errorType() != AssetValidationError.NONE || validation.asset() == null) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_VALIDATION_FAILED, validation.message());
        }

        UploadedAsset existing = uploadedAssets.get(validation.asset().resolvedPath());
        if (existing != null) {
            return existing;
        }

        FileResponse fileResponse = articleImportAssetStorageService.storeEditorAsset(
            ownerId,
            validation.asset().originalFileName(),
            validation.asset().bytes(),
            validation.asset().mimeType()
        );
        if (fileResponse.id() == null) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_ASSET_FILE_MISSING);
        }

        UploadedAsset uploadedAsset = new UploadedAsset(
            validation.asset().resolvedPath(),
            assetKind,
            fileResponse.id(),
            "/api/files/" + fileResponse.id() + "/view"
        );
        uploadedAssets.put(validation.asset().resolvedPath(), uploadedAsset);
        return uploadedAsset;
    }

    private AssetValidationResult validateAsset(
        String rawPath,
        String markdownPath,
        AssetKind assetKind,
        Map<String, byte[]> zipEntries
    ) {
        String resolvedPath;
        try {
            resolvedPath = resolveRelativeAssetPath(markdownPath, rawPath);
        } catch (IllegalArgumentException exception) {
            return new AssetValidationResult(null, AssetValidationError.UNSUPPORTED, "assets 경로가 올바르지 않습니다: " + rawPath);
        }

        byte[] bytes = zipEntries.get(resolvedPath);
        if (bytes == null || bytes.length == 0) {
            String message = assetKind == AssetKind.IMAGE
                ? "assets 파일을 찾을 수 없습니다: " + rawPath
                : "동영상 파일을 찾을 수 없습니다: " + rawPath;
            return new AssetValidationResult(null, AssetValidationError.MISSING, message);
        }
        if (bytes.length > MAX_ASSET_SIZE) {
            return new AssetValidationResult(null, AssetValidationError.OVERSIZED, "파일 크기 제한을 초과했습니다(50MB): " + rawPath);
        }

        String mimeType = resolveMimeType(resolvedPath, assetKind);
        if (!StringUtils.hasText(mimeType)) {
            return new AssetValidationResult(null, AssetValidationError.UNSUPPORTED, "지원하지 않는 본문 assets 형식입니다: " + rawPath);
        }

        return new AssetValidationResult(
            new ResolvedAsset(resolvedPath, extractFileName(resolvedPath), mimeType, bytes),
            AssetValidationError.NONE,
            null
        );
    }

    private Long ensureCategory(Long boardId, String categoryName) {
        ArticleCategoryEntity existing = articleCategoryRepository.findByBoardIdAndCategoryNameIgnoreCase(boardId, categoryName).orElse(null);
        if (existing != null) {
            return existing.getId();
        }

        BoardEntity board = boardRepository.findByIdAndDeletedAtIsNull(boardId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_NOT_FOUND));

        try {
            ArticleCategoryEntity created = articleCategoryRepository.save(
                ArticleCategoryEntity.builder()
                    .board(board)
                    .categoryName(categoryName)
                    .build()
            );
            return created.getId();
        } catch (DataIntegrityViolationException exception) {
            return articleCategoryRepository.findByBoardIdAndCategoryNameIgnoreCase(boardId, categoryName)
                .map(ArticleCategoryEntity::getId)
                .orElseThrow(() -> exception);
        }
    }

    private UserEntity getCurrentUser() {
        Long userId = currentUserService.getUserId();
        return userRepository.findByIdWithRoleAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private String resolveExecutionErrorMessage(RuntimeException exception) {
        if (exception instanceof ApiException apiException) {
            return messageResolver.resolve(apiException.getErrorCode(), apiException.getArgs());
        }
        String message = normalizeText(exception.getMessage());
        return message == null ? "게시글 생성에 실패했습니다." : message;
    }

    private String stripMarkdownFrontmatter(String contentSource) {
        return splitMarkdownFrontmatter(contentSource).body();
    }

    private MarkdownSplit splitMarkdownFrontmatter(String contentSource) {
        if (contentSource == null) {
            return new MarkdownSplit("", false, List.of(), "");
        }
        String normalizedSource = contentSource.startsWith("\uFEFF") ? contentSource.substring(1) : contentSource;
        String[] lines = normalizedSource.split("\\R", -1);
        if (lines.length == 0 || !FRONTMATTER_BOUNDARY_PATTERN.matcher(lines[0].trim()).matches()) {
            return new MarkdownSplit(normalizedSource, false, List.of(), normalizedSource);
        }

        int closingIndex = -1;
        for (int index = 1; index < lines.length; index += 1) {
            if (FRONTMATTER_END_PATTERN.matcher(lines[index].trim()).matches()) {
                closingIndex = index;
                break;
            }
        }
        if (closingIndex < 0) {
            return new MarkdownSplit(normalizedSource, false, List.of(), normalizedSource);
        }

        String body = String.join("\n", List.of(lines).subList(closingIndex + 1, lines.length)).replaceFirst("^(\\r?\\n)+", "");
        return new MarkdownSplit(normalizedSource, true, List.of(lines).subList(1, closingIndex), body);
    }

    private String rebuildMarkdownWithBody(MarkdownSplit split, String body) {
        if (!split.hasFrontmatter()) {
            return body;
        }
        List<String> lines = new ArrayList<>();
        lines.add("---");
        lines.addAll(split.frontmatterLines());
        lines.add("---");
        if (StringUtils.hasText(body)) {
            lines.add("");
            lines.add(body);
        }
        return String.join("\n", lines);
    }

    private String mergeManagedMarkdownFrontmatter(String source, ManagedFrontmatterValues values) {
        MarkdownSplit split = splitMarkdownFrontmatter(source);
        List<String> managedLines = buildManagedFrontmatterLines(values);
        if (!split.hasFrontmatter()) {
            if (managedLines.isEmpty()) {
                return split.normalizedSource();
            }
            if (!StringUtils.hasText(split.normalizedSource())) {
                return String.join("\n", List.of("---", String.join("\n", managedLines), "---"));
            }
            return String.join("\n", List.of("---", String.join("\n", managedLines), "---", "", split.normalizedSource()));
        }

        List<String> preservedLines = parseFrontmatterEntries(split.frontmatterLines()).stream()
            .filter(entry -> entry.normalizedKey() == null || !MANAGED_FRONTMATTER_KEYS.contains(entry.normalizedKey()))
            .flatMap(entry -> entry.lines().stream())
            .toList();

        List<String> nextLines = new ArrayList<>(managedLines);
        nextLines.addAll(preservedLines);
        if (nextLines.isEmpty()) {
            return split.body();
        }

        List<String> rebuilt = new ArrayList<>();
        rebuilt.add("---");
        rebuilt.addAll(nextLines);
        rebuilt.add("---");
        if (StringUtils.hasText(split.body())) {
            rebuilt.add("");
            rebuilt.add(split.body());
        }
        return String.join("\n", rebuilt);
    }

    private List<FrontmatterEntry> parseFrontmatterEntries(List<String> frontmatterLines) {
        List<FrontmatterEntry> entries = new ArrayList<>();
        int index = 0;
        while (index < frontmatterLines.size()) {
            String line = frontmatterLines.get(index);
            Matcher keyMatcher = TOP_LEVEL_KEY_PATTERN.matcher(line);
            if (!keyMatcher.find()) {
                entries.add(new FrontmatterEntry(null, null, List.of(line)));
                index += 1;
                continue;
            }
            String key = keyMatcher.group(1);
            List<String> lines = new ArrayList<>();
            lines.add(line);
            index += 1;
            while (index < frontmatterLines.size()) {
                String nextLine = frontmatterLines.get(index);
                if (TOP_LEVEL_KEY_PATTERN.matcher(nextLine).find()) {
                    break;
                }
                lines.add(nextLine);
                index += 1;
            }
            entries.add(new FrontmatterEntry(key, canonicalizeFrontmatterKey(key), lines));
        }
        return entries;
    }

    private List<String> buildManagedFrontmatterLines(ManagedFrontmatterValues values) {
        List<String> lines = new ArrayList<>();
        if (StringUtils.hasText(values.title())) {
            lines.add("title: " + quoteYaml(values.title()));
        }
        if (StringUtils.hasText(values.boardSlug())) {
            lines.add("boardSlug: " + quoteYaml(values.boardSlug()));
        }
        if (StringUtils.hasText(values.visibility())) {
            lines.add("visibility: " + quoteYaml(values.visibility().toUpperCase(Locale.ROOT)));
        }
        if (StringUtils.hasText(values.categoryName())) {
            lines.add("categoryName: " + quoteYaml(values.categoryName()));
        }
        return lines;
    }

    private List<Long> extractFileIdsFromContent(String contentSource) {
        if (!StringUtils.hasText(contentSource)) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        Matcher matcher = FILE_VIEW_PATTERN.matcher(contentSource);
        while (matcher.find()) {
            try {
                ids.add(Long.valueOf(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // 파일 id가 숫자가 아니면 무시합니다.
            }
        }
        return new ArrayList<>(ids);
    }

    private String quoteYaml(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String canonicalizeFrontmatterKey(String key) {
        return key == null ? null : key.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeReferencePath(String rawPath) {
        if (rawPath == null) {
            return null;
        }
        String normalized = rawPath.trim();
        if (normalized.startsWith("<") && normalized.endsWith(">") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalizeText(normalized);
    }

    private boolean isRelativeAssetReference(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return false;
        }
        if (rawPath.startsWith("#") || rawPath.startsWith("/") || rawPath.startsWith("//") || rawPath.startsWith("data:")) {
            return false;
        }
        return !rawPath.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$");
    }

    private String resolveRelativeAssetPath(String markdownPath, String rawPath) {
        String baseDirectory = extractDirectory(markdownPath);
        if (!StringUtils.hasText(baseDirectory)) {
            return normalizeZipPath(rawPath);
        }
        return normalizeZipPath(baseDirectory + "/" + rawPath);
    }

    private String extractDirectory(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        int separatorIndex = path.lastIndexOf('/');
        return separatorIndex < 0 ? "" : path.substring(0, separatorIndex);
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

    private String resolveMimeType(String path, AssetKind expectedKind) {
        String extension = resolveExtension(path);
        if (!StringUtils.hasText(extension)) {
            return null;
        }
        String lowerCaseExtension = extension.toLowerCase(Locale.ROOT);
        if (expectedKind == AssetKind.IMAGE && SUPPORTED_IMAGE_EXTENSIONS.contains(lowerCaseExtension)) {
            return switch (lowerCaseExtension) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                case "svg" -> "image/svg+xml";
                default -> null;
            };
        }
        if (expectedKind == AssetKind.VIDEO && SUPPORTED_VIDEO_EXTENSIONS.contains(lowerCaseExtension)) {
            return switch (lowerCaseExtension) {
                case "mp4" -> "video/mp4";
                case "webm" -> "video/webm";
                case "ogg" -> "video/ogg";
                default -> null;
            };
        }
        return null;
    }

    private String resolveExtension(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == path.length() - 1) {
            return null;
        }
        return path.substring(dotIndex + 1);
    }

    private String extractFileName(String path) {
        if (!StringUtils.hasText(path)) {
            return "file";
        }
        int slashIndex = path.lastIndexOf('/');
        return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
    }

    private String resolveYouTubeVideoId(String rawValue) {
        String normalized = normalizeText(rawValue);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.matches("^[A-Za-z0-9_-]{11}$")) {
            return normalized;
        }
        try {
            URI uri = URI.create(normalized);
            String host = normalizeText(uri.getHost());
            if (!StringUtils.hasText(host)) {
                return null;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            String path = normalizeText(uri.getPath());
            if ("youtu.be".equals(normalizedHost) && StringUtils.hasText(path)) {
                String candidate = path.startsWith("/") ? path.substring(1) : path;
                return candidate.matches("^[A-Za-z0-9_-]{11}$") ? candidate : null;
            }
            if (normalizedHost.endsWith("youtube.com") || normalizedHost.endsWith("youtube-nocookie.com")) {
                if (StringUtils.hasText(path) && path.startsWith("/embed/")) {
                    String candidate = path.substring("/embed/".length());
                    int slashIndex = candidate.indexOf('/');
                    if (slashIndex >= 0) {
                        candidate = candidate.substring(0, slashIndex);
                    }
                    return candidate.matches("^[A-Za-z0-9_-]{11}$") ? candidate : null;
                }
                if (StringUtils.hasText(path) && path.startsWith("/shorts/")) {
                    String candidate = path.substring("/shorts/".length());
                    int slashIndex = candidate.indexOf('/');
                    if (slashIndex >= 0) {
                        candidate = candidate.substring(0, slashIndex);
                    }
                    return candidate.matches("^[A-Za-z0-9_-]{11}$") ? candidate : null;
                }
                String query = normalizeText(uri.getQuery());
                if (StringUtils.hasText(query)) {
                    for (String queryPart : query.split("&")) {
                        String[] pair = queryPart.split("=", 2);
                        if (pair.length == 2 && "v".equals(pair[0])) {
                            return pair[1].matches("^[A-Za-z0-9_-]{11}$") ? pair[1] : null;
                        }
                    }
                }
            }
            return null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PreparedImportArticle(
        String filePath,
        String markdownPath,
        String title,
        String boardSlug,
        String categoryName,
        String visibility,
        String contentSource,
        Long boardId,
        Long categoryId,
        ContentVisibility visibilityEnum,
        ContentAnalysis contentAnalysis,
        List<String> warnings,
        List<String> errors,
        boolean executable
    ) {
    }

    private record ContentAnalysis(
        int relativeImageCount,
        int relativeVideoCount,
        int youtubeEmbedCount,
        int missingAssetCount,
        int oversizedAssetCount,
        int unsupportedAssetCount,
        List<String> warnings,
        List<String> errors
    ) {
        private static ContentAnalysis empty() {
            return new ContentAnalysis(0, 0, 0, 0, 0, 0, List.of(), List.of());
        }
    }

    private record MaterializedContent(
        String contentSource,
        int uploadedImageCount,
        int uploadedVideoCount
    ) {
    }

    private record MarkdownSplit(
        String normalizedSource,
        boolean hasFrontmatter,
        List<String> frontmatterLines,
        String body
    ) {
    }

    private record FrontmatterEntry(
        String key,
        String normalizedKey,
        List<String> lines
    ) {
    }

    private record ManagedFrontmatterValues(
        String title,
        String boardSlug,
        String visibility,
        String categoryName
    ) {
    }

    private enum AssetKind {
        IMAGE,
        VIDEO
    }

    private enum AssetValidationError {
        NONE,
        MISSING,
        OVERSIZED,
        UNSUPPORTED
    }

    private record ResolvedAsset(
        String resolvedPath,
        String originalFileName,
        String mimeType,
        byte[] bytes
    ) {
    }

    private record AssetValidationResult(
        ResolvedAsset asset,
        AssetValidationError errorType,
        String message
    ) {
    }

    private record UploadedAsset(
        String resolvedPath,
        AssetKind kind,
        Long fileId,
        String viewUrl
    ) {
    }
}
