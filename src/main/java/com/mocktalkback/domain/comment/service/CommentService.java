package com.mocktalkback.domain.comment.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.article.entity.ArticleEntity;
import com.mocktalkback.domain.article.repository.ArticleRepository;
import com.mocktalkback.domain.article.service.ArticleSyncVersionService;
import com.mocktalkback.domain.article.service.ArticleTrendingService;
import com.mocktalkback.domain.comment.dto.CommentCreateRequest;
import com.mocktalkback.domain.comment.dto.CommentPageResponse;
import com.mocktalkback.domain.comment.dto.CommentReactionSummaryResponse;
import com.mocktalkback.domain.comment.dto.CommentSnapshotResponse;
import com.mocktalkback.domain.comment.dto.CommentReactionToggleRequest;
import com.mocktalkback.domain.comment.dto.CommentTreeResponse;
import com.mocktalkback.domain.comment.dto.CommentUpdateRequest;
import com.mocktalkback.domain.comment.entity.CommentEntity;
import com.mocktalkback.domain.comment.repository.CommentReactionRepository;
import com.mocktalkback.domain.comment.repository.CommentRepository;
import com.mocktalkback.domain.common.policy.AuthorDisplayResolver;
import com.mocktalkback.domain.common.policy.BoardAccessPolicy;
import com.mocktalkback.domain.common.policy.PageNormalizer;
import com.mocktalkback.domain.common.policy.SanctionGuard;
import com.mocktalkback.domain.notification.service.NotificationService;
import com.mocktalkback.domain.realtime.service.BoardRealtimeSseService;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.repository.BoardMemberRepository;
import com.mocktalkback.domain.role.type.ContentVisibility;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;
import com.mocktalkback.global.common.util.ActivityPointPolicy;
import com.mocktalkback.global.common.util.ReactionTypeValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Sort ROOT_COMMENT_SORT = Sort.by(
        Sort.Order.asc("createdAt"),
        Sort.Order.asc("id")
    );

    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final BoardMemberRepository boardMemberRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final BoardRealtimeSseService boardRealtimeSseService;
    private final ArticleSyncVersionService articleSyncVersionService;
    private final ArticleTrendingService articleTrendingService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final SanctionGuard sanctionGuard;
    private final PageNormalizer pageNormalizer;
    private final AuthorDisplayResolver authorDisplayResolver;

    @Transactional
    public CommentTreeResponse createRoot(Long articleId, CommentCreateRequest request) {
        UserEntity user = getCurrentUser();
        ArticleEntity article = getAccessibleArticle(articleId, user);
        sanctionGuard.requireNotSanctioned(user, article.getBoard());
        String content = normalizeContent(request.content());

        CommentEntity entity = CommentEntity.builder()
            .user(user)
            .article(article)
            .parentComment(null)
            .rootComment(null)
            .depth(0)
            .content(content)
            .build();
        CommentEntity saved = commentRepository.save(entity);
        saved.assignRootComment(saved);
        user.changePoint(ActivityPointPolicy.CREATE_REPLY.delta);
        notifyArticleComment(user, article);
        long syncVersion = articleSyncVersionService.increaseAndGet(article.getId());
        article.applySyncVersion(syncVersion);
        articleTrendingService.recordCommentCreated(article.getId());
        publishCommentChanged(saved, "CREATED", syncVersion);
        return toTreeResponse(saved);
    }

    @Transactional
    public CommentTreeResponse createReply(Long articleId, Long parentCommentId, CommentCreateRequest request) {
        UserEntity user = getCurrentUser();
        ArticleEntity article = getAccessibleArticle(articleId, user);
        sanctionGuard.requireNotSanctioned(user, article.getBoard());
        CommentEntity parent = getComment(parentCommentId);
        if (!parent.getArticle().getId().equals(article.getId())) {
            throw new ApiException(ErrorCode.COMMENT_WRONG_ARTICLE);
        }

        CommentEntity root = resolveRootComment(parent);
        int depth = parent.getDepth() + 1;
        String content = normalizeContent(request.content());

        CommentEntity entity = CommentEntity.builder()
            .user(user)
            .article(article)
            .parentComment(parent)
            .rootComment(root)
            .depth(depth)
            .content(content)
            .build();
        CommentEntity saved = commentRepository.save(entity);
        user.changePoint(ActivityPointPolicy.CREATE_REPLY.delta);
        notifyCommentReply(user, article, parent, saved);
        long syncVersion = articleSyncVersionService.increaseAndGet(article.getId());
        article.applySyncVersion(syncVersion);
        articleTrendingService.recordCommentCreated(article.getId());
        publishCommentChanged(saved, "CREATED", syncVersion);
        return toTreeResponse(saved);
    }

    @Transactional(readOnly = true)
    public CommentPageResponse<CommentTreeResponse> getArticleComments(Long articleId, int page, int size) {
        UserEntity currentUser = getOptionalCurrentUser();
        ArticleEntity article = getAccessibleArticle(articleId, currentUser);
        return getArticleComments(article, currentUser, page, size);
    }

    @Transactional(readOnly = true)
    public CommentSnapshotResponse getArticleCommentsSnapshot(Long articleId, int page, int size) {
        UserEntity currentUser = getOptionalCurrentUser();
        ArticleEntity article = getAccessibleArticle(articleId, currentUser);
        CommentPageResponse<CommentTreeResponse> pageResponse = getArticleComments(article, currentUser, page, size);
        return new CommentSnapshotResponse(article.getId(), article.getSyncVersion(), pageResponse);
    }

    private CommentPageResponse<CommentTreeResponse> getArticleComments(ArticleEntity article, UserEntity currentUser, int page, int size) {
        int resolvedPage = pageNormalizer.normalizePage(page);
        int resolvedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, ROOT_COMMENT_SORT);

        Page<CommentEntity> rootPage = commentRepository.findByArticleIdAndParentCommentIsNull(
            article.getId(),
            pageable
        );
        List<CommentEntity> rootComments = rootPage.getContent();
        List<Long> rootIds = rootComments.stream()
            .map(CommentEntity::getId)
            .toList();

        Map<Long, CommentTreeResponse> rootNodeMap = new LinkedHashMap<>();
        if (!rootIds.isEmpty()) {
            List<CommentEntity> treeEntities = commentRepository.findTreeByArticleIdAndRootIds(
                article.getId(),
                rootIds
            );
            List<Long> commentIds = treeEntities.stream()
                .map(CommentEntity::getId)
                .toList();
            Map<Long, ReactionCounts> reactionCounts = getReactionCounts(commentIds);
            Map<Long, Short> myReactions = getMyReactions(currentUser, commentIds);

            Map<Long, CommentTreeResponse> nodeMap = new LinkedHashMap<>();
            for (CommentEntity entity : treeEntities) {
                ReactionCounts counts = reactionCounts.getOrDefault(entity.getId(), ReactionCounts.empty());
                short myReaction = myReactions.getOrDefault(entity.getId(), (short) 0);
                nodeMap.put(entity.getId(), toTreeResponse(entity, counts, myReaction));
            }
            for (CommentEntity entity : treeEntities) {
                CommentTreeResponse node = nodeMap.get(entity.getId());
                if (node == null) {
                    continue;
                }
                CommentEntity parent = entity.getParentComment();
                if (parent != null && nodeMap.containsKey(parent.getId())) {
                    nodeMap.get(parent.getId()).children().add(node);
                }
            }
            for (CommentEntity root : rootComments) {
                CommentTreeResponse node = nodeMap.get(root.getId());
                if (node != null) {
                    rootNodeMap.put(root.getId(), node);
                }
            }
        }

        Page<CommentTreeResponse> treePage = rootPage.map(root -> {
            CommentTreeResponse node = rootNodeMap.get(root.getId());
            if (node != null) {
                return node;
            }
            return toTreeResponse(root, ReactionCounts.empty(), (short) 0);
        });

        return CommentPageResponse.from(treePage);
    }

    @Transactional
    public CommentReactionSummaryResponse toggleReaction(Long commentId, CommentReactionToggleRequest request) {
        if (request.reactionType() == null) {
            throw new ApiException(ErrorCode.REACTION_TYPE_REQUIRED);
        }
        short reactionType = request.reactionType();
        ReactionTypeValidator.validate(reactionType);
        if (reactionType == 0) {
            throw new ApiException(ErrorCode.REACTION_TYPE_INVALID);
        }

        UserEntity user = getCurrentUser();
        CommentEntity comment = getComment(commentId);
        if (comment.isDeleted()) {
            throw new ApiException(ErrorCode.COMMENT_DELETED_NO_REACTION);
        }
        getAccessibleArticle(comment.getArticle().getId(), user);
        sanctionGuard.requireNotSanctioned(user, comment.getArticle().getBoard());

        short myReaction = commentReactionRepository.upsertToggleReaction(
            user.getId(),
            comment.getId(),
            reactionType
        );

        ReactionCounts counts = getReactionCounts(comment.getId());
        publishReactionChanged(comment, counts, myReaction);
        return new CommentReactionSummaryResponse(
            comment.getId(),
            counts.likeCount(),
            counts.dislikeCount(),
            myReaction
        );
    }

    @Transactional
    public CommentTreeResponse update(Long id, CommentUpdateRequest request) {
        CommentEntity entity = getComment(id);
        UserEntity user = getCurrentUser();
        if (entity.isDeleted()) {
            throw new ApiException(ErrorCode.COMMENT_DELETED_NO_EDIT);
        }
        sanctionGuard.requireNotSanctioned(user, entity.getArticle().getBoard());
        requireOwnership(user, entity);
        entity.updateContent(normalizeContent(request.content()));
        long syncVersion = articleSyncVersionService.increaseAndGet(entity.getArticle().getId());
        entity.getArticle().applySyncVersion(syncVersion);
        publishCommentChanged(entity, "UPDATED", syncVersion);
        return toTreeResponse(entity);
    }

    @Transactional
    public void delete(Long id) {
        CommentEntity entity = getComment(id);
        UserEntity user = getCurrentUser();
        sanctionGuard.requireNotSanctioned(user, entity.getArticle().getBoard());
        requireOwnership(user, entity);
        if (!entity.isDeleted()) {
            entity.softDelete();
            long syncVersion = articleSyncVersionService.increaseAndGet(entity.getArticle().getId());
            entity.getArticle().applySyncVersion(syncVersion);
            articleTrendingService.recordCommentDeleted(entity.getArticle().getId());
            publishCommentChanged(entity, "DELETED", syncVersion);
            if (entity.getUser().getId().equals(user.getId())) {
                user.changePoint(ActivityPointPolicy.DELETE_REPLY.delta);
            }
        }
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private UserEntity getCurrentUser() {
        Long userId = currentUserService.getUserId();
        return getUser(userId);
    }

    private UserEntity getOptionalCurrentUser() {
        return currentUserService.getOptionalUserId().map(this::getUser).orElse(null);
    }

    private CommentEntity getComment(Long commentId) {
        return commentRepository.findById(commentId)
            .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private ArticleEntity getAccessibleArticle(Long articleId, UserEntity currentUser) {
        ArticleEntity article = articleRepository.findByIdAndDeletedAtIsNull(articleId)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));
        BoardEntity board = article.getBoard();

        UserEntity user = currentUser;
        if (user == null) {
            Optional<Long> optionalUserId = currentUserService.getOptionalUserId();
            user = optionalUserId.map(this::getUser).orElse(null);
        }
        BoardMemberEntity member = user == null
            ? null
            : boardMemberRepository.findByUserIdAndBoardId(user.getId(), board.getId()).orElse(null);

        if (!boardAccessPolicy.canAccessBoard(board, user, member)) {
            throw new ApiException(ErrorCode.BOARD_NOT_FOUND);
        }
        EnumSet<ContentVisibility> allowed = boardAccessPolicy.resolveAllowedVisibilities(board, user, member);
        if (!allowed.contains(article.getVisibility())) {
            throw new AccessDeniedException("Access Denied");
        }
        return article;
    }

    private CommentEntity resolveRootComment(CommentEntity parent) {
        CommentEntity root = parent.getRootComment();
        if (root != null) {
            return root;
        }
        return parent;
    }

    private CommentTreeResponse toTreeResponse(CommentEntity entity) {
        return toTreeResponse(entity, ReactionCounts.empty(), (short) 0);
    }

    private CommentTreeResponse toTreeResponse(CommentEntity entity, ReactionCounts counts, short myReaction) {
        String content = entity.isDeleted() ? "삭제된 댓글입니다." : entity.getContent();
        Long parentId = entity.getParentComment() == null ? null : entity.getParentComment().getId();
        Long rootId = entity.getRootComment() == null ? entity.getId() : entity.getRootComment().getId();
        return new CommentTreeResponse(
            entity.getId(),
            entity.getUser().getId(),
            authorDisplayResolver.resolveAuthorName(entity.getUser()),
            content,
            entity.getDepth(),
            parentId,
            rootId,
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt(),
            counts.likeCount(),
            counts.dislikeCount(),
            myReaction,
            new ArrayList<>()
        );
    }

    private void requireOwnership(UserEntity user, CommentEntity entity) {
        if (entity.getUser().getId().equals(user.getId())) {
            return;
        }
        if (boardAccessPolicy.isManagerOrAdmin(user)) {
            return;
        }
        throw new AccessDeniedException("Access Denied");
    }

    private String normalizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ApiException(ErrorCode.COMMENT_CONTENT_REQUIRED);
        }
        return content.trim();
    }

    private void notifyArticleComment(UserEntity sender, ArticleEntity article) {
        UserEntity receiver = article.getUser();
        if (receiver.getId().equals(sender.getId())) {
            return;
        }
        notificationService.createArticleComment(receiver, sender, article);
    }

    private void notifyCommentReply(
        UserEntity sender,
        ArticleEntity article,
        CommentEntity parent,
        CommentEntity reply
    ) {
        UserEntity receiver = parent.getUser();
        if (receiver.getId().equals(sender.getId())) {
            return;
        }
        notificationService.createCommentReply(receiver, sender, article, reply);
    }

    private void publishCommentChanged(CommentEntity comment, String action, long syncVersion) {
        CommentTreeResponse commentSnapshot = toTreeResponse(comment);
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetType", "COMMENT");
        payload.put("action", action);
        payload.put("boardId", comment.getArticle().getBoard().getId());
        payload.put("articleId", comment.getArticle().getId());
        payload.put("commentId", comment.getId());
        payload.put("parentCommentId", comment.getParentComment() == null ? null : comment.getParentComment().getId());
        payload.put("depth", comment.getDepth());
        payload.put("syncVersion", syncVersion);
        payload.put("comment", commentSnapshot);
        boardRealtimeSseService.publishCommentChanged(comment.getArticle().getBoard().getId(), payload);
    }

    private void publishReactionChanged(CommentEntity comment, ReactionCounts counts, short myReaction) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetType", "COMMENT");
        payload.put("action", "TOGGLED");
        payload.put("boardId", comment.getArticle().getBoard().getId());
        payload.put("articleId", comment.getArticle().getId());
        payload.put("commentId", comment.getId());
        payload.put("likeCount", counts.likeCount());
        payload.put("dislikeCount", counts.dislikeCount());
        payload.put("myReaction", myReaction);
        boardRealtimeSseService.publishReactionChanged(comment.getArticle().getBoard().getId(), payload);
    }

    private ReactionCounts getReactionCounts(Long commentId) {
        long likeCount = commentReactionRepository.countByCommentIdAndReactionType(commentId, (short) 1);
        long dislikeCount = commentReactionRepository.countByCommentIdAndReactionType(commentId, (short) -1);
        return new ReactionCounts(likeCount, dislikeCount);
    }

    private Map<Long, ReactionCounts> getReactionCounts(List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ReactionCounts> counts = new HashMap<>();
        List<CommentReactionRepository.CommentReactionCountView> views =
            commentReactionRepository.countByCommentIds(commentIds);
        for (CommentReactionRepository.CommentReactionCountView view : views) {
            ReactionCounts current = counts.getOrDefault(view.getCommentId(), ReactionCounts.empty());
            if (view.getReactionType() == 1) {
                counts.put(view.getCommentId(), new ReactionCounts(view.getCount(), current.dislikeCount()));
            } else if (view.getReactionType() == -1) {
                counts.put(view.getCommentId(), new ReactionCounts(current.likeCount(), view.getCount()));
            }
        }
        return counts;
    }

    private Map<Long, Short> getMyReactions(UserEntity user, List<Long> commentIds) {
        if (user == null || commentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Short> result = new HashMap<>();
        List<CommentReactionRepository.CommentReactionUserView> views =
            commentReactionRepository.findUserReactions(user.getId(), commentIds);
        for (CommentReactionRepository.CommentReactionUserView view : views) {
            result.put(view.getCommentId(), view.getReactionType());
        }
        return result;
    }

    private record ReactionCounts(long likeCount, long dislikeCount) {
        private static ReactionCounts empty() {
            return new ReactionCounts(0, 0);
        }
    }
}
