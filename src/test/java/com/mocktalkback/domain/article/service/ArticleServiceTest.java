package com.mocktalkback.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

import com.mocktalkback.domain.article.dto.ArticleCategoryResponse;
import com.mocktalkback.domain.article.dto.ArticleCreateRequest;
import com.mocktalkback.domain.article.dto.ArticleDetailResponse;
import com.mocktalkback.domain.article.dto.ArticleRecentItemResponse;
import com.mocktalkback.domain.article.dto.ArticleReactionSummaryResponse;
import com.mocktalkback.domain.article.dto.ArticleReactionToggleRequest;
import com.mocktalkback.domain.article.dto.ArticleUpdateRequest;
import com.mocktalkback.domain.article.dto.BoardArticleListResponse;
import com.mocktalkback.domain.article.entity.ArticleCategoryEntity;
import com.mocktalkback.domain.article.entity.ArticleEntity;
import com.mocktalkback.domain.article.entity.ArticleFileEntity;
import com.mocktalkback.domain.article.mapper.ArticleMapper;
import com.mocktalkback.domain.article.policy.PublicArticleFeedPolicy;
import com.mocktalkback.domain.article.repository.ArticleBookmarkRepository;
import com.mocktalkback.domain.article.repository.ArticleCategoryRepository;
import com.mocktalkback.domain.article.repository.ArticleFileRepository;
import com.mocktalkback.domain.article.repository.ArticleReactionRepository;
import com.mocktalkback.domain.article.repository.ArticleRepository;
import com.mocktalkback.domain.article.type.ArticleContentFormat;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.repository.BoardFileRepository;
import com.mocktalkback.domain.board.repository.BoardMemberRepository;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.board.type.BoardArticleWritePolicy;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.comment.repository.CommentRepository;
import com.mocktalkback.domain.common.policy.AuthorDisplayResolver;
import com.mocktalkback.domain.common.policy.BoardAccessPolicy;
import com.mocktalkback.domain.common.policy.PageNormalizer;
import com.mocktalkback.domain.common.policy.RoleEvaluator;
import com.mocktalkback.domain.common.policy.SanctionGuard;
import com.mocktalkback.domain.file.entity.FileClassEntity;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.mapper.FileMapper;
import com.mocktalkback.domain.file.repository.FileRepository;
import com.mocktalkback.domain.file.repository.FileVariantRepository;
import com.mocktalkback.domain.file.service.FileStorage;
import com.mocktalkback.domain.file.service.TemporaryFilePolicy;
import com.mocktalkback.domain.file.type.FileClassCode;
import com.mocktalkback.domain.file.type.MediaKind;
import com.mocktalkback.domain.realtime.service.BoardRealtimeSseService;
import com.mocktalkback.domain.role.entity.RoleEntity;
import com.mocktalkback.domain.role.type.ContentVisibility;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;
import com.mocktalkback.global.common.dto.SliceResponse;
import com.mocktalkback.global.common.type.SortOrder;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardMemberRepository boardMemberRepository;

    @Mock
    private BoardFileRepository boardFileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ArticleCategoryRepository articleCategoryRepository;

    @Mock
    private ArticleFileRepository articleFileRepository;

    @Mock
    private ArticleBookmarkRepository articleBookmarkRepository;

    @Mock
    private ArticleReactionRepository articleReactionRepository;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileMapper fileMapper;

    @Mock
    private FileVariantRepository fileVariantRepository;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private TemporaryFilePolicy temporaryFilePolicy;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ArticleContentService articleContentService;

    @Mock
    private ArticleViewService articleViewService;

    @Mock
    private ArticleTrendingService articleTrendingService;

    @Mock
    private SanctionGuard sanctionGuard;

    @Mock
    private BoardRealtimeSseService boardRealtimeSseService;

    @Spy
    private RoleEvaluator roleEvaluator = new RoleEvaluator();

    @Spy
    private BoardAccessPolicy boardAccessPolicy = new BoardAccessPolicy(roleEvaluator);

    @Spy
    private PageNormalizer pageNormalizer = new PageNormalizer();

    @Spy
    private AuthorDisplayResolver authorDisplayResolver = new AuthorDisplayResolver();

    @Spy
    private PublicArticleFeedPolicy publicArticleFeedPolicy = new PublicArticleFeedPolicy();

    @InjectMocks
    private ArticleService articleService;

    // 게시글 생성 시 fileIds가 매핑되고 임시 상태가 해제되어야 한다.
    @Test
    void create_attaches_files_and_clears_temporary() {
        // Given: 게시글 생성 요청과 파일 목록
        BoardEntity board = createBoard(1L);
        UserEntity user = createUser(2L);
        ArticleCategoryEntity category = createCategory(3L, board);
        ArticleCreateRequest request = new ArticleCreateRequest(
            1L,
            2L,
            3L,
            ContentVisibility.PUBLIC,
            "title",
            "content",
            ArticleContentFormat.HTML,
            false,
            List.of(10L, 20L)
        );

        when(currentUserService.getUserId()).thenReturn(2L);
        when(boardRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(board));
        when(boardMemberRepository.findByUserIdAndBoardId(2L, 1L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(articleCategoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(articleContentService.render("content", ArticleContentFormat.HTML))
            .thenReturn(new ArticleContentService.RenderedContent("content", "content"));

        ArticleEntity article = createArticle(100L, board, user, category);
        when(articleMapper.toEntity(any(ArticleCreateRequest.class), eq(board), eq(user), eq(category)))
            .thenReturn(article);
        when(articleRepository.save(article)).thenReturn(article);

        FileClassEntity fileClass = createFileClass(FileClassCode.ARTICLE_CONTENT_IMAGE);
        FileEntity fileA = createFile(10L, fileClass, Instant.parse("2024-01-01T00:00:00Z"));
        FileEntity fileB = createFile(20L, fileClass, Instant.parse("2024-01-01T00:00:00Z"));
        when(fileRepository.findById(10L)).thenReturn(Optional.of(fileA));
        when(fileRepository.findById(20L)).thenReturn(Optional.of(fileB));

        // When: 게시글 생성 호출
        articleService.create(request);

        // Then: 매핑 저장과 임시 해제 확인
        verify(articleFileRepository, times(2)).save(any(ArticleFileEntity.class));
        assertThat(fileA.getTempExpiresAt()).isNull();
        assertThat(fileB.getTempExpiresAt()).isNull();
    }

    // 게시글 생성 시 다른 게시판 카테고리를 지정하면 예외가 발생해야 한다.
    @Test
    void create_throws_when_category_not_belongs_to_board() {
        // Given: 게시판과 다른 소속의 카테고리
        BoardEntity board = createBoard(1L);
        UserEntity user = createUser(2L);
        BoardEntity otherBoard = createBoard(99L);
        ArticleCategoryEntity otherBoardCategory = createCategory(3L, otherBoard);
        ArticleCreateRequest request = new ArticleCreateRequest(
            1L,
            2L,
            3L,
            ContentVisibility.PUBLIC,
            "title",
            "content",
            ArticleContentFormat.HTML,
            false,
            List.of()
        );

        when(currentUserService.getUserId()).thenReturn(2L);
        when(boardRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(board));
        when(boardMemberRepository.findByUserIdAndBoardId(2L, 1L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(articleCategoryRepository.findById(3L)).thenReturn(Optional.of(otherBoardCategory));

        // When & Then: 소속 불일치 예외 확인
        assertThatThrownBy(() -> articleService.create(request))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BOARD_CATEGORY_INVALID);
    }

    // 게시글 생성 시 게시판 작성 정책이 MEMBER면 비멤버는 차단되어야 한다.
    @Test
    void create_throws_when_write_policy_is_member_and_user_is_not_member() {
        // Given: 멤버 이상 작성 정책 게시판과 비멤버 사용자
        BoardEntity board = createBoard(1L, BoardArticleWritePolicy.MEMBER);
        UserEntity user = createUser(2L);
        ArticleCreateRequest request = new ArticleCreateRequest(
            1L,
            2L,
            null,
            ContentVisibility.PUBLIC,
            "title",
            "content",
            ArticleContentFormat.HTML,
            false,
            List.of()
        );

        when(currentUserService.getUserId()).thenReturn(2L);
        when(boardRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(board));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(boardMemberRepository.findByUserIdAndBoardId(2L, 1L)).thenReturn(Optional.empty());

        // When & Then: 비멤버 작성 차단 예외 확인
        assertThatThrownBy(() -> articleService.create(request))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // 게시글 수정 시 신규 파일은 매핑하고 제거된 파일은 임시 처리해야 한다.
    @Test
    void update_syncs_files_and_marks_removed_as_temporary() {
        // Given: 기존 게시글과 첨부 상태
        BoardEntity board = createBoard(1L);
        UserEntity user = createUser(2L);
        ArticleCategoryEntity category = createCategory(3L, board);
        ArticleEntity article = createArticle(100L, board, user, category);

        when(articleRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(article));
        when(currentUserService.getUserId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(articleCategoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(articleContentService.render("content", ArticleContentFormat.HTML))
            .thenReturn(new ArticleContentService.RenderedContent("content", "content"));

        FileClassEntity fileClass = createFileClass(FileClassCode.ARTICLE_CONTENT_IMAGE);
        FileEntity existingFile = createFile(10L, fileClass, null);
        ArticleFileEntity existingMapping = ArticleFileEntity.builder()
            .article(article)
            .file(existingFile)
            .build();
        when(articleFileRepository.findAllByArticleIdOrderByCreatedAtAsc(100L))
            .thenReturn(List.of(existingMapping));
        when(articleFileRepository.existsByFileId(10L)).thenReturn(false);

        FileEntity newFile = createFile(20L, fileClass, Instant.parse("2024-01-01T00:00:00Z"));
        when(fileRepository.findById(20L)).thenReturn(Optional.of(newFile));

        Instant expiry = Instant.parse("2024-02-01T00:00:00Z");
        when(temporaryFilePolicy.resolveExpiry()).thenReturn(expiry);

        ArticleUpdateRequest request = new ArticleUpdateRequest(
            3L,
            ContentVisibility.PUBLIC,
            "title",
            "content",
            ArticleContentFormat.HTML,
            false,
            List.of(20L)
        );

        // When: 게시글 수정 호출
        articleService.update(100L, request);

        // Then: 신규 매핑과 제거된 파일 임시 처리 확인
        verify(articleFileRepository).save(any(ArticleFileEntity.class));
        verify(articleFileRepository).delete(existingMapping);
        assertThat(existingFile.getTempExpiresAt()).isEqualTo(expiry);
        assertThat(newFile.getTempExpiresAt()).isNull();
    }

    // 게시판 카테고리 목록 조회는 접근 가능한 게시판에서 카테고리 응답을 반환해야 한다.
    @Test
    void getBoardCategories_returns_list_when_board_is_accessible() {
        // Given: 공개 게시판과 카테고리 목록
        BoardEntity board = createBoard(1L);
        ArticleCategoryEntity category = createCategory(3L, board);
        ArticleCategoryResponse categoryResponse = new ArticleCategoryResponse(
            3L,
            1L,
            "공지",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-01T00:00:00Z")
        );

        when(boardRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(board));
        when(currentUserService.getOptionalUserId()).thenReturn(Optional.empty());
        when(articleCategoryRepository.findAllByBoardIdOrderByCategoryNameAsc(1L)).thenReturn(List.of(category));
        when(articleMapper.toResponse(category)).thenReturn(categoryResponse);

        // When: 게시판 카테고리 목록 조회
        List<ArticleCategoryResponse> result = articleService.getBoardCategories(1L);

        // Then: 카테고리 응답 확인
        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoryName()).isEqualTo("공지");
    }

    // 게시글 상세 조회는 조회수 증가 요청 시 조회 dedupe 서비스의 최신 hit 값을 반영해야 한다.
    @Test
    void findDetailById_uses_article_view_service_when_view_is_eligible() {
        // Given: 공개 게시판의 게시글과 증가된 조회수 값
        BoardEntity board = createBoard(1L);
        UserEntity user = createUser(2L);
        ArticleEntity article = createArticle(10L, board, user, null);
        ReflectionTestUtils.setField(article, "hit", 7L);

        when(articleRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(article));
        when(currentUserService.getOptionalUserId()).thenReturn(Optional.empty());
        when(articleViewService.increaseHitIfEligible(10L, 7L, "127.0.0.1", "MockBrowser/1.0")).thenReturn(8L);
        when(commentRepository.countByArticleIds(List.of(10L))).thenReturn(List.of());
        when(articleFileRepository.findAllByArticleIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());
        when(boardFileRepository.findAllByBoardIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        // When: 상세 조회에서 조회수 증가를 요청하면
        ArticleDetailResponse response = articleService.findDetailById(10L, "127.0.0.1", "MockBrowser/1.0");

        // Then: 조회 dedupe 서비스의 최신 값을 응답에 반영해야 한다.
        assertThat(response.hit()).isEqualTo(8L);
        verify(articleViewService).increaseHitIfEligible(10L, 7L, "127.0.0.1", "MockBrowser/1.0");
    }

    // 게시글 상세 조회는 중복 조회거나 Redis 장애면 현재 hit 값을 그대로 반환해야 한다.
    @Test
    void findDetailById_returns_current_hit_when_view_is_not_eligible() {
        // Given: 공개 게시판의 게시글과 현재 조회수
        BoardEntity board = createBoard(1L);
        UserEntity user = createUser(2L);
        ArticleEntity article = createArticle(10L, board, user, null);
        ReflectionTestUtils.setField(article, "hit", 7L);

        when(articleRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(article));
        when(currentUserService.getOptionalUserId()).thenReturn(Optional.empty());
        when(articleViewService.increaseHitIfEligible(10L, 7L, "127.0.0.1", "MockBrowser/1.0")).thenReturn(7L);
        when(commentRepository.countByArticleIds(List.of(10L))).thenReturn(List.of());
        when(articleFileRepository.findAllByArticleIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());
        when(boardFileRepository.findAllByBoardIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        // When: 상세 조회에서 조회 dedupe가 증가 불가를 반환하면
        ArticleDetailResponse response = articleService.findDetailById(10L, "127.0.0.1", "MockBrowser/1.0");

        // Then: 현재 조회수만 반환해야 한다.
        assertThat(response.hit()).isEqualTo(7L);
        verify(articleViewService).increaseHitIfEligible(10L, 7L, "127.0.0.1", "MockBrowser/1.0");
    }

    // 게시글 목록 조회는 카테고리 필터가 있으면 고정글을 제외하고 카테고리 기준으로 조회해야 한다.
    @Test
    void getBoardArticles_with_category_filter_uses_category_query_without_pinned() {
        // Given: 공개 게시판과 카테고리 필터
        BoardEntity board = createBoard(1L);
        ArticleCategoryEntity category = createCategory(3L, board);
        Page<ArticleEntity> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);

        when(boardRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(board));
        when(currentUserService.getOptionalUserId()).thenReturn(Optional.empty());
        when(articleCategoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(articleRepository.findByBoardIdAndCategoryIdAndNoticeFalseAndVisibilityInAndDeletedAtIsNull(
            eq(1L),
            eq(3L),
            any(),
            any()
        )).thenReturn(emptyPage);

        // When: 카테고리 필터로 게시글 목록 조회
        BoardArticleListResponse result = articleService.getBoardArticles(1L, 0, 10, SortOrder.LATEST, 3L, false);

        // Then: 카테고리 조회 메서드 사용 및 pinned 미조회 확인
        assertThat(result.pinned()).isEmpty();
        assertThat(result.page().items()).isEmpty();
        verify(articleRepository).findByBoardIdAndCategoryIdAndNoticeFalseAndVisibilityInAndDeletedAtIsNull(
            eq(1L),
            eq(3L),
            any(),
            any()
        );
        verify(articleRepository, never()).findByBoardIdAndNoticeTrueAndVisibilityInAndDeletedAtIsNull(
            anyLong(),
            any(),
            any()
        );
    }

    // 게시글 목록 조회는 미분류 필터가 있으면 카테고리 null 기준으로 조회하고 고정글을 제외해야 한다.
    @Test
    void getBoardArticles_with_uncategorized_filter_uses_null_category_query_without_pinned() {
        // Given: 공개 게시판과 미분류 필터
        BoardEntity board = createBoard(1L);
        Page<ArticleEntity> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);

        when(boardRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(board));
        when(currentUserService.getOptionalUserId()).thenReturn(Optional.empty());
        when(articleRepository.findByBoardIdAndCategoryIsNullAndNoticeFalseAndVisibilityInAndDeletedAtIsNull(
            eq(1L),
            any(),
            any()
        )).thenReturn(emptyPage);

        // When: 미분류 필터로 게시글 목록 조회
        BoardArticleListResponse result = articleService.getBoardArticles(1L, 0, 10, SortOrder.LATEST, null, true);

        // Then: 미분류 조회 메서드 사용 및 pinned 미조회 확인
        assertThat(result.pinned()).isEmpty();
        assertThat(result.page().items()).isEmpty();
        verify(articleRepository).findByBoardIdAndCategoryIsNullAndNoticeFalseAndVisibilityInAndDeletedAtIsNull(
            eq(1L),
            any(),
            any()
        );
        verify(articleRepository, never()).findByBoardIdAndNoticeTrueAndVisibilityInAndDeletedAtIsNull(
            anyLong(),
            any(),
            any()
        );
    }

    // 게시글 목록 조회는 게시글 카테고리 정보를 요약 응답에 포함해야 한다.
    @Test
    void getBoardArticles_maps_category_fields_in_summary_response() {
        // Given: 카테고리가 지정된 게시글 한 건
        BoardEntity board = createBoard(1L);
        UserEntity user = createUser(2L);
        ArticleCategoryEntity category = createCategory(3L, board);
        ArticleEntity article = createArticle(10L, board, user, category);
        Page<ArticleEntity> page = new PageImpl<>(List.of(article), PageRequest.of(0, 10), 1L);

        when(boardRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(board));
        when(currentUserService.getOptionalUserId()).thenReturn(Optional.empty());
        when(articleCategoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(articleRepository.findByBoardIdAndCategoryIdAndNoticeFalseAndVisibilityInAndDeletedAtIsNull(
            eq(1L),
            eq(3L),
            any(),
            any()
        )).thenReturn(page);
        when(commentRepository.countByArticleIds(any())).thenReturn(List.of());
        when(articleReactionRepository.countByArticleIds(any())).thenReturn(List.of());

        // When: 카테고리 필터로 게시글 목록을 조회하면
        BoardArticleListResponse result = articleService.getBoardArticles(1L, 0, 10, SortOrder.LATEST, 3L, false);

        // Then: 카테고리 ID와 이름이 함께 응답되어야 한다.
        assertThat(result.page().items()).hasSize(1);
        assertThat(result.page().items().get(0).categoryId()).isEqualTo(3L);
        assertThat(result.page().items().get(0).categoryName()).isEqualTo("공지");
    }

    // 게시글 목록 조회는 categoryId와 uncategorized를 동시에 사용하면 예외가 발생해야 한다.
    @Test
    void getBoardArticles_throws_when_category_and_uncategorized_used_together() {
        // Given: 동시 필터 입력

        // When & Then: 동시 필터 사용 예외 확인
        assertThatThrownBy(() -> articleService.getBoardArticles(1L, 0, 10, SortOrder.LATEST, 3L, true))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.ARTICLE_CATEGORY_FILTER_CONFLICT);
    }

    // 홈 최근 공개 게시글 조회는 공개 게시글 요약 응답을 반환해야 한다.
    @Test
    void findRecentPublic_returns_recent_public_items() {
        // Given: 공개 게시판의 최신 게시글 슬라이스
        BoardEntity board = createBoard(1L);
        ReflectionTestUtils.setField(board, "slug", "free");
        ReflectionTestUtils.setField(board, "boardName", "자유게시판");
        UserEntity user = createUser(2L);
        ArticleEntity article = ArticleEntity.builder()
            .board(board)
            .user(user)
            .category(null)
            .visibility(ContentVisibility.PUBLIC)
            .title("첫 글")
            .content("<p>첫 줄 <strong>강조</strong></p>")
            .contentSource("첫 줄 **강조**")
            .contentFormat(ArticleContentFormat.HTML)
            .notice(false)
            .hit(12L)
            .build();
        ReflectionTestUtils.setField(article, "id", 100L);
        Slice<ArticleEntity> slice = new SliceImpl<>(List.of(article), PageRequest.of(0, 8), true);

        CommentRepository.CommentCountView commentCountView = org.mockito.Mockito.mock(CommentRepository.CommentCountView.class);
        when(commentCountView.getArticleId()).thenReturn(100L);
        when(commentCountView.getCount()).thenReturn(3L);

        ArticleReactionRepository.ArticleReactionCountView reactionCountView = org.mockito.Mockito.mock(
            ArticleReactionRepository.ArticleReactionCountView.class
        );
        when(reactionCountView.getArticleId()).thenReturn(100L);
        when(reactionCountView.getReactionType()).thenReturn((short) 1);
        when(reactionCountView.getCount()).thenReturn(7L);

        when(articleRepository.findByBoardVisibilityAndBoardDeletedAtIsNullAndBoardSlugNotInAndVisibilityAndNoticeFalseAndDeletedAtIsNull(
            eq(BoardVisibility.PUBLIC),
            eq(Set.of("notice", "inquiry")),
            eq(ContentVisibility.PUBLIC),
            any()
        )).thenReturn(slice);
        when(commentRepository.countByArticleIds(any())).thenReturn(List.of(commentCountView));
        when(articleReactionRepository.countByArticleIds(List.of(100L))).thenReturn(List.of(reactionCountView));

        // When: 홈 최근 공개 게시글을 조회하면
        SliceResponse<ArticleRecentItemResponse> response = articleService.findRecentPublic(0, 8);

        // Then: 게시판/미리보기/집계가 포함된 응답을 반환한다.
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).boardSlug()).isEqualTo("free");
        assertThat(response.items().get(0).previewText()).isEqualTo("첫 줄 강조");
        assertThat(response.items().get(0).commentCount()).isEqualTo(3L);
        assertThat(response.items().get(0).likeCount()).isEqualTo(7L);
        assertThat(response.items().get(0).hit()).isEqualTo(12L);
        assertThat(response.hasNext()).isTrue();
        verify(articleRepository).findByBoardVisibilityAndBoardDeletedAtIsNullAndBoardSlugNotInAndVisibilityAndNoticeFalseAndDeletedAtIsNull(
            eq(BoardVisibility.PUBLIC),
            eq(Set.of("notice", "inquiry")),
            eq(ContentVisibility.PUBLIC),
            any()
        );
    }

    // 반응 토글은 원자 upsert를 사용해 경합 상황에서도 일관된 결과를 반환해야 한다.
    @Test
    void toggleReaction_uses_atomic_upsert_and_returns_summary() {
        // Given: 공개 게시글에 대한 반응 토글 요청
        BoardEntity board = createBoard(1L);
        UserEntity user = createUser(2L);
        ArticleEntity article = createArticle(10L, board, user, null);

        when(currentUserService.getUserId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(articleRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(article));
        when(boardMemberRepository.findByUserIdAndBoardId(2L, 1L)).thenReturn(Optional.empty());
        when(articleReactionRepository.upsertToggleReaction(2L, 10L, (short) 1)).thenReturn((short) 1);
        when(articleReactionRepository.countByArticleIdAndReactionType(10L, (short) 1)).thenReturn(5L);
        when(articleReactionRepository.countByArticleIdAndReactionType(10L, (short) -1)).thenReturn(2L);

        // When: 반응 토글 실행
        ArticleReactionSummaryResponse response = articleService.toggleReaction(
            10L,
            new ArticleReactionToggleRequest((short) 1)
        );

        // Then: 원자 토글 결과와 집계가 반환되어야 한다.
        assertThat(response.articleId()).isEqualTo(10L);
        assertThat(response.myReaction()).isEqualTo((short) 1);
        assertThat(response.likeCount()).isEqualTo(5L);
        assertThat(response.dislikeCount()).isEqualTo(2L);
        verify(articleReactionRepository).upsertToggleReaction(2L, 10L, (short) 1);
        verify(articleTrendingService).recordArticleReactionChanged(10L, (short) 0, (short) 1);
    }

    // 첨부파일 다운로드 URL 조회는 접근 가능한 게시글의 첨부파일 URL을 반환해야 한다.
    @Test
    void resolveAttachmentDownloadLocation_returns_storage_url_for_accessible_attachment() {
        // Given: 공개 게시글과 첨부파일 매핑
        BoardEntity board = createBoard(1L);
        UserEntity author = createUser(2L);
        ArticleEntity article = createArticle(10L, board, author, null);
        FileClassEntity fileClass = createFileClass(FileClassCode.ARTICLE_ATTACHMENT);
        FileEntity attachment = createFile(20L, fileClass, null);
        ReflectionTestUtils.setField(attachment, "fileName", "guide.txt");
        ReflectionTestUtils.setField(attachment, "mimeType", "text/plain");
        ReflectionTestUtils.setField(attachment, "storageKey", "/uploads/attachment-20.zip");
        ArticleFileEntity mapping = ArticleFileEntity.builder()
            .article(article)
            .file(attachment)
            .build();

        when(articleRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(article));
        when(currentUserService.getOptionalUserId()).thenReturn(Optional.empty());
        when(articleFileRepository.findByArticleIdAndFileId(10L, 20L)).thenReturn(Optional.of(mapping));
        when(fileStorage.resolveDownloadUrl("/uploads/attachment-20.zip", "guide.txt", "text/plain"))
            .thenReturn("https://storage.mocktalk.local/attachment-20.zip");

        // When: 첨부파일 다운로드 URL을 조회하면
        String location = articleService.resolveAttachmentDownloadLocation(10L, 20L);

        // Then: 저장소 조회 URL을 반환한다.
        assertThat(location).isEqualTo("https://storage.mocktalk.local/attachment-20.zip");
    }

    // 첨부파일 다운로드 URL 조회는 첨부 타입이 아니면 404를 반환해야 한다.
    @Test
    void resolveAttachmentDownloadLocation_throws_when_file_is_not_attachment_type() {
        // Given: 게시글에 매핑된 파일이 첨부 타입이 아닌 경우
        BoardEntity board = createBoard(1L);
        UserEntity author = createUser(2L);
        ArticleEntity article = createArticle(10L, board, author, null);
        FileClassEntity fileClass = createFileClass(FileClassCode.ARTICLE_CONTENT_IMAGE);
        FileEntity imageFile = createFile(20L, fileClass, null);
        ArticleFileEntity mapping = ArticleFileEntity.builder()
            .article(article)
            .file(imageFile)
            .build();

        when(articleRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(article));
        when(currentUserService.getOptionalUserId()).thenReturn(Optional.empty());
        when(articleFileRepository.findByArticleIdAndFileId(10L, 20L)).thenReturn(Optional.of(mapping));

        // When & Then: 첨부 타입이 아니면 404 예외가 발생한다.
        assertThatThrownBy(() -> articleService.resolveAttachmentDownloadLocation(10L, 20L))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.ATTACHMENT_NOT_FOUND);
    }

    private BoardEntity createBoard(Long id) {
        return createBoard(id, BoardArticleWritePolicy.ALL_AUTHENTICATED);
    }

    private BoardEntity createBoard(Long id, BoardArticleWritePolicy writePolicy) {
        BoardEntity board = BoardEntity.builder()
            .boardName("notice")
            .slug("notice")
            .description("테스트 게시판")
            .visibility(BoardVisibility.PUBLIC)
            .articleWritePolicy(writePolicy)
            .build();
        ReflectionTestUtils.setField(board, "id", id);
        return board;
    }

    private UserEntity createUser(Long id) {
        RoleEntity role = RoleEntity.create("USER", 0, "테스트");
        UserEntity user = UserEntity.createLocal(
            role,
            "login",
            "user@test.com",
            "pw",
            "user",
            "display",
            "handle"
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ArticleCategoryEntity createCategory(Long id, BoardEntity board) {
        ArticleCategoryEntity category = ArticleCategoryEntity.builder()
            .board(board)
            .categoryName("공지")
            .build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private ArticleEntity createArticle(
        Long id,
        BoardEntity board,
        UserEntity user,
        ArticleCategoryEntity category
    ) {
        ArticleEntity article = ArticleEntity.builder()
            .board(board)
            .user(user)
            .category(category)
            .visibility(ContentVisibility.PUBLIC)
            .title("title")
            .content("content")
            .contentSource("content")
            .contentFormat(ArticleContentFormat.HTML)
            .notice(false)
            .hit(0)
            .build();
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private FileClassEntity createFileClass(String code) {
        return FileClassEntity.builder()
            .code(code)
            .name("테스트 파일 클래스")
            .description("테스트")
            .mediaKind(MediaKind.IMAGE)
            .build();
    }

    private FileEntity createFile(Long id, FileClassEntity fileClass, Instant tempExpiresAt) {
        FileEntity file = FileEntity.builder()
            .fileClass(fileClass)
            .fileName("file.png")
            .storageKey("/uploads/test/file.png")
            .fileSize(123L)
            .mimeType("image/png")
            .metadataPreserved(false)
            .tempExpiresAt(tempExpiresAt)
            .build();
        ReflectionTestUtils.setField(file, "id", id);
        return file;
    }
}
