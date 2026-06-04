package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.article.dto.ArticleBookmarkCreateRequest;
import com.mocktalkback.domain.article.dto.ArticleBookmarkDeleteRequest;
import com.mocktalkback.domain.article.dto.ArticleBookmarkItemResponse;
import com.mocktalkback.domain.article.dto.ArticleBookmarkResponse;
import com.mocktalkback.domain.article.entity.ArticleBookmarkEntity;
import com.mocktalkback.domain.article.entity.ArticleEntity;
import com.mocktalkback.domain.article.mapper.ArticleMapper;
import com.mocktalkback.domain.article.repository.ArticleBookmarkRepository;
import com.mocktalkback.domain.article.repository.ArticleReactionRepository;
import com.mocktalkback.domain.article.repository.ArticleReactionRepository.ArticleReactionCountView;
import com.mocktalkback.domain.article.repository.ArticleRepository;
import com.mocktalkback.domain.comment.repository.CommentRepository;
import com.mocktalkback.domain.common.policy.AuthorDisplayResolver;
import com.mocktalkback.domain.common.policy.PageNormalizer;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;
import com.mocktalkback.global.common.dto.PageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleBookmarkService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Sort BOOKMARK_SORT = Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("id")
    );

    private final ArticleBookmarkRepository articleBookmarkRepository;
    private final ArticleRepository articleRepository;
    private final ArticleReactionRepository articleReactionRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ArticleMapper articleMapper;
    private final PageNormalizer pageNormalizer;
    private final AuthorDisplayResolver authorDisplayResolver;

    @Transactional
    public ArticleBookmarkResponse create(ArticleBookmarkCreateRequest request) {
        UserEntity user = getUser(request.userId());
        ArticleEntity article = getArticle(request.articleId());
        ArticleBookmarkEntity entity = articleMapper.toEntity(request, user, article);
        ArticleBookmarkEntity saved = articleBookmarkRepository.save(entity);
        return articleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ArticleBookmarkResponse findById(Long id) {
        ArticleBookmarkEntity entity = articleBookmarkRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_BOOKMARK_NOT_FOUND));
        return articleMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ArticleBookmarkResponse> findAll() {
        return articleBookmarkRepository.findAll().stream()
            .map(articleMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleBookmarkItemResponse> findMyBookmarks(int page, int size) {
        int resolvedPage = pageNormalizer.normalizePage(page);
        int resolvedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, BOOKMARK_SORT);

        Long userId = currentUserService.getUserId();
        Page<ArticleBookmarkEntity> result = articleBookmarkRepository
            .findAllByUserIdAndArticleDeletedAtIsNullAndArticleBoardDeletedAtIsNull(userId, pageable);

        List<ArticleEntity> articles = result.getContent().stream()
            .map(ArticleBookmarkEntity::getArticle)
            .toList();
        Map<Long, Long> commentCounts = loadCommentCounts(articles);
        Map<Long, ReactionCounts> reactionCounts = loadReactionCounts(articles);

        List<ArticleBookmarkItemResponse> items = articles.stream()
            .map(article -> new ArticleBookmarkItemResponse(
                article.getId(),
                article.getBoard().getId(),
                article.getBoard().getSlug(),
                article.getUser().getId(),
                authorDisplayResolver.resolveAuthorName(article.getUser()),
                article.getTitle(),
                article.getHit(),
                commentCounts.getOrDefault(article.getId(), 0L),
                reactionCounts.getOrDefault(article.getId(), ReactionCounts.empty()).likeCount(),
                reactionCounts.getOrDefault(article.getId(), ReactionCounts.empty()).dislikeCount(),
                article.isNotice(),
                article.getCreatedAt()
            ))
            .toList();

        return new PageResponse<>(
            items,
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext(),
            result.hasPrevious()
        );
    }

    @Transactional
    public void deleteAllByUser() {
        Long userId = currentUserService.getUserId();
        articleBookmarkRepository.deleteByUserId(userId);
    }

    @Transactional
    public void deleteByArticleIds(ArticleBookmarkDeleteRequest request) {
        Long userId = currentUserService.getUserId();
        articleBookmarkRepository.deleteByUserIdAndArticleIdIn(userId, request.articleIds());
    }

    @Transactional
    public void delete(Long id) {
        articleBookmarkRepository.deleteById(id);
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private ArticleEntity getArticle(Long articleId) {
        return articleRepository.findById(articleId)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));
    }

    private Map<Long, Long> loadCommentCounts(List<ArticleEntity> articles) {
        Set<Long> ids = articles.stream()
            .map(ArticleEntity::getId)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countByArticleIds(ids).stream()
            .collect(Collectors.toMap(
                CommentRepository.CommentCountView::getArticleId,
                CommentRepository.CommentCountView::getCount
            ));
    }

    private Map<Long, ReactionCounts> loadReactionCounts(List<ArticleEntity> articles) {
        List<Long> articleIds = articles.stream()
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

    private record ReactionCounts(long likeCount, long dislikeCount) {
        private static ReactionCounts empty() {
            return new ReactionCounts(0, 0);
        }
    }
}
