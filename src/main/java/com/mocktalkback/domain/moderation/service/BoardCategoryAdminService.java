package com.mocktalkback.domain.moderation.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.article.dto.ArticleCategoryResponse;
import com.mocktalkback.domain.article.entity.ArticleCategoryEntity;
import com.mocktalkback.domain.article.mapper.ArticleMapper;
import com.mocktalkback.domain.article.repository.ArticleCategoryRepository;
import com.mocktalkback.domain.article.repository.ArticleRepository;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.moderation.dto.BoardCategoryCreateRequest;
import com.mocktalkback.domain.moderation.dto.BoardCategoryUpdateRequest;
import com.mocktalkback.domain.moderation.policy.BoardAdminPermissionGuard;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardCategoryAdminService {

    private final ArticleCategoryRepository articleCategoryRepository;
    private final ArticleRepository articleRepository;
    private final ArticleMapper articleMapper;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final BoardAdminPermissionGuard boardAdminPermissionGuard;

    @Transactional(readOnly = true)
    public List<ArticleCategoryResponse> findAll(Long boardId) {
        BoardEntity board = getBoard(boardId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);
        return articleCategoryRepository.findAllByBoardIdOrderByCategoryNameAsc(board.getId()).stream()
            .map(articleMapper::toResponse)
            .toList();
    }

    @Transactional
    public ArticleCategoryResponse create(Long boardId, BoardCategoryCreateRequest request) {
        BoardEntity board = getBoard(boardId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);
        String categoryName = normalizeName(request.categoryName());
        if (articleCategoryRepository.existsByBoardIdAndCategoryNameIgnoreCase(boardId, categoryName)) {
            throw new ApiException(ErrorCode.BOARD_CATEGORY_DUPLICATE);
        }
        ArticleCategoryEntity entity = ArticleCategoryEntity.builder()
            .board(board)
            .categoryName(categoryName)
            .build();
        ArticleCategoryEntity saved = articleCategoryRepository.save(entity);
        return articleMapper.toResponse(saved);
    }

    @Transactional
    public ArticleCategoryResponse update(Long boardId, Long categoryId, BoardCategoryUpdateRequest request) {
        BoardEntity board = getBoard(boardId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);
        ArticleCategoryEntity entity = getCategory(categoryId);
        ensureSameBoard(board, entity);
        String categoryName = normalizeName(request.categoryName());
        if (!entity.getCategoryName().equalsIgnoreCase(categoryName)
            && articleCategoryRepository.existsByBoardIdAndCategoryNameIgnoreCase(boardId, categoryName)) {
            throw new ApiException(ErrorCode.BOARD_CATEGORY_DUPLICATE);
        }
        entity.updateName(categoryName);
        return articleMapper.toResponse(entity);
    }

    @Transactional
    public void delete(Long boardId, Long categoryId) {
        BoardEntity board = getBoard(boardId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);
        ArticleCategoryEntity entity = getCategory(categoryId);
        ensureSameBoard(board, entity);
        if (articleRepository.existsByCategoryIdAndDeletedAtIsNull(categoryId)) {
            throw new ApiException(ErrorCode.BOARD_CATEGORY_HAS_ARTICLES);
        }
        articleCategoryRepository.delete(entity);
    }

    private ArticleCategoryEntity getCategory(Long categoryId) {
        return articleCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_CATEGORY_NOT_FOUND));
    }

    private BoardEntity getBoard(Long boardId) {
        return boardRepository.findByIdAndDeletedAtIsNull(boardId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_NOT_FOUND));
    }

    private UserEntity getCurrentUser() {
        Long userId = currentUserService.getUserId();
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void ensureSameBoard(BoardEntity board, ArticleCategoryEntity category) {
        if (!board.getId().equals(category.getBoard().getId())) {
            throw new ApiException(ErrorCode.BOARD_CATEGORY_INVALID);
        }
    }

    private String normalizeName(String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            throw new ApiException(ErrorCode.BOARD_CATEGORY_NAME_REQUIRED);
        }
        return categoryName.trim();
    }
}
