package com.mocktalkback.domain.search.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.article.entity.ArticleEntity;
import com.mocktalkback.domain.article.entity.QArticleEntity;
import com.mocktalkback.domain.article.repository.ArticleReactionRepository;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardFileEntity;
import com.mocktalkback.domain.board.entity.QBoardEntity;
import com.mocktalkback.domain.board.repository.BoardFileRepository;
import com.mocktalkback.domain.comment.entity.CommentEntity;
import com.mocktalkback.domain.comment.entity.QCommentEntity;
import com.mocktalkback.domain.comment.repository.CommentRepository;
import com.mocktalkback.domain.common.policy.AuthorDisplayResolver;
import com.mocktalkback.domain.common.policy.PageNormalizer;
import com.mocktalkback.domain.common.policy.RoleEvaluator;
import com.mocktalkback.domain.file.dto.FileResponse;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.mapper.FileMapper;
import com.mocktalkback.domain.search.dto.ArticleSearchResponse;
import com.mocktalkback.domain.search.dto.BoardSearchResponse;
import com.mocktalkback.domain.search.dto.CommentSearchResponse;
import com.mocktalkback.domain.search.dto.SearchResponse;
import com.mocktalkback.domain.search.dto.UserSearchResponse;
import com.mocktalkback.domain.search.type.SearchType;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.entity.QUserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;
import com.mocktalkback.global.common.dto.SliceResponse;
import com.mocktalkback.global.common.type.SortOrder;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE = 0;

    private final JPAQueryFactory queryFactory;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final BoardFileRepository boardFileRepository;
    private final CommentRepository commentRepository;
    private final ArticleReactionRepository articleReactionRepository;
    private final SearchNativeQueryExecutor searchNativeQueryExecutor;
    private final FileMapper fileMapper;
    private final RoleEvaluator roleEvaluator;
    private final PageNormalizer pageNormalizer;
    private final AuthorDisplayResolver authorDisplayResolver;

    @Transactional(readOnly = true)
    public SearchResponse search(
        String keyword,
        SearchType type,
        SortOrder order,
        Integer page,
        Integer size,
        String boardSlug
    ) {
        String normalized = normalizeKeyword(keyword);
        int resolvedPage = pageNormalizer.normalizePage(page, DEFAULT_PAGE);
        int resolvedSize = pageNormalizer.normalizeSize(size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        SearchUserContext context = resolveUserContext();
        SearchType resolvedType = type == null ? SearchType.ALL : type;
        SortOrder resolvedOrder = order == null ? SortOrder.LATEST : order;

        SliceResponse<BoardSearchResponse> boards = resolvedType == SearchType.ALL || resolvedType == SearchType.BOARD
            ? searchBoards(normalized, resolvedPage, resolvedSize, resolvedOrder, context)
            : emptySlice(resolvedPage, resolvedSize);
        SliceResponse<ArticleSearchResponse> articles = resolvedType == SearchType.ALL || resolvedType == SearchType.ARTICLE
            ? searchArticles(normalized, resolvedPage, resolvedSize, resolvedOrder, boardSlug, context)
            : emptySlice(resolvedPage, resolvedSize);
        SliceResponse<CommentSearchResponse> comments = resolvedType == SearchType.ALL || resolvedType == SearchType.COMMENT
            ? searchComments(normalized, resolvedPage, resolvedSize, resolvedOrder, boardSlug, context)
            : emptySlice(resolvedPage, resolvedSize);
        SliceResponse<UserSearchResponse> users = resolvedType == SearchType.ALL || resolvedType == SearchType.USER
            ? searchUsers(normalized, resolvedPage, resolvedSize, resolvedOrder)
            : emptySlice(resolvedPage, resolvedSize);

        return new SearchResponse(boards, articles, comments, users);
    }

    private SliceResponse<BoardSearchResponse> searchBoards(
        String keyword,
        int page,
        int size,
        SortOrder order,
        SearchUserContext context
    ) {
        long offset = (long) page * size;
        List<Long> ftsIds = fetchBoardIdsByFts(keyword, offset, size + 1, order, context);
        boolean hasNext = hasNext(ftsIds, size);
        Set<Long> idSet = takeUpToSize(ftsIds, size);
        if (!hasNext && hasRemainingSlot(idSet, size)) {
            int remaining = remainingSlots(idSet, size);
            List<Long> fallbackIds = fetchBoardIdsByIlike(keyword, offset, remaining + 1, order, context, List.copyOf(idSet));
            hasNext = hasNext(fallbackIds, remaining);
            addUpToSize(idSet, fallbackIds, size);
        }
        List<Long> ids = new ArrayList<>(idSet);
        if (ids.isEmpty()) {
            return emptySlice(page, size);
        }
        List<BoardEntity> boards = loadBoards(ids);
        Map<Long, FileResponse> boardImages = resolveBoardImages(boards);
        List<BoardSearchResponse> items = boards.stream()
            .map(entity -> new BoardSearchResponse(
                entity.getId(),
                entity.getBoardName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getVisibility(),
                boardImages.get(entity.getId()),
                entity.getCreatedAt()
            ))
            .toList();

        return new SliceResponse<>(items, page, size, hasNext, page > 0);
    }

    private SliceResponse<ArticleSearchResponse> searchArticles(
        String keyword,
        int page,
        int size,
        SortOrder order,
        String boardSlug,
        SearchUserContext context
    ) {
        long offset = (long) page * size;
        List<Long> ftsIds = fetchArticleIdsByFts(keyword, offset, size + 1, order, boardSlug, context);
        boolean hasNext = hasNext(ftsIds, size);
        Set<Long> idSet = takeUpToSize(ftsIds, size);
        if (!hasNext && hasRemainingSlot(idSet, size)) {
            // 폴백은 제목/작성자 우선 조회 후 부족할 때만 본문 스캔으로 보충한다.
            int remaining = remainingSlots(idSet, size);
            List<Long> primaryFallbackIds = fetchArticleIdsByIlikePrimary(
                keyword,
                offset,
                remaining + 1,
                order,
                boardSlug,
                context,
                List.copyOf(idSet)
            );
            boolean primaryFallbackHasNext = hasNext(primaryFallbackIds, remaining);
            addUpToSize(idSet, primaryFallbackIds, size);
            if (primaryFallbackHasNext) {
                hasNext = true;
            } else if (hasRemainingSlot(idSet, size)) {
                int contentRemaining = remainingSlots(idSet, size);
                List<Long> contentFallbackIds = fetchArticleIdsByIlikeContent(
                    keyword,
                    offset,
                    contentRemaining + 1,
                    order,
                    boardSlug,
                    context,
                    List.copyOf(idSet)
                );
                hasNext = hasNext(contentFallbackIds, contentRemaining);
                addUpToSize(idSet, contentFallbackIds, size);
            } else {
                hasNext = false;
            }
        }
        List<Long> ids = new ArrayList<>(idSet);
        if (ids.isEmpty()) {
            return emptySlice(page, size);
        }
        List<ArticleEntity> articles = loadArticles(ids);
        Map<Long, Long> commentCounts = loadCommentCounts(articles);
        Map<Long, ReactionCounts> reactionCounts = loadReactionCounts(articles);
        List<ArticleSearchResponse> items = articles.stream()
            .map(entity -> toArticleSearchResponse(entity, commentCounts, reactionCounts))
            .toList();

        return new SliceResponse<>(items, page, size, hasNext, page > 0);
    }

    private SliceResponse<CommentSearchResponse> searchComments(
        String keyword,
        int page,
        int size,
        SortOrder order,
        String boardSlug,
        SearchUserContext context
    ) {
        long offset = (long) page * size;
        List<Long> ftsIds = fetchCommentIdsByFts(keyword, offset, size + 1, order, boardSlug, context);
        boolean hasNext = hasNext(ftsIds, size);
        Set<Long> idSet = takeUpToSize(ftsIds, size);
        if (!hasNext && hasRemainingSlot(idSet, size)) {
            int remaining = remainingSlots(idSet, size);
            List<Long> fallbackIds = fetchCommentIdsByIlike(keyword, offset, remaining + 1, order, boardSlug, context, List.copyOf(idSet));
            hasNext = hasNext(fallbackIds, remaining);
            addUpToSize(idSet, fallbackIds, size);
        }
        List<Long> ids = new ArrayList<>(idSet);
        if (ids.isEmpty()) {
            return emptySlice(page, size);
        }
        List<CommentEntity> comments = loadComments(ids);
        List<CommentSearchResponse> items = comments.stream()
            .map(this::toCommentSearchResponse)
            .toList();

        return new SliceResponse<>(items, page, size, hasNext, page > 0);
    }

    private SliceResponse<UserSearchResponse> searchUsers(
        String keyword,
        int page,
        int size,
        SortOrder order
    ) {
        long offset = (long) page * size;
        List<Long> ftsIds = fetchUserIdsByFts(keyword, offset, size + 1, order);
        boolean hasNext = hasNext(ftsIds, size);
        Set<Long> idSet = takeUpToSize(ftsIds, size);
        if (!hasNext && hasRemainingSlot(idSet, size)) {
            int remaining = remainingSlots(idSet, size);
            List<Long> fallbackIds = fetchUserIdsByIlike(keyword, offset, remaining + 1, order, List.copyOf(idSet));
            hasNext = hasNext(fallbackIds, remaining);
            addUpToSize(idSet, fallbackIds, size);
        }
        List<Long> ids = new ArrayList<>(idSet);
        if (ids.isEmpty()) {
            return emptySlice(page, size);
        }
        List<UserEntity> users = loadUsers(ids);
        List<UserSearchResponse> items = users.stream()
            .map(entity -> new UserSearchResponse(
                entity.getId(),
                entity.getHandle(),
                entity.getDisplayName(),
                entity.getCreatedAt()
            ))
            .toList();

        return new SliceResponse<>(items, page, size, hasNext, page > 0);
    }

    private List<Long> fetchBoardIdsByFts(
        String keyword,
        long offset,
        int limit,
        SortOrder order,
        SearchUserContext context
    ) {
        return searchNativeQueryExecutor.fetchBoardIdsByFts(
            keyword,
            offset,
            limit,
            order,
            context.userId(),
            context.isManagerOrAdmin()
        );
    }

    private List<Long> fetchBoardIdsByIlike(
        String keyword,
        long offset,
        int limit,
        SortOrder order,
        SearchUserContext context,
        List<Long> excludeIds
    ) {
        return searchNativeQueryExecutor.fetchBoardIdsByIlike(
            keyword,
            offset,
            limit,
            order,
            context.userId(),
            context.isManagerOrAdmin(),
            excludeIds
        );
    }

    private List<Long> fetchArticleIdsByFts(
        String keyword,
        long offset,
        int limit,
        SortOrder order,
        String boardSlug,
        SearchUserContext context
    ) {
        return searchNativeQueryExecutor.fetchArticleIdsByFts(
            keyword,
            offset,
            limit,
            order,
            boardSlug,
            context.userId(),
            context.isManagerOrAdmin()
        );
    }

    private List<Long> fetchArticleIdsByIlikePrimary(
        String keyword,
        long offset,
        int limit,
        SortOrder order,
        String boardSlug,
        SearchUserContext context,
        List<Long> excludeIds
    ) {
        return searchNativeQueryExecutor.fetchArticleIdsByIlikePrimary(
            keyword,
            offset,
            limit,
            order,
            boardSlug,
            context.userId(),
            context.isManagerOrAdmin(),
            excludeIds
        );
    }

    private List<Long> fetchArticleIdsByIlikeContent(
        String keyword,
        long offset,
        int limit,
        SortOrder order,
        String boardSlug,
        SearchUserContext context,
        List<Long> excludeIds
    ) {
        return searchNativeQueryExecutor.fetchArticleIdsByIlikeContent(
            keyword,
            offset,
            limit,
            order,
            boardSlug,
            context.userId(),
            context.isManagerOrAdmin(),
            excludeIds
        );
    }

    private List<Long> fetchCommentIdsByFts(
        String keyword,
        long offset,
        int limit,
        SortOrder order,
        String boardSlug,
        SearchUserContext context
    ) {
        return searchNativeQueryExecutor.fetchCommentIdsByFts(
            keyword,
            offset,
            limit,
            order,
            boardSlug,
            context.userId(),
            context.isManagerOrAdmin()
        );
    }

    private List<Long> fetchCommentIdsByIlike(
        String keyword,
        long offset,
        int limit,
        SortOrder order,
        String boardSlug,
        SearchUserContext context,
        List<Long> excludeIds
    ) {
        return searchNativeQueryExecutor.fetchCommentIdsByIlike(
            keyword,
            offset,
            limit,
            order,
            boardSlug,
            context.userId(),
            context.isManagerOrAdmin(),
            excludeIds
        );
    }

    private List<Long> fetchUserIdsByFts(
        String keyword,
        long offset,
        int limit,
        SortOrder order
    ) {
        return searchNativeQueryExecutor.fetchUserIdsByFts(
            keyword,
            offset,
            limit,
            order
        );
    }

    private List<Long> fetchUserIdsByIlike(
        String keyword,
        long offset,
        int limit,
        SortOrder order,
        List<Long> excludeIds
    ) {
        return searchNativeQueryExecutor.fetchUserIdsByIlike(
            keyword,
            offset,
            limit,
            order,
            excludeIds
        );
    }

    private List<BoardEntity> loadBoards(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        QBoardEntity board = QBoardEntity.boardEntity;
        List<BoardEntity> rows = queryFactory.selectFrom(board)
            .where(board.id.in(ids))
            .fetch();
        return orderByIds(ids, rows, BoardEntity::getId);
    }

    private List<ArticleEntity> loadArticles(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        QArticleEntity article = QArticleEntity.articleEntity;
        QBoardEntity board = QBoardEntity.boardEntity;
        QUserEntity author = QUserEntity.userEntity;
        List<ArticleEntity> rows = queryFactory.selectFrom(article)
            .join(article.board, board)
            .join(article.user, author)
            .where(article.id.in(ids))
            .fetch();
        return orderByIds(ids, rows, ArticleEntity::getId);
    }

    private List<CommentEntity> loadComments(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        QCommentEntity comment = QCommentEntity.commentEntity;
        QArticleEntity article = QArticleEntity.articleEntity;
        QBoardEntity board = QBoardEntity.boardEntity;
        QUserEntity author = QUserEntity.userEntity;
        List<CommentEntity> rows = queryFactory.selectFrom(comment)
            .join(comment.article, article)
            .join(article.board, board)
            .join(comment.user, author)
            .where(comment.id.in(ids))
            .fetch();
        return orderByIds(ids, rows, CommentEntity::getId);
    }

    private List<UserEntity> loadUsers(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        QUserEntity user = QUserEntity.userEntity;
        List<UserEntity> rows = queryFactory.selectFrom(user)
            .where(user.id.in(ids))
            .fetch();
        return orderByIds(ids, rows, UserEntity::getId);
    }

    private <T> List<T> orderByIds(List<Long> ids, List<T> rows, Function<T, Long> idExtractor) {
        Map<Long, T> map = new HashMap<>();
        for (T row : rows) {
            map.put(idExtractor.apply(row), row);
        }
        List<T> ordered = new ArrayList<>();
        for (Long id : ids) {
            T row = map.get(id);
            if (row != null) {
                ordered.add(row);
            }
        }
        return ordered;
    }

    private boolean hasNext(List<Long> candidateIds, int pageSize) {
        return candidateIds.size() > pageSize;
    }

    private Set<Long> takeUpToSize(List<Long> candidateIds, int size) {
        Set<Long> ids = new LinkedHashSet<>();
        addUpToSize(ids, candidateIds, size);
        return ids;
    }

    private void addUpToSize(Set<Long> targetIds, List<Long> candidateIds, int maxSize) {
        for (Long id : candidateIds) {
            if (targetIds.size() >= maxSize) {
                break;
            }
            targetIds.add(id);
        }
    }

    private boolean hasRemainingSlot(Set<Long> ids, int maxSize) {
        return ids.size() < maxSize;
    }

    private int remainingSlots(Set<Long> ids, int maxSize) {
        return maxSize - ids.size();
    }

    private <T> SliceResponse<T> emptySlice(int page, int size) {
        return new SliceResponse<>(List.of(), page, size, false, page > 0);
    }

    private Map<Long, FileResponse> resolveBoardImages(List<BoardEntity> boards) {
        if (boards.isEmpty()) {
            return Map.of();
        }
        List<Long> boardIds = boards.stream()
            .map(BoardEntity::getId)
            .toList();
        List<BoardFileEntity> files = boardFileRepository.findAllByBoardIdInOrderByCreatedAtDesc(boardIds);
        Map<Long, FileResponse> result = new HashMap<>();
        for (BoardFileEntity boardFile : files) {
            Long boardId = boardFile.getBoard().getId();
            if (result.containsKey(boardId)) {
                continue;
            }
            FileEntity file = boardFile.getFile();
            if (file.isDeleted()) {
                continue;
            }
            result.put(boardId, fileMapper.toResponse(file));
        }
        return result;
    }

    private Map<Long, Long> loadCommentCounts(List<ArticleEntity> articles) {
        Set<Long> ids = toArticleIds(articles);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countByArticleIds(ids).stream()
            .collect(java.util.stream.Collectors.toMap(
                CommentRepository.CommentCountView::getArticleId,
                CommentRepository.CommentCountView::getCount
            ));
    }

    private Map<Long, ReactionCounts> loadReactionCounts(List<ArticleEntity> articles) {
        Set<Long> ids = toArticleIds(articles);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ReactionCounts> counts = new HashMap<>();
        List<ArticleReactionRepository.ArticleReactionCountView> rows = articleReactionRepository.countByArticleIds(ids);
        for (ArticleReactionRepository.ArticleReactionCountView row : rows) {
            ReactionCounts current = counts.getOrDefault(row.getArticleId(), ReactionCounts.empty());
            if (row.getReactionType() == 1) {
                counts.put(row.getArticleId(), current.withLike(row.getCount()));
            } else if (row.getReactionType() == -1) {
                counts.put(row.getArticleId(), current.withDislike(row.getCount()));
            }
        }
        return counts;
    }

    private Set<Long> toArticleIds(List<ArticleEntity> articles) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ArticleEntity article : articles) {
            ids.add(article.getId());
        }
        return ids;
    }

    private ArticleSearchResponse toArticleSearchResponse(
        ArticleEntity entity,
        Map<Long, Long> commentCounts,
        Map<Long, ReactionCounts> reactionCounts
    ) {
        ReactionCounts counts = reactionCounts.getOrDefault(entity.getId(), ReactionCounts.empty());
        return new ArticleSearchResponse(
            entity.getId(),
            entity.getBoard().getId(),
            entity.getBoard().getSlug(),
            entity.getBoard().getBoardName(),
            entity.getUser().getId(),
            authorDisplayResolver.resolveAuthorName(entity.getUser()),
            entity.getTitle(),
            entity.getCategory() != null ? entity.getCategory().getId() : null,
            entity.getCategory() != null ? entity.getCategory().getCategoryName() : null,
            entity.getHit(),
            commentCounts.getOrDefault(entity.getId(), 0L),
            counts.likeCount(),
            counts.dislikeCount(),
            entity.isNotice(),
            entity.getCreatedAt()
        );
    }

    private CommentSearchResponse toCommentSearchResponse(CommentEntity entity) {
        ArticleEntity article = entity.getArticle();
        BoardEntity board = article.getBoard();
        return new CommentSearchResponse(
            entity.getId(),
            article.getId(),
            article.getTitle(),
            board.getId(),
            board.getSlug(),
            board.getBoardName(),
            entity.getUser().getId(),
            authorDisplayResolver.resolveAuthorName(entity.getUser()),
            entity.getContent(),
            entity.getCreatedAt()
        );
    }

    private SearchUserContext resolveUserContext() {
        return currentUserService.getOptionalUserId()
            .map(this::loadUserContext)
            .orElse(SearchUserContext.anonymous());
    }

    private SearchUserContext loadUserContext(Long userId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return new SearchUserContext(userId, roleEvaluator.isManagerOrAdmin(user));
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new ApiException(ErrorCode.SEARCH_KEYWORD_REQUIRED);
        }
        return keyword.trim();
    }

    private record ReactionCounts(long likeCount, long dislikeCount) {
        private static ReactionCounts empty() {
            return new ReactionCounts(0, 0);
        }

        private ReactionCounts withLike(long count) {
            return new ReactionCounts(count, dislikeCount);
        }

        private ReactionCounts withDislike(long count) {
            return new ReactionCounts(likeCount, count);
        }
    }

    private record SearchUserContext(Long userId, boolean managerOrAdmin) {
        private static SearchUserContext anonymous() {
            return new SearchUserContext(null, false);
        }

        private boolean isManagerOrAdmin() {
            return managerOrAdmin;
        }
    }
}
