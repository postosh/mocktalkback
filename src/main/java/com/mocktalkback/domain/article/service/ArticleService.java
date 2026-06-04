package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import com.mocktalkback.domain.article.dto.ArticleBoardResponse;
import com.mocktalkback.domain.article.dto.ArticleBookmarkStatusResponse;
import com.mocktalkback.domain.article.dto.ArticleCategoryResponse;
import com.mocktalkback.domain.article.dto.ArticleDetailResponse;
import com.mocktalkback.domain.article.dto.ArticleEditorDetailResponse;
import com.mocktalkback.domain.article.dto.ArticlePreviewRequest;
import com.mocktalkback.domain.article.dto.ArticlePreviewResponse;
import com.mocktalkback.domain.article.dto.ArticleRecentItemResponse;
import com.mocktalkback.domain.article.dto.ArticleRecommendedItemResponse;
import com.mocktalkback.domain.article.dto.ArticleReactionSummaryResponse;
import com.mocktalkback.domain.article.dto.ArticleReactionToggleRequest;
import com.mocktalkback.domain.article.dto.ArticleSummaryResponse;
import com.mocktalkback.domain.article.dto.ArticleTrendingItemResponse;
import com.mocktalkback.domain.article.dto.BoardArticleListResponse;
import com.mocktalkback.domain.article.dto.ArticleCreateRequest;
import com.mocktalkback.domain.article.dto.ArticleResponse;
import com.mocktalkback.domain.article.dto.ArticleUpdateRequest;
import com.mocktalkback.domain.article.entity.ArticleCategoryEntity;
import com.mocktalkback.domain.article.entity.ArticleEntity;
import com.mocktalkback.domain.article.entity.ArticleFileEntity;
import com.mocktalkback.domain.article.entity.ArticleBookmarkEntity;
import com.mocktalkback.domain.article.entity.ArticleReactionEntity;
import com.mocktalkback.domain.article.mapper.ArticleMapper;
import com.mocktalkback.domain.article.policy.PublicArticleFeedPolicy;
import com.mocktalkback.domain.article.repository.ArticleCategoryRepository;
import com.mocktalkback.domain.article.repository.ArticleFileRepository;
import com.mocktalkback.domain.article.repository.ArticleBookmarkRepository;
import com.mocktalkback.domain.article.repository.ArticleReactionRepository;
import com.mocktalkback.domain.article.repository.ArticleReactionRepository.ArticleReactionCountView;
import com.mocktalkback.domain.article.repository.ArticleRepository;
import com.mocktalkback.domain.article.type.ArticleTrendingWindow;
import com.mocktalkback.domain.board.entity.BoardFileEntity;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.repository.BoardFileRepository;
import com.mocktalkback.domain.board.repository.BoardMemberRepository;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.comment.repository.CommentRepository;
import com.mocktalkback.domain.common.policy.AuthorDisplayResolver;
import com.mocktalkback.domain.common.policy.BoardAccessPolicy;
import com.mocktalkback.domain.common.policy.PageNormalizer;
import com.mocktalkback.domain.common.policy.SanctionGuard;
import com.mocktalkback.domain.file.dto.FileResponse;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.entity.FileVariantEntity;
import com.mocktalkback.domain.file.mapper.FileMapper;
import com.mocktalkback.domain.file.repository.FileRepository;
import com.mocktalkback.domain.file.repository.FileVariantRepository;
import com.mocktalkback.domain.file.service.FileStorage;
import com.mocktalkback.domain.file.service.TemporaryFilePolicy;
import com.mocktalkback.domain.file.type.FileClassCode;
import com.mocktalkback.domain.realtime.service.BoardRealtimeSseService;
import com.mocktalkback.domain.role.type.ContentVisibility;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;
import com.mocktalkback.global.common.dto.PageResponse;
import com.mocktalkback.global.common.dto.SliceResponse;
import com.mocktalkback.global.common.util.ActivityPointPolicy;
import com.mocktalkback.global.common.util.ReactionTypeValidator;
import com.mocktalkback.global.common.type.SortOrder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int PINNED_LIMIT = 5;
    private static final Sort ARTICLE_SORT = Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("updatedAt"),
        Sort.Order.desc("id")
    );

    private final ArticleRepository articleRepository;
    private final BoardRepository boardRepository;
    private final BoardMemberRepository boardMemberRepository;
    private final BoardFileRepository boardFileRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final ArticleCategoryRepository articleCategoryRepository;
    private final ArticleFileRepository articleFileRepository;
    private final ArticleBookmarkRepository articleBookmarkRepository;
    private final ArticleReactionRepository articleReactionRepository;
    private final ArticleMapper articleMapper;
    private final FileRepository fileRepository;
    private final FileMapper fileMapper;
    private final FileVariantRepository fileVariantRepository;
    private final FileStorage fileStorage;
    private final TemporaryFilePolicy temporaryFilePolicy;
    private final CurrentUserService currentUserService;
    private final ArticleContentService articleContentService;
    private final ArticleViewService articleViewService;
    private final ArticleTrendingService articleTrendingService;
    private final ArticleRecommendationService articleRecommendationService;
    private final BoardRealtimeSseService boardRealtimeSseService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final SanctionGuard sanctionGuard;
    private final PageNormalizer pageNormalizer;
    private final AuthorDisplayResolver authorDisplayResolver;
    private final PublicArticleFeedPolicy publicArticleFeedPolicy;

    @Transactional
    public ArticleResponse create(ArticleCreateRequest request) {
        Long actorUserId = currentUserService.getUserId();
        if (actorUserId == null || !actorUserId.equals(request.userId())) {
            throw new AccessDeniedException("Access Denied");
        }

        BoardEntity board = getBoard(request.boardId());
        UserEntity user = getUser(actorUserId);
        BoardMemberEntity member = boardMemberRepository.findByUserIdAndBoardId(actorUserId, board.getId())
            .orElse(null);
        if (!boardAccessPolicy.canAccessBoard(board, user, member)) {
            throw new ApiException(ErrorCode.BOARD_NOT_FOUND);
        }
        boardAccessPolicy.requireCanWrite(board, user, member);
        sanctionGuard.requireNotSanctioned(user, board);
        ArticleCategoryEntity category = getCategoryForBoard(request.categoryId(), board);
        ArticleContentService.RenderedContent renderedContent = articleContentService.render(
            request.contentSource(),
            request.contentFormat()
        );
        ArticleCreateRequest sanitizedRequest = new ArticleCreateRequest(
            request.boardId(),
            actorUserId,
            request.categoryId(),
            request.visibility(),
            request.title(),
            renderedContent.contentSource(),
            request.contentFormat(),
            request.notice(),
            request.fileIds()
        );
        ArticleEntity entity = articleMapper.toEntity(sanitizedRequest, board, user, category);
        entity.update(
            category,
            request.visibility(),
            request.title(),
            renderedContent.content(),
            renderedContent.contentSource(),
            request.contentFormat(),
            request.notice()
        );
        ArticleEntity saved = articleRepository.save(entity);
        attachArticleFiles(saved, sanitizedRequest.fileIds());
        user.changePoint(ActivityPointPolicy.CREATE_ARTICLE.delta);
        return articleMapper.toResponse(saved);
    }

    @Transactional
    public ArticleDetailResponse findDetailById(Long id, String clientIp, String userAgent) {
        ArticleEntity article = articleRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));

        BoardEntity board = article.getBoard();
        Long userId = currentUserService.getOptionalUserId().orElse(null);
        UserEntity user = userId == null ? null : getUser(userId);
        BoardMemberEntity member = userId == null
            ? null
            : boardMemberRepository.findByUserIdAndBoardId(userId, board.getId()).orElse(null);

        if (!boardAccessPolicy.canAccessBoard(board, user, member)) {
            throw new ApiException(ErrorCode.BOARD_NOT_FOUND);
        }

        EnumSet<ContentVisibility> allowed = boardAccessPolicy.resolveAllowedVisibilities(board, user, member);
        if (!allowed.contains(article.getVisibility())) {
            throw new AccessDeniedException("Access Denied");
        }

        long hit = articleViewService.increaseHitIfEligible(article.getId(), article.getHit(), clientIp, userAgent);

        long commentCount = getCommentCount(article.getId());
        ReactionCounts reactionCounts = getReactionCounts(article.getId());
        short myReaction = resolveMyReaction(article.getId(), user);
        boolean bookmarked = resolveBookmarked(article.getId(), user);
        List<FileResponse> attachments = resolveAttachments(article.getId());
        FileResponse boardImage = resolveBoardImage(board.getId());
        ArticleBoardResponse boardResponse = new ArticleBoardResponse(
            board.getId(),
            board.getBoardName(),
            board.getSlug(),
            board.getDescription(),
            board.getVisibility(),
            boardImage
        );

        return new ArticleDetailResponse(
            article.getId(),
            boardResponse,
            article.getUser().getId(),
            authorDisplayResolver.resolveAuthorName(article.getUser()),
            article.getVisibility(),
            article.getTitle(),
            article.getContent(),
            hit,
            commentCount,
            reactionCounts.likeCount(),
            reactionCounts.dislikeCount(),
            myReaction,
            bookmarked,
            article.isNotice(),
            article.getCreatedAt(),
            article.getUpdatedAt(),
            attachments
        );
    }

    @Transactional(readOnly = true)
    public ArticleEditorDetailResponse findEditorDetailById(Long id) {
        ArticleEntity article = articleRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));

        UserEntity user = getCurrentUser();
        sanctionGuard.requireNotSanctioned(user, article.getBoard());
        requireOwnership(user, article);

        BoardEntity board = article.getBoard();
        FileResponse boardImage = resolveBoardImage(board.getId());
        List<FileResponse> attachments = resolveAttachments(article.getId());
        ArticleCategoryEntity category = article.getCategory();
        ArticleBoardResponse boardResponse = new ArticleBoardResponse(
            board.getId(),
            board.getBoardName(),
            board.getSlug(),
            board.getDescription(),
            board.getVisibility(),
            boardImage
        );

        return new ArticleEditorDetailResponse(
            article.getId(),
            boardResponse,
            article.getUser().getId(),
            category != null ? category.getId() : null,
            category != null ? category.getCategoryName() : null,
            authorDisplayResolver.resolveAuthorName(article.getUser()),
            article.getVisibility(),
            article.getTitle(),
            article.getContent(),
            article.getContentSource(),
            article.getContentFormat(),
            article.isNotice(),
            article.getCreatedAt(),
            article.getUpdatedAt(),
            attachments
        );
    }

    @Transactional(readOnly = true)
    public ArticlePreviewResponse preview(ArticlePreviewRequest request) {
        ArticleContentService.RenderedContent renderedContent = articleContentService.render(
            request.contentSource(),
            request.contentFormat()
        );
        return new ArticlePreviewResponse(renderedContent.content());
    }

    @Transactional
    public ArticleBookmarkStatusResponse bookmark(Long articleId) {
        UserEntity user = getCurrentUser();
        ArticleEntity article = getArticleForReaction(articleId, user);
        sanctionGuard.requireNotSanctioned(user, article.getBoard());

        if (articleBookmarkRepository.existsByUserIdAndArticleId(user.getId(), article.getId())) {
            return new ArticleBookmarkStatusResponse(article.getId(), true);
        }

        ArticleBookmarkEntity entity = ArticleBookmarkEntity.builder()
            .user(user)
            .article(article)
            .build();
        articleBookmarkRepository.save(entity);
        articleTrendingService.recordBookmarkCreated(article.getId());
        return new ArticleBookmarkStatusResponse(article.getId(), true);
    }

    @Transactional
    public ArticleBookmarkStatusResponse unbookmark(Long articleId) {
        UserEntity user = getCurrentUser();
        ArticleEntity article = getArticleForReaction(articleId, user);
        sanctionGuard.requireNotSanctioned(user, article.getBoard());
        if (!articleBookmarkRepository.existsByUserIdAndArticleId(user.getId(), articleId)) {
            return new ArticleBookmarkStatusResponse(articleId, false);
        }
        articleBookmarkRepository.deleteByUserIdAndArticleId(user.getId(), articleId);
        articleTrendingService.recordBookmarkDeleted(articleId);
        return new ArticleBookmarkStatusResponse(articleId, false);
    }

    @Transactional
    public ArticleReactionSummaryResponse toggleReaction(Long articleId, ArticleReactionToggleRequest request) {
        if (request.reactionType() == null) {
            throw new ApiException(ErrorCode.REACTION_TYPE_REQUIRED);
        }
        short reactionType = request.reactionType();
        ReactionTypeValidator.validate(reactionType);
        if (reactionType == 0) {
            throw new ApiException(ErrorCode.REACTION_TYPE_INVALID);
        }

        UserEntity user = getCurrentUser();
        ArticleEntity article = getArticleForReaction(articleId, user);
        sanctionGuard.requireNotSanctioned(user, article.getBoard());
        short previousReaction = resolveMyReaction(article.getId(), user);

        short myReaction = articleReactionRepository.upsertToggleReaction(
            user.getId(),
            article.getId(),
            reactionType
        );

        ReactionCounts counts = getReactionCounts(article.getId());
        articleTrendingService.recordArticleReactionChanged(article.getId(), previousReaction, myReaction);
        publishArticleReactionChanged(article, counts, myReaction);
        return new ArticleReactionSummaryResponse(
            article.getId(),
            counts.likeCount(),
            counts.dislikeCount(),
            myReaction
        );
    }

    @Transactional(readOnly = true)
    public List<ArticleTrendingItemResponse> findTrendingPublic(ArticleTrendingWindow window, int limit) {
        ArticleTrendingWindow resolvedWindow = window == null ? ArticleTrendingWindow.DAY : window;
        return articleTrendingService.findTrendingPublic(resolvedWindow, limit);
    }

    @Transactional(readOnly = true)
    public List<ArticleResponse> findAll() {
        return articleRepository.findAll().stream()
            .map(articleMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public SliceResponse<ArticleRecentItemResponse> findRecentPublic(int page, int size) {
        int resolvedPage = pageNormalizer.normalizePage(page);
        int resolvedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, ARTICLE_SORT);

        Slice<ArticleEntity> slice = articleRepository
            .findByBoardVisibilityAndBoardDeletedAtIsNullAndBoardSlugNotInAndVisibilityAndNoticeFalseAndDeletedAtIsNull(
                BoardVisibility.PUBLIC,
                publicArticleFeedPolicy.excludedBoardSlugs(),
                ContentVisibility.PUBLIC,
                pageable
            );

        List<ArticleEntity> articles = slice.getContent();
        Map<Long, Long> commentCounts = loadCommentCounts(articles, List.of());
        Map<Long, ReactionCounts> reactionCounts = loadReactionCounts(articles, List.of());
        List<ArticleRecentItemResponse> items = articles.stream()
            .map(article -> toRecentItemResponse(article, commentCounts, reactionCounts))
            .toList();

        return new SliceResponse<>(
            items,
            slice.getNumber(),
            slice.getSize(),
            slice.hasNext(),
            slice.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public List<ArticleRecommendedItemResponse> findRecommendedPublic(int limit) {
        return articleRecommendationService.findRecommendedPublic(limit);
    }

    @Transactional(readOnly = true)
    public List<ArticleCategoryResponse> getBoardCategories(Long boardId) {
        BoardEntity board = getBoardForRead(boardId);
        Long userId = currentUserService.getOptionalUserId().orElse(null);
        UserEntity user = userId == null ? null : getUser(userId);
        BoardMemberEntity member = userId == null
            ? null
            : boardMemberRepository.findByUserIdAndBoardId(userId, boardId).orElse(null);

        if (!boardAccessPolicy.canAccessBoard(board, user, member)) {
            throw new ApiException(ErrorCode.BOARD_NOT_FOUND);
        }

        return articleCategoryRepository.findAllByBoardIdOrderByCategoryNameAsc(boardId).stream()
            .map(articleMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public BoardArticleListResponse getBoardArticles(
        Long boardId,
        int page,
        int size,
        SortOrder order,
        Long categoryId,
        boolean uncategorized
    ) {
        int resolvedPage = pageNormalizer.normalizePage(page);
        int resolvedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        Sort sort = resolveArticleSort(order);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, sort);

        if (categoryId != null && uncategorized) {
            throw new ApiException(ErrorCode.ARTICLE_CATEGORY_FILTER_CONFLICT);
        }

        BoardEntity board = getBoardForRead(boardId);
        Long userId = currentUserService.getOptionalUserId().orElse(null);
        UserEntity user = userId == null ? null : getUser(userId);
        BoardMemberEntity member = userId == null
            ? null
            : boardMemberRepository.findByUserIdAndBoardId(userId, boardId).orElse(null);

        if (!boardAccessPolicy.canAccessBoard(board, user, member)) {
            throw new ApiException(ErrorCode.BOARD_NOT_FOUND);
        }

        EnumSet<ContentVisibility> visibilities = boardAccessPolicy.resolveAllowedVisibilities(board, user, member);
        if (visibilities.isEmpty()) {
            throw new AccessDeniedException("Access Denied");
        }

        ArticleCategoryEntity category = getCategoryForBoard(categoryId, board);
        Page<ArticleEntity> pageResult;
        if (uncategorized) {
            pageResult = articleRepository.findByBoardIdAndCategoryIsNullAndNoticeFalseAndVisibilityInAndDeletedAtIsNull(
                boardId,
                visibilities,
                pageable
            );
        } else if (category == null) {
            pageResult = articleRepository.findByBoardIdAndNoticeFalseAndVisibilityInAndDeletedAtIsNull(
                boardId,
                visibilities,
                pageable
            );
        } else {
            pageResult = articleRepository.findByBoardIdAndCategoryIdAndNoticeFalseAndVisibilityInAndDeletedAtIsNull(
                boardId,
                category.getId(),
                visibilities,
                pageable
            );
        }

        List<ArticleEntity> pinnedEntities = List.of();
        if (resolvedPage == 0 && category == null && !uncategorized) {
            pinnedEntities = articleRepository.findByBoardIdAndNoticeTrueAndVisibilityInAndDeletedAtIsNull(
                boardId,
                visibilities,
                PageRequest.of(0, PINNED_LIMIT, sort)
            );
        }

        Map<Long, Long> commentCounts = loadCommentCounts(pageResult.getContent(), pinnedEntities);
        Map<Long, ReactionCounts> reactionCounts = loadReactionCounts(pageResult.getContent(), pinnedEntities);

        List<ArticleSummaryResponse> pinned = mapSummaries(pinnedEntities, commentCounts, reactionCounts);
        List<ArticleSummaryResponse> items = mapSummaries(pageResult.getContent(), commentCounts, reactionCounts);

        PageResponse<ArticleSummaryResponse> pageResponse = new PageResponse<>(
            items,
            pageResult.getNumber(),
            pageResult.getSize(),
            pageResult.getTotalElements(),
            pageResult.getTotalPages(),
            pageResult.hasNext(),
            pageResult.hasPrevious()
        );

        return new BoardArticleListResponse(pinned, pageResponse);
    }

    @Transactional
    public ArticleResponse update(Long id, ArticleUpdateRequest request) {
        ArticleEntity entity = articleRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));
        UserEntity user = getCurrentUser();
        sanctionGuard.requireNotSanctioned(user, entity.getBoard());
        requireOwnership(user, entity);
        ArticleCategoryEntity category = getCategoryForBoard(request.categoryId(), entity.getBoard());
        ArticleContentService.RenderedContent renderedContent = articleContentService.render(
            request.contentSource(),
            request.contentFormat()
        );
        entity.update(
            category,
            request.visibility(),
            request.title(),
            renderedContent.content(),
            renderedContent.contentSource(),
            request.contentFormat(),
            request.notice()
        );
        syncArticleFiles(entity, request.fileIds());
        return articleMapper.toResponse(entity);
    }

    @Transactional
    public void delete(Long id) {
        ArticleEntity entity = articleRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));
        UserEntity user = getCurrentUser();
        sanctionGuard.requireNotSanctioned(user, entity.getBoard());
        requireOwnership(user, entity);
        if (!entity.isDeleted()) {
            entity.softDelete();
            softDeleteAttachments(entity.getId());
            if (entity.getUser().getId().equals(user.getId())) {
                user.changePoint(ActivityPointPolicy.DELETE_ARTICLE.delta);
            }
        }
    }

    @Transactional(readOnly = true)
    public String resolveAttachmentDownloadLocation(Long articleId, Long fileId) {
        ArticleEntity article = articleRepository.findByIdAndDeletedAtIsNull(articleId)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));

        BoardEntity board = article.getBoard();
        Long userId = currentUserService.getOptionalUserId().orElse(null);
        UserEntity user = userId == null ? null : getUser(userId);
        BoardMemberEntity member = userId == null
            ? null
            : boardMemberRepository.findByUserIdAndBoardId(userId, board.getId()).orElse(null);

        if (!boardAccessPolicy.canAccessBoard(board, user, member)) {
            throw new ApiException(ErrorCode.BOARD_NOT_FOUND);
        }

        EnumSet<ContentVisibility> allowed = boardAccessPolicy.resolveAllowedVisibilities(board, user, member);
        if (!allowed.contains(article.getVisibility())) {
            throw new AccessDeniedException("Access Denied");
        }

        ArticleFileEntity mapping = articleFileRepository.findByArticleIdAndFileId(articleId, fileId)
            .orElseThrow(() -> new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND));

        FileEntity file = mapping.getFile();
        if (file == null || file.isDeleted() || !isAttachmentFile(file)) {
            throw new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }

        return fileStorage.resolveDownloadUrl(
            file.getStorageKey(),
            file.getFileName(),
            file.getMimeType()
        );
    }

    private void attachArticleFiles(ArticleEntity article, List<Long> fileIds) {
        if (article == null) {
            return;
        }
        List<Long> normalized = normalizeFileIds(fileIds);
        if (normalized.isEmpty()) {
            return;
        }
        for (Long fileId : normalized) {
            FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ApiException(ErrorCode.FILE_NOT_FOUND));
            if (file.isDeleted()) {
                continue;
            }
            if (!isAttachableFile(file)) {
                throw new ApiException(ErrorCode.ARTICLE_FILE_NOT_ATTACHABLE);
            }
            ArticleFileEntity mapping = ArticleFileEntity.builder()
                .article(article)
                .file(file)
                .build();
            articleFileRepository.save(mapping);
            file.clearTemporary();
        }
    }

    private void syncArticleFiles(ArticleEntity article, List<Long> fileIds) {
        if (article == null || article.getId() == null || fileIds == null) {
            return;
        }
        List<Long> normalized = normalizeFileIds(fileIds);
        List<ArticleFileEntity> existing = articleFileRepository.findAllByArticleIdOrderByCreatedAtAsc(article.getId());
        Set<Long> desired = new LinkedHashSet<>(normalized);
        Map<Long, ArticleFileEntity> existingMap = new HashMap<>();
        for (ArticleFileEntity mapping : existing) {
            FileEntity file = mapping.getFile();
            if (file != null && file.getId() != null) {
                existingMap.put(file.getId(), mapping);
            }
        }

        for (Long fileId : desired) {
            if (existingMap.containsKey(fileId)) {
                FileEntity existingFile = existingMap.get(fileId).getFile();
                if (existingFile != null) {
                    existingFile.clearTemporary();
                }
                continue;
            }
            FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ApiException(ErrorCode.FILE_NOT_FOUND));
            if (file.isDeleted()) {
                continue;
            }
            if (!isAttachableFile(file)) {
                throw new ApiException(ErrorCode.ARTICLE_FILE_NOT_ATTACHABLE);
            }
            ArticleFileEntity mapping = ArticleFileEntity.builder()
                .article(article)
                .file(file)
                .build();
            articleFileRepository.save(mapping);
            file.clearTemporary();
        }

        for (ArticleFileEntity mapping : existing) {
            FileEntity file = mapping.getFile();
            if (file == null || file.getId() == null) {
                continue;
            }
            Long fileId = file.getId();
            if (desired.contains(fileId)) {
                continue;
            }
            articleFileRepository.delete(mapping);
            if (!articleFileRepository.existsByFileId(fileId) && isArticleFile(file)) {
                file.markTemporary(temporaryFilePolicy.resolveExpiry());
            }
        }
    }

    private void softDeleteAttachments(Long articleId) {
        if (articleId == null) {
            return;
        }
        List<ArticleFileEntity> mappings = articleFileRepository.findAllByArticleIdOrderByCreatedAtAsc(articleId);
        for (ArticleFileEntity mapping : mappings) {
            FileEntity file = mapping.getFile();
            if (file == null || file.isDeleted()) {
                continue;
            }
            softDeleteVariants(file.getId());
            file.softDelete();
        }
    }

    private void softDeleteVariants(Long fileId) {
        if (fileId == null) {
            return;
        }
        List<FileVariantEntity> variants = fileVariantRepository.findAllByFileIdAndDeletedAtIsNull(fileId);
        for (FileVariantEntity variant : variants) {
            variant.softDelete();
        }
    }

    private List<Long> normalizeFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Long fileId : fileIds) {
            if (fileId == null || seen.contains(fileId)) {
                continue;
            }
            seen.add(fileId);
            result.add(fileId);
        }
        return result;
    }

    private boolean isAttachableFile(FileEntity file) {
        if (file == null || file.getFileClass() == null) {
            return false;
        }
        String code = file.getFileClass().getCode();
        return FileClassCode.ARTICLE_CONTENT_IMAGE.equals(code)
            || FileClassCode.ARTICLE_CONTENT_VIDEO.equals(code)
            || FileClassCode.ARTICLE_ATTACHMENT.equals(code)
            || FileClassCode.ARTICLE_THUMBNAIL.equals(code);
    }

    private boolean isAttachmentFile(FileEntity file) {
        if (file == null || file.getFileClass() == null) {
            return false;
        }
        return FileClassCode.ARTICLE_ATTACHMENT.equals(file.getFileClass().getCode());
    }

    private boolean isArticleFile(FileEntity file) {
        if (file == null || file.getFileClass() == null) {
            return false;
        }
        String code = file.getFileClass().getCode();
        return FileClassCode.ARTICLE_CONTENT_IMAGE.equals(code)
            || FileClassCode.ARTICLE_CONTENT_VIDEO.equals(code)
            || FileClassCode.ARTICLE_ATTACHMENT.equals(code)
            || FileClassCode.ARTICLE_THUMBNAIL.equals(code);
    }

    private BoardEntity getBoard(Long boardId) {
        return boardRepository.findByIdAndDeletedAtIsNull(boardId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_NOT_FOUND));
    }

    private BoardEntity getBoardForRead(Long boardId) {
        return boardRepository.findByIdAndDeletedAtIsNull(boardId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_NOT_FOUND));
    }

    private UserEntity getCurrentUser() {
        Long userId = currentUserService.getUserId();
        return getUser(userId);
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private ArticleCategoryEntity getCategoryForBoard(Long categoryId, BoardEntity board) {
        if (categoryId == null) {
            return null;
        }
        ArticleCategoryEntity category = articleCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_CATEGORY_NOT_FOUND));
        if (!category.getBoard().getId().equals(board.getId())) {
            throw new ApiException(ErrorCode.BOARD_CATEGORY_INVALID);
        }
        return category;
    }

    private void requireOwnership(UserEntity user, ArticleEntity entity) {
        if (entity.getUser().getId().equals(user.getId())) {
            return;
        }
        if (boardAccessPolicy.isManagerOrAdmin(user)) {
            return;
        }
        throw new AccessDeniedException("Access Denied");
    }

    private Map<Long, Long> loadCommentCounts(
        List<ArticleEntity> items,
        List<ArticleEntity> pinned
    ) {
        Set<Long> ids = new LinkedHashSet<>();
        items.forEach(article -> ids.add(article.getId()));
        pinned.forEach(article -> ids.add(article.getId()));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countByArticleIds(ids).stream()
            .collect(Collectors.toMap(
                CommentRepository.CommentCountView::getArticleId,
                CommentRepository.CommentCountView::getCount
            ));
    }

    private Map<Long, ReactionCounts> loadReactionCounts(
        List<ArticleEntity> items,
        List<ArticleEntity> pinned
    ) {
        List<ArticleEntity> combined = new ArrayList<>(items);
        combined.addAll(pinned);
        List<Long> articleIds = combined.stream()
            .map(ArticleEntity::getId)
            .toList();
        if (articleIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ReactionCounts> counts = new HashMap<>();
        List<ArticleReactionCountView> result = articleReactionRepository.countByArticleIds(articleIds);
        for (ArticleReactionCountView row : result) {
            ReactionCounts current = counts.getOrDefault(row.getArticleId(), ReactionCounts.empty());
            if (row.getReactionType() == 1) {
                counts.put(row.getArticleId(), new ReactionCounts(current.likeCount() + row.getCount(), current.dislikeCount()));
            } else if (row.getReactionType() == -1) {
                counts.put(row.getArticleId(), new ReactionCounts(current.likeCount(), current.dislikeCount() + row.getCount()));
            }
        }
        return counts;
    }

    private long getCommentCount(Long articleId) {
        return commentRepository.countByArticleIds(List.of(articleId)).stream()
            .findFirst()
            .map(CommentRepository.CommentCountView::getCount)
            .orElse(0L);
    }

    private ReactionCounts getReactionCounts(Long articleId) {
        long likeCount = articleReactionRepository.countByArticleIdAndReactionType(articleId, (short) 1);
        long dislikeCount = articleReactionRepository.countByArticleIdAndReactionType(articleId, (short) -1);
        return new ReactionCounts(likeCount, dislikeCount);
    }

    private short resolveMyReaction(Long articleId, UserEntity user) {
        if (user == null) {
            return 0;
        }
        return articleReactionRepository.findByUserIdAndArticleId(user.getId(), articleId)
            .map(ArticleReactionEntity::getReactionType)
            .orElse((short) 0);
    }

    private boolean resolveBookmarked(Long articleId, UserEntity user) {
        if (user == null) {
            return false;
        }
        return articleBookmarkRepository.existsByUserIdAndArticleId(user.getId(), articleId);
    }

    private void publishArticleReactionChanged(ArticleEntity article, ReactionCounts counts, short myReaction) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetType", "ARTICLE");
        payload.put("action", "TOGGLED");
        payload.put("boardId", article.getBoard().getId());
        payload.put("articleId", article.getId());
        payload.put("likeCount", counts.likeCount());
        payload.put("dislikeCount", counts.dislikeCount());
        payload.put("myReaction", myReaction);
        boardRealtimeSseService.publishReactionChanged(article.getBoard().getId(), payload);
    }

    private ArticleEntity getArticleForReaction(Long articleId, UserEntity user) {
        ArticleEntity article = articleRepository.findByIdAndDeletedAtIsNull(articleId)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));

        BoardEntity board = article.getBoard();
        BoardMemberEntity member = boardMemberRepository
            .findByUserIdAndBoardId(user.getId(), board.getId())
            .orElse(null);

        if (!boardAccessPolicy.canAccessBoard(board, user, member)) {
            throw new ApiException(ErrorCode.BOARD_NOT_FOUND);
        }

        EnumSet<ContentVisibility> allowed = boardAccessPolicy.resolveAllowedVisibilities(board, user, member);
        if (!allowed.contains(article.getVisibility())) {
            throw new AccessDeniedException("Access Denied");
        }
        return article;
    }

    private List<FileResponse> resolveAttachments(Long articleId) {
        List<ArticleFileEntity> mappings = articleFileRepository.findAllByArticleIdOrderByCreatedAtAsc(articleId);
        return mappings.stream()
            .map(ArticleFileEntity::getFile)
            .filter(file -> !file.isDeleted() && isAttachmentFile(file))
            .map(fileMapper::toResponse)
            .toList();
    }

    private FileResponse resolveBoardImage(Long boardId) {
        List<BoardFileEntity> files = boardFileRepository.findAllByBoardIdOrderByCreatedAtDesc(boardId);
        for (BoardFileEntity boardFile : files) {
            FileEntity file = boardFile.getFile();
            if (file.isDeleted()) {
                continue;
            }
            return fileMapper.toResponse(file);
        }
        return null;
    }

    private List<ArticleSummaryResponse> mapSummaries(
        List<ArticleEntity> articles,
        Map<Long, Long> commentCounts,
        Map<Long, ReactionCounts> reactionCounts
    ) {
        return articles.stream()
            .map(article -> new ArticleSummaryResponse(
                article.getId(),
                article.getBoard().getId(),
                article.getUser().getId(),
                authorDisplayResolver.resolveAuthorName(article.getUser()),
                article.getTitle(),
                article.getCategory() != null ? article.getCategory().getId() : null,
                article.getCategory() != null ? article.getCategory().getCategoryName() : null,
                article.getHit(),
                commentCounts.getOrDefault(article.getId(), 0L),
                reactionCounts.getOrDefault(article.getId(), ReactionCounts.empty()).likeCount(),
                reactionCounts.getOrDefault(article.getId(), ReactionCounts.empty()).dislikeCount(),
                article.isNotice(),
                article.getCreatedAt()
            ))
            .toList();
    }

    private ArticleRecentItemResponse toRecentItemResponse(
        ArticleEntity article,
        Map<Long, Long> commentCounts,
        Map<Long, ReactionCounts> reactionCounts
    ) {
        ReactionCounts counts = reactionCounts.getOrDefault(article.getId(), ReactionCounts.empty());
        return new ArticleRecentItemResponse(
            article.getId(),
            article.getBoard().getId(),
            article.getBoard().getSlug(),
            article.getBoard().getBoardName(),
            article.getUser().getId(),
            authorDisplayResolver.resolveAuthorName(article.getUser()),
            article.getTitle(),
            buildPreviewText(article.getContent()),
            commentCounts.getOrDefault(article.getId(), 0L),
            counts.likeCount(),
            article.getHit(),
            article.getCreatedAt()
        );
    }

    private String buildPreviewText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String withoutCodeBlocks = html
            .replaceAll("(?is)<pre\\b[^>]*>.*?</pre>", " ")
            .replaceAll("(?is)<code\\b[^>]*>.*?</code>", " ");
        String plainText = withoutCodeBlocks.replaceAll("(?is)<[^>]+>", " ");
        String normalized = HtmlUtils.htmlUnescape(plainText)
            .replace('\u00A0', ' ')
            .replaceAll("\\s+", " ")
            .trim();
        if (normalized.length() <= 140) {
            return normalized;
        }
        return normalized.substring(0, 139).trim() + "…";
    }

    private Sort resolveArticleSort(SortOrder order) {
        if (order == SortOrder.OLDEST) {
            return Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("updatedAt"),
                Sort.Order.asc("id")
            );
        }
        return ARTICLE_SORT;
    }

    private record ReactionCounts(long likeCount, long dislikeCount) {
        private static ReactionCounts empty() {
            return new ReactionCounts(0, 0);
        }
    }

}
